package com.friends.features.balloon;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollisionConfig;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;

import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BalloonSystem - Hot Air Balloon vehicle using real BlockEntity
 *
 * Creates a 3x3 physical balloon platform that players can stand on.
 * Uses the same technique as the flying cube experiment.
 */
public class BalloonSystem {

    // All balloons in the world (balloonId -> Balloon)
    private final Map<Integer, Balloon> balloons = new ConcurrentHashMap<>();

    // Which player is riding which balloon (playerUUID -> balloonId)
    private final Map<UUID, Integer> playerBalloons = new ConcurrentHashMap<>();

    // Online players reference (shared with radar)
    private final Map<UUID, PlayerRef> onlinePlayers;

    // Balloon ID counter
    private final AtomicInteger nextBalloonId = new AtomicInteger(1);

    // Block type for the balloon basket and toggle
    private static final String BALLOON_BLOCK_TYPE = "Soil_Pebbles_Frozen";
    // Note: Custom pack blocks don't work with BlockEntity.assembleDefaultBlockEntity()
    // Using Deco_Lever which has IsUsable=true for F key prompt
    private static final String TOGGLE_BLOCK_TYPE = "Deco_Lever";

    // Map ALL block entity refs to balloon IDs for interaction handling
    private final Map<Ref<EntityStore>, Integer> blockToBalloon = new ConcurrentHashMap<>();

    // Map brazier world block positions to balloon IDs (for UseBlockEvent)
    private final Map<String, Integer> brazierPositionToBalloon = new ConcurrentHashMap<>();

    // Flight parameters
    private static final double FLIGHT_HEIGHT = 10.0;  // Height above ground when flying
    private static final double ASCENT_SPEED = 1.0;    // Velocity for rising (blocks/sec)
    private static final double HOVER_SPEED = 0.0;     // No movement when hovering
    private static final double DESCENT_SPEED = 1.0;   // Velocity for landing
    private static final double HORIZONTAL_SPEED = 1.5; // Speed for horizontal movement
    private static final double BOARDING_RADIUS = 2.0; // How close to board (center of 3x3)

    // Update interval
    private static final long UPDATE_INTERVAL_MS = 100; // 10 updates per second

    // Scheduler for updates
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> updateTask;

    public BalloonSystem(Map<UUID, PlayerRef> onlinePlayers) {
        this.onlinePlayers = onlinePlayers;
    }

    /**
     * Start the balloon update loop
     */
    public void start() {
        System.out.println("[Balloon] Starting balloon vehicle system...");
        updateTask = scheduler.scheduleAtFixedRate(
            this::update,
            UPDATE_INTERVAL_MS,
            UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    // Height to spawn balloon above player (will fall down)
    private static final double SPAWN_HEIGHT_OFFSET = 30.0;

    /**
     * Spawn a new 3x3 balloon platform at the given position
     * Spawns HIGH in the sky and lets it fall down to land naturally
     * Must be called with access to the entity store (from a command context)
     */
    public int spawnBalloon(Vector3d position, UUID worldUuid, Store<EntityStore> entityStore, float playerYaw) {
        try {
            // Calculate spawn position 5 units in front of player, HIGH in the sky
            double forwardX = -Math.sin(playerYaw);
            double forwardZ = Math.cos(playerYaw);
            double spawnX = position.x + forwardX * 5.0;
            double spawnZ = position.z + forwardZ * 5.0;
            double spawnY = position.y + SPAWN_HEIGHT_OFFSET;  // Spawn HIGH, will fall down

            System.out.println("[Balloon] Spawning balloon at height " + spawnY + " (player at " + position.y + ")");

            // Get TimeResource for entity creation
            TimeResource timeResource = entityStore.getResource(TimeResource.getResourceType());

            List<Ref<EntityStore>> blockRefs = new ArrayList<>();
            Ref<EntityStore> centerRef = null;

            // Get the world for placing world blocks
            World world = Universe.get().getWorld(worldUuid);

            // Create 3x3 platform of BlockEntities
            // The center brazier will be placed AFTER landing (world blocks can't move)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Vector3d blockPos = new Vector3d(spawnX + dx, spawnY, spawnZ + dz);
                    boolean isCenter = (dx == 0 && dz == 0);

                    // Create BlockEntity for ALL 9 positions (full 3x3 platform)
                    // Brazier will be placed as WORLD BLOCK on top after landing
                    String blockType = BALLOON_BLOCK_TYPE;

                    Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
                        timeResource,
                        blockType,
                        blockPos
                    );

                    HitboxCollisionConfig config = HitboxCollisionConfig.getAssetMap().getAsset("HardCollision");
                    if (config != null) {
                        holder.addComponent(HitboxCollision.getComponentType(), new HitboxCollision(config));
                    }

                    Ref<EntityStore> blockRef = entityStore.addEntity(holder, AddReason.SPAWN);
                    blockRefs.add(blockRef);

                    // Track center block for position tracking
                    if (isCenter) {
                        centerRef = blockRef;
                    }

                    // Enable gravity and apply downward velocity to make blocks fall
                    BlockEntity block = entityStore.getComponent(blockRef, BlockEntity.getComponentType());
                    if (block != null && block.getSimplePhysicsProvider() != null) {
                        var physics = block.getSimplePhysicsProvider();
                        var boundingBox = block.createBoundingBoxComponent();
                        if (boundingBox != null) {
                            // Set gravity to 1.0 (normal gravity)
                            physics.setGravity(1.0, boundingBox);
                        }
                        // Apply initial downward velocity to start falling
                        physics.setVelocity(new Vector3d(0, -0.5, 0));
                    }
                }
            }

            int id = nextBalloonId.getAndIncrement();
            Vector3d centerPos = new Vector3d(spawnX, spawnY, spawnZ);
            Balloon balloon = new Balloon(id, blockRefs, centerRef, entityStore, worldUuid, centerPos);

            // Store target landing position (X/Z only - Y will be determined when landed)
            int targetX = (int) Math.floor(spawnX);
            int targetZ = (int) Math.floor(spawnZ);
            balloon.setTargetLandingPos(new Vector3i(targetX, 0, targetZ));  // Y=0 placeholder
            balloon.setLanded(false);  // Not landed yet

            balloons.put(id, balloon);

            // Map blocks to balloon for interaction handling
            for (Ref<EntityStore> blockRef : blockRefs) {
                blockToBalloon.put(blockRef, id);
            }

            System.out.println("[Balloon] Spawned 3x3 balloon #" + id + " at height " + spawnY + " - will land at X=" + targetX + ", Z=" + targetZ);
            return id;

        } catch (Exception e) {
            System.err.println("[Balloon] Error spawning balloon: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Remove a balloon from the world
     */
    public void removeBalloon(int balloonId) {
        Balloon balloon = balloons.remove(balloonId);
        if (balloon != null) {
            // Remove all block mappings
            for (Ref<EntityStore> blockRef : balloon.getBlockRefs()) {
                blockToBalloon.remove(blockRef);
            }

            // Remove brazier world block and unregister position mapping
            Vector3i brazierPos = balloon.getBrazierBlockPos();
            if (brazierPos != null) {
                unregisterBrazierPosition(brazierPos.x, brazierPos.y, brazierPos.z);

                // Remove the world block
                try {
                    World world = Universe.get().getWorld(balloon.getWorldUuid());
                    if (world != null) {
                        world.setBlock(brazierPos.x, brazierPos.y, brazierPos.z, "empty");
                        System.out.println("[Balloon] Removed toggle world block at " + brazierPos);
                    }
                } catch (Exception e) {
                    System.err.println("[Balloon] Error removing toggle world block: " + e.getMessage());
                }
            }

            // Eject any rider
            UUID rider = balloon.getRider();
            if (rider != null) {
                playerBalloons.remove(rider);
                PlayerRef player = onlinePlayers.get(rider);
                if (player != null) {
                    player.sendMessage(Message.raw("[Balloon] Balloon removed!").color(java.awt.Color.RED));
                }
            }

            // Remove all block entities from the world (including the brazier BlockEntity)
            try {
                for (Ref<EntityStore> blockRef : balloon.getBlockRefs()) {
                    balloon.getEntityStore().removeEntity(blockRef, RemoveReason.REMOVE);
                }
            } catch (Exception e) {
                System.err.println("[Balloon] Error removing balloon entity: " + e.getMessage());
            }

            System.out.println("[Balloon] Removed balloon #" + balloonId);
        }
    }

    /**
     * Toggle balloon flight on
     */
    public boolean turnOn(int balloonId, UUID playerUuid) {
        Balloon balloon = balloons.get(balloonId);
        if (balloon == null) return false;

        balloon.setFlying(true);
        balloon.setRider(playerUuid);
        playerBalloons.put(playerUuid, balloonId);

        PlayerRef player = onlinePlayers.get(playerUuid);
        if (player != null) {
            player.sendMessage(Message.raw("[Balloon] Balloon activated! Rising...").color(java.awt.Color.CYAN));
            // Spawn fire particles to show the brazier is lit
            spawnFireParticles(player, balloon.getLastKnownPosition());
        }

        System.out.println("[Balloon] Balloon #" + balloonId + " activated");
        return true;
    }

    /**
     * Spawn fire particles at a position to indicate the brazier is lit
     */
    private void spawnFireParticles(PlayerRef player, Vector3d position) {
        try {
            // Try different fire particle system IDs
            String[] fireParticles = {"Flame", "Fire", "Torch_Fire", "Campfire", "Ember"};

            for (String particleId : fireParticles) {
                try {
                    Position pos = new Position((float)position.x, (float)position.y + 0.5f, (float)position.z);
                    Direction rotation = new Direction(0, 0, 0);
                    Color color = new Color((byte)255, (byte)128, (byte)0); // Orange fire color

                    SpawnParticleSystem packet = new SpawnParticleSystem(
                        particleId,
                        pos,
                        rotation,
                        1.5f,  // scale
                        color
                    );

                    player.getPacketHandler().write(packet);
                    System.out.println("[Balloon] Spawned fire particles: " + particleId);
                    break; // If successful, stop trying other particles
                } catch (Exception e) {
                    // Try next particle type
                }
            }
        } catch (Exception e) {
            System.err.println("[Balloon] Error spawning fire particles: " + e.getMessage());
        }
    }

    /**
     * Toggle balloon flight on/off
     */
    public boolean toggle(int balloonId, UUID playerUuid) {
        Balloon balloon = balloons.get(balloonId);
        if (balloon == null) return false;

        if (balloon.isFlying()) {
            return turnOff(balloonId, playerUuid);
        } else {
            return turnOn(balloonId, playerUuid);
        }
    }

    /**
     * Toggle balloon flight off
     */
    public boolean turnOff(int balloonId, UUID playerUuid) {
        Balloon balloon = balloons.get(balloonId);
        if (balloon == null) return false;

        balloon.setFlying(false);

        PlayerRef player = onlinePlayers.get(playerUuid);
        if (player != null) {
            player.sendMessage(Message.raw("[Balloon] Balloon deactivated.").color(java.awt.Color.YELLOW));
        }

        System.out.println("[Balloon] Balloon #" + balloonId + " deactivated");
        return true;
    }

    /**
     * Get the nearest balloon to a player
     */
    public Integer getNearestBalloon(UUID playerUuid) {
        PlayerRef player = onlinePlayers.get(playerUuid);
        if (player == null) return null;

        Vector3d playerPos = player.getTransform().getPosition();
        double closestDist = Double.MAX_VALUE;
        Integer closestId = null;

        for (Balloon balloon : balloons.values()) {
            if (!balloon.getWorldUuid().equals(player.getWorldUuid())) continue;

            Vector3d balloonPos = balloon.getLastKnownPosition();
            double dist = Math.sqrt(
                Math.pow(playerPos.x - balloonPos.x, 2) +
                Math.pow(playerPos.y - balloonPos.y, 2) +
                Math.pow(playerPos.z - balloonPos.z, 2)
            );

            if (dist < closestDist) {
                closestDist = dist;
                closestId = balloon.getId();
            }
        }

        return closestId;
    }

    /**
     * Get the nearest balloon to a player within a given range
     */
    public Integer getNearestBalloonInRange(UUID playerUuid, double maxRange) {
        PlayerRef player = onlinePlayers.get(playerUuid);
        if (player == null) return null;

        Vector3d playerPos = player.getTransform().getPosition();
        double closestDist = Double.MAX_VALUE;
        Integer closestId = null;

        for (Balloon balloon : balloons.values()) {
            if (!balloon.getWorldUuid().equals(player.getWorldUuid())) continue;

            Vector3d balloonPos = balloon.getLastKnownPosition();
            double dist = Math.sqrt(
                Math.pow(playerPos.x - balloonPos.x, 2) +
                Math.pow(playerPos.y - balloonPos.y, 2) +
                Math.pow(playerPos.z - balloonPos.z, 2)
            );

            if (dist < closestDist && dist <= maxRange) {
                closestDist = dist;
                closestId = balloon.getId();
            }
        }

        return closestId;
    }

    /**
     * Main update loop
     */
    private void update() {
        // Update all balloons
        for (Balloon balloon : balloons.values()) {
            try {
                updateBalloon(balloon);
            } catch (Exception e) {
                System.err.println("[Balloon] Error updating balloon #" + balloon.getId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Get current position of a balloon entity (center block)
     */
    private Vector3d getBalloonPosition(Balloon balloon) {
        try {
            Ref<EntityStore> centerRef = balloon.getCenterRef();
            if (centerRef == null) return balloon.getLastKnownPosition();

            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform =
                balloon.getEntityStore().getComponent(
                    centerRef,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType()
                );
            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Entity might have been removed
        }
        return balloon.getLastKnownPosition();
    }

    /**
     * Update a single balloon
     */
    private void updateBalloon(Balloon balloon) {
        // Get the world and execute on its thread
        World world = Universe.get().getWorld(balloon.getWorldUuid());
        if (world == null) return;

        // Queue the physics update on the world thread
        world.execute(() -> {
            try {
                applyBalloonForce(balloon);
            } catch (Exception e) {
                System.err.println("[Balloon] Error in world thread: " + e.getMessage());
            }
        });
    }

    /**
     * Check if balloon has landed and handle the landing (disable gravity, register brazier)
     */
    private void checkAndHandleLanding(Balloon balloon, Vector3d currentPos) {
        // Check if any block is on ground using physics provider
        boolean anyOnGround = false;
        for (Ref<EntityStore> blockRef : balloon.getBlockRefs()) {
            try {
                BlockEntity block = balloon.getEntityStore().getComponent(blockRef, BlockEntity.getComponentType());
                if (block != null && block.getSimplePhysicsProvider() != null) {
                    if (block.getSimplePhysicsProvider().isOnGround()) {
                        anyOnGround = true;
                        break;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        if (anyOnGround) {
            System.out.println("[Balloon] #" + balloon.getId() + " has landed at Y=" + currentPos.y);
            balloon.setLanded(true);

            // Disable gravity on all blocks so they stay in place
            for (Ref<EntityStore> blockRef : balloon.getBlockRefs()) {
                try {
                    BlockEntity block = balloon.getEntityStore().getComponent(blockRef, BlockEntity.getComponentType());
                    if (block != null && block.getSimplePhysicsProvider() != null) {
                        var boundingBox = block.createBoundingBoxComponent();
                        if (boundingBox != null) {
                            block.getSimplePhysicsProvider().setGravity(0.0, boundingBox);
                        }
                        // Stop any velocity
                        block.getSimplePhysicsProvider().setVelocity(new Vector3d(0, 0, 0));
                    }
                } catch (Exception e) {
                    System.err.println("[Balloon] Error disabling gravity: " + e.getMessage());
                }
            }

            // Place toggle block as a WORLD BLOCK on top of the center (Y+1)
            // World blocks work with UseBlockEvent for F key interactions
            int brazierX = (int) Math.floor(currentPos.x);
            int brazierY = (int) Math.floor(currentPos.y) + 1;  // ON TOP of platform
            int brazierZ = (int) Math.floor(currentPos.z);

            World world = Universe.get().getWorld(balloon.getWorldUuid());
            if (world != null) {
                System.out.println("[Balloon] Placing toggle WORLD BLOCK at (" + brazierX + "," + brazierY + "," + brazierZ + ") type=" + TOGGLE_BLOCK_TYPE);

                // Place the lever as a world block
                world.setBlock(brazierX, brazierY, brazierZ, TOGGLE_BLOCK_TYPE);

                // Verify placement
                BlockType placedType = world.getBlockType(brazierX, brazierY, brazierZ);
                if (placedType != null) {
                    System.out.println("[Balloon] Placed toggle world block verified: " + placedType.getId());
                } else {
                    System.err.println("[Balloon] WARNING: Toggle block placement verification failed!");
                }
            } else {
                System.err.println("[Balloon] ERROR: World is null, cannot place toggle block!");
            }

            balloon.setBrazierBlockPos(new Vector3i(brazierX, brazierY, brazierZ));
            registerBrazierPosition(balloon.getId(), brazierX, brazierY, brazierZ);

            System.out.println("[Balloon] #" + balloon.getId() + " registered brazier at (" + brazierX + "," + brazierY + "," + brazierZ + ")");

            // Notify any online players
            for (PlayerRef player : onlinePlayers.values()) {
                player.sendMessage(Message.raw("[Balloon] Balloon has landed! Press F on the brazier to activate.").color(java.awt.Color.GREEN));
            }
        }
    }

    /**
     * Apply force to balloon - must be called on world thread
     */
    private void applyBalloonForce(Balloon balloon) {
        // Get current balloon position
        Vector3d balloonPos = getBalloonPosition(balloon);
        if (balloonPos == null) return;

        // Update last known position
        balloon.setLastKnownPosition(balloonPos);

        // Check if balloon has landed yet (spawned from sky)
        if (!balloon.isLanded()) {
            checkAndHandleLanding(balloon, balloonPos);
            // Don't apply any forces while falling - let gravity do its job
            return;
        }

        // Spawn fire particles while flying (every ~500ms, so every 5 updates)
        if (balloon.isFlying() && balloon.getRider() != null) {
            balloon.incrementParticleTick();
            if (balloon.getParticleTick() >= 5) {
                balloon.resetParticleTick();
                PlayerRef rider = onlinePlayers.get(balloon.getRider());
                if (rider != null) {
                    spawnFireParticles(rider, balloonPos);
                }
            }
        }

        // Find ground level
        double groundY = findGroundLevel(balloon.getWorldUuid(), balloonPos.x, balloonPos.y, balloonPos.z);
        double targetY = groundY + FLIGHT_HEIGHT;
        double yDiff = targetY - balloonPos.y;

        // Calculate the velocity to apply
        double velocity = 0.0;

        if (balloon.isFlying()) {
            // Flying mode: rise to target height, then hover
            if (yDiff > 1.0) {
                // Below target - rise (slower as we approach)
                velocity = Math.min(ASCENT_SPEED, yDiff * 0.5);
            } else if (yDiff < -1.0) {
                // Above target - descend slowly
                velocity = Math.max(-DESCENT_SPEED, yDiff * 0.5);
            } else {
                // At target height - hover (no movement)
                velocity = 0.0;
            }
        } else {
            // Landing mode: descend slowly until near ground
            double heightAboveGround = balloonPos.y - groundY;
            if (heightAboveGround > 1.5) {
                // Still in air - descend gently
                velocity = -DESCENT_SPEED;
            } else {
                // Near ground - stop
                velocity = 0.0;
            }
        }

        System.out.println("[Balloon] #" + balloon.getId() + " y=" + balloonPos.y + " target=" + targetY + " vel=" + velocity);

        // Calculate horizontal movement based on rider's look direction
        double hVelX = 0.0;
        double hVelZ = 0.0;

        if (balloon.isFlying() && balloon.getRider() != null) {
            PlayerRef rider = onlinePlayers.get(balloon.getRider());
            if (rider != null) {
                float yaw = rider.getTransform().getRotation().getYaw();
                // Move in direction player is looking
                hVelX = -Math.sin(yaw) * HORIZONTAL_SPEED;
                hVelZ = Math.cos(yaw) * HORIZONTAL_SPEED;
            }
        }

        // Apply velocity to all blocks in the platform
        for (Ref<EntityStore> blockRef : balloon.getBlockRefs()) {
            try {
                BlockEntity block = balloon.getEntityStore().getComponent(blockRef, BlockEntity.getComponentType());
                if (block != null && block.getSimplePhysicsProvider() != null) {
                    block.getSimplePhysicsProvider().setResting(false);
                }

                Velocity vel = balloon.getEntityStore().getComponent(blockRef, Velocity.getComponentType());
                if (vel != null) {
                    vel.setY(velocity);
                    vel.setX(hVelX);
                    vel.setZ(hVelZ);
                }
            } catch (Exception e) {
                System.err.println("[Balloon] Error applying velocity: " + e.getMessage());
            }
        }
    }

    /**
     * Find ground level at a position
     */
    private double findGroundLevel(UUID worldUuid, double x, double startY, double z) {
        try {
            World world = Universe.get().getWorld(worldUuid);
            if (world == null) return 64.0;

            int blockX = (int) Math.floor(x);
            int blockZ = (int) Math.floor(z);
            int startBlockY = (int) Math.floor(startY);

            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            BlockAccessor accessor = world.getChunkIfLoaded(chunkKey);

            if (accessor == null) return startY - FLIGHT_HEIGHT;

            for (int y = startBlockY; y > 0; y--) {
                BlockType blockType = accessor.getBlockType(blockX & 15, y, blockZ & 15);
                if (blockType != null && blockType != BlockType.EMPTY) {
                    return y + 1.0;
                }
            }

            return 1.0;
        } catch (Exception e) {
            return startY - FLIGHT_HEIGHT;
        }
    }

    /**
     * Handle player disconnect
     */
    public void onPlayerDisconnect(UUID uuid) {
        Integer balloonId = playerBalloons.remove(uuid);
        if (balloonId != null) {
            Balloon balloon = balloons.get(balloonId);
            if (balloon != null) {
                balloon.setRider(null);
                balloon.setFlying(false);
            }
        }
    }

    /**
     * Get all balloon IDs
     */
    public java.util.Set<Integer> getBalloonIds() {
        return balloons.keySet();
    }

    /**
     * Shutdown the system
     */
    public void shutdown() {
        System.out.println("[Balloon] Shutting down balloon system...");
        if (updateTask != null) {
            updateTask.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        // Remove all balloon entities
        for (Integer id : balloons.keySet().toArray(new Integer[0])) {
            removeBalloon(id);
        }
    }

    /**
     * Handle player interaction via PlayerInteractEvent (F key)
     * Uses entity Ref directly from the event
     */
    public void onPlayerInteractRef(Ref<EntityStore> targetRef, Ref<EntityStore> playerEntityRef) {
        System.out.println("[Balloon] onPlayerInteractRef called with ref: " + targetRef);

        if (targetRef == null) {
            System.out.println("[Balloon] targetRef is null");
            return;
        }

        // Find player UUID from entity ref by checking online players
        UUID playerUuid = null;
        for (var entry : onlinePlayers.entrySet()) {
            // Just use the first online player for now (single player mode)
            playerUuid = entry.getKey();
            break;
        }

        if (playerUuid == null) {
            System.out.println("[Balloon] Could not find player UUID");
            return;
        }

        System.out.println("[Balloon] Looking for ref in map. Map size: " + blockToBalloon.size());
        System.out.println("[Balloon] Target ref: " + targetRef + " (hash: " + System.identityHashCode(targetRef) + ")");

        // Check all entries to find matching balloon
        Integer balloonId = blockToBalloon.get(targetRef);

        // If direct lookup fails, compare using equals
        if (balloonId == null) {
            System.out.println("[Balloon] Direct lookup failed, trying equals comparison");
            for (var entry : blockToBalloon.entrySet()) {
                System.out.println("[Balloon] Comparing: " + entry.getKey() + " vs " + targetRef);
                if (entry.getKey().equals(targetRef)) {
                    balloonId = entry.getValue();
                    System.out.println("[Balloon] Found match via equals!");
                    break;
                }
            }
        }

        if (balloonId == null) {
            System.out.println("[Balloon] No balloon found for this ref");
            return;
        }

        System.out.println("[Balloon] Found balloon #" + balloonId + " for interaction");

        Balloon balloon = balloons.get(balloonId);
        if (balloon == null) return;

        PlayerRef player = onlinePlayers.get(playerUuid);

        if (balloon.isFlying()) {
            // Turn off
            balloon.setFlying(false);
            balloon.setRider(null);
            playerBalloons.remove(playerUuid);
            if (player != null) {
                player.sendMessage(Message.raw("[Balloon] Balloon deactivated. Landing...").color(java.awt.Color.YELLOW));
            }
            System.out.println("[Balloon] Balloon #" + balloonId + " deactivated by F key");
        } else {
            // Turn on
            balloon.setFlying(true);
            balloon.setRider(playerUuid);
            playerBalloons.put(playerUuid, balloonId);
            if (player != null) {
                player.sendMessage(Message.raw("[Balloon] Balloon activated! Rising...").color(java.awt.Color.CYAN));
            }
            System.out.println("[Balloon] Balloon #" + balloonId + " activated by F key");
        }
    }

    /**
     * Handle player interaction via PlayerInteractEvent (F key)
     * Player ref is an entity ref, so we find the player UUID by checking online players
     */
    public void onPlayerInteractEntity(com.hypixel.hytale.server.core.entity.Entity targetEntity, Ref<EntityStore> playerEntityRef) {
        System.out.println("[Balloon] onPlayerInteractEntity called");

        // Find player UUID from entity ref by checking online players
        UUID playerUuid = null;
        for (var entry : onlinePlayers.entrySet()) {
            // Just use the first online player for now (single player mode)
            playerUuid = entry.getKey();
            break;
        }

        if (playerUuid != null) {
            onPlayerInteract(targetEntity, playerUuid);
        } else {
            System.out.println("[Balloon] Could not find player UUID");
        }
    }

    /**
     * Handle player interaction with center block - toggle balloon on/off
     */
    public void onPlayerInteract(com.hypixel.hytale.server.core.entity.Entity targetEntity, UUID playerUuid) {
        System.out.println("[Balloon] onPlayerInteract called with entity: " + targetEntity);

        if (targetEntity == null) {
            System.out.println("[Balloon] targetEntity is null");
            return;
        }
        Ref<EntityStore> targetRef = targetEntity.getReference();
        if (targetRef == null) {
            System.out.println("[Balloon] targetRef is null");
            return;
        }

        System.out.println("[Balloon] Looking for ref in map. Map size: " + blockToBalloon.size());
        System.out.println("[Balloon] Target ref: " + targetRef + " (hash: " + System.identityHashCode(targetRef) + ")");
        for (var entry : blockToBalloon.entrySet()) {
            System.out.println("[Balloon] Map entry: " + entry.getKey() + " (hash: " + System.identityHashCode(entry.getKey()) + ") -> " + entry.getValue());
            System.out.println("[Balloon] Equals check: " + entry.getKey().equals(targetRef));
        }

        Integer balloonId = blockToBalloon.get(targetRef);
        if (balloonId == null) {
            System.out.println("[Balloon] No balloon found for this ref");
            return;
        }

        System.out.println("[Balloon] Found balloon #" + balloonId + " for interaction");

        Balloon balloon = balloons.get(balloonId);
        if (balloon == null) return;

        PlayerRef player = onlinePlayers.get(playerUuid);

        if (balloon.isFlying()) {
            // Turn off
            balloon.setFlying(false);
            balloon.setRider(null);
            playerBalloons.remove(playerUuid);
            if (player != null) {
                player.sendMessage(Message.raw("[Balloon] Balloon deactivated. Landing...").color(java.awt.Color.YELLOW));
            }
            System.out.println("[Balloon] Balloon #" + balloonId + " deactivated by right-click");
        } else {
            // Turn on
            balloon.setFlying(true);
            balloon.setRider(playerUuid);
            playerBalloons.put(playerUuid, balloonId);
            if (player != null) {
                player.sendMessage(Message.raw("[Balloon] Balloon activated! Rising...").color(java.awt.Color.CYAN));
            }
            System.out.println("[Balloon] Balloon #" + balloonId + " activated by right-click");
        }
    }

    /**
     * Handle UseBlockEvent for brazier world blocks (F key interaction)
     * @param blockPos The position of the block that was used
     * @param blockTypeKey The block type key
     * @return true if the interaction was handled
     */
    public boolean onUseBlockEvent(Vector3i blockPos, String blockTypeKey) {
        System.out.println("[Balloon] onUseBlockEvent called at " + blockPos + " type=" + blockTypeKey);

        // Check if this is a brazier
        if (!TOGGLE_BLOCK_TYPE.equals(blockTypeKey)) {
            return false;
        }

        return toggleBalloonAtPosition(blockPos);
    }

    /**
     * Toggle a balloon at a specific block position
     * @param blockPos The position of the brazier block
     * @return true if the interaction was handled
     */
    public boolean toggleBalloonAtPosition(Vector3i blockPos) {
        // Look up balloon by position
        String posKey = blockPos.x + "," + blockPos.y + "," + blockPos.z;
        Integer balloonId = brazierPositionToBalloon.get(posKey);

        if (balloonId == null) {
            System.out.println("[Balloon] No balloon found for brazier at " + posKey);
            return false;
        }

        System.out.println("[Balloon] Found balloon #" + balloonId + " for brazier interaction!");

        // Find player (single player mode - use first online)
        UUID playerUuid = null;
        for (var entry : onlinePlayers.entrySet()) {
            playerUuid = entry.getKey();
            break;
        }

        if (playerUuid == null) {
            System.out.println("[Balloon] No player found");
            return false;
        }

        // Toggle the balloon
        toggle(balloonId, playerUuid);
        return true;
    }

    /**
     * Create a position key string for the brazier map
     */
    private String positionKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * Register a brazier position for a balloon
     */
    public void registerBrazierPosition(int balloonId, int x, int y, int z) {
        String key = positionKey(x, y, z);
        brazierPositionToBalloon.put(key, balloonId);
        System.out.println("[Balloon] Registered brazier position " + key + " for balloon #" + balloonId);
    }

    /**
     * Unregister a brazier position
     */
    public void unregisterBrazierPosition(int x, int y, int z) {
        String key = positionKey(x, y, z);
        brazierPositionToBalloon.remove(key);
    }

    /**
     * Inner class representing a balloon in the world
     */
    public static class Balloon {
        private final int id;
        private final List<Ref<EntityStore>> blockRefs;  // All platform blocks
        private final Ref<EntityStore> centerRef;        // Center block (interactable, for position tracking)
        private final Store<EntityStore> entityStore;
        private final UUID worldUuid;
        private Vector3d lastKnownPosition;
        private UUID rider;
        private boolean flying;
        private Vector3i brazierBlockPos;  // World block position for brazier (set after landing)
        private Vector3i targetLandingPos; // Target X/Z landing position
        private boolean landed;            // Has the balloon landed?
        private int particleTick;          // Counter for particle spawning

        public Balloon(int id, List<Ref<EntityStore>> blockRefs, Ref<EntityStore> centerRef,
                       Store<EntityStore> entityStore, UUID worldUuid, Vector3d initialPosition) {
            this.id = id;
            this.blockRefs = blockRefs;
            this.centerRef = centerRef;
            this.entityStore = entityStore;
            this.worldUuid = worldUuid;
            this.lastKnownPosition = initialPosition;
            this.rider = null;
            this.flying = false;
            this.brazierBlockPos = null;
            this.targetLandingPos = null;
            this.landed = false;
            this.particleTick = 0;
        }

        public int getId() { return id; }
        public List<Ref<EntityStore>> getBlockRefs() { return blockRefs; }
        public Ref<EntityStore> getCenterRef() { return centerRef; }
        public Store<EntityStore> getEntityStore() { return entityStore; }
        public UUID getWorldUuid() { return worldUuid; }
        public Vector3d getLastKnownPosition() { return lastKnownPosition; }
        public void setLastKnownPosition(Vector3d pos) { this.lastKnownPosition = pos; }
        public UUID getRider() { return rider; }
        public void setRider(UUID rider) { this.rider = rider; }
        public boolean isFlying() { return flying; }
        public void setFlying(boolean flying) { this.flying = flying; }
        public Vector3i getBrazierBlockPos() { return brazierBlockPos; }
        public void setBrazierBlockPos(Vector3i pos) { this.brazierBlockPos = pos; }
        public Vector3i getTargetLandingPos() { return targetLandingPos; }
        public void setTargetLandingPos(Vector3i pos) { this.targetLandingPos = pos; }
        public boolean isLanded() { return landed; }
        public void setLanded(boolean landed) { this.landed = landed; }
        public int getParticleTick() { return particleTick; }
        public void incrementParticleTick() { this.particleTick++; }
        public void resetParticleTick() { this.particleTick = 0; }
    }
}
