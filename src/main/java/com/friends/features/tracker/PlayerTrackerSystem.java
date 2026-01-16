package com.friends.features.tracker;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.math.vector.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * PlayerTrackerSystem - Shows a particle trail to other players when using a special item.
 *
 * When the player uses an item (right-click), it spawns particles pointing toward
 * the nearest other player, creating a visual path to follow.
 */
public class PlayerTrackerSystem {

    // Track online players (shared with radar)
    private final Map<UUID, PlayerRef> onlinePlayers;

    // Cooldown tracking (player UUID -> last use timestamp)
    private final Map<UUID, Long> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    // The item that triggers tracking
    // TODO: Create custom item once we figure out asset registration
    private static final String TRACKER_ITEM = "Tool_Map";

    // Cooldown in milliseconds
    private static final long COOLDOWN_MS = 2000; // 2 seconds

    // Particle settings - using valid game particle system IDs
    private static final String PARTICLE_ID = "Torch_Fire"; // Small flame - try this first
    private static final String FAIL_PARTICLE_ID = "Magic_Hit"; // Magic burst for failure
    private static final float PARTICLE_SCALE = 0.3f; // Small scale for path markers
    private static final float FAIL_PARTICLE_SCALE = 0.5f;
    private static final int PROJECTILE_SPEED = 5; // blocks per second
    private static final long SPAWN_INTERVAL_MS = 200; // spawn every 200ms
    private static final int MAX_PARTICLES = 50; // more particles for better path visibility

    // Scheduler for delayed particle removal (if needed)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public PlayerTrackerSystem(Map<UUID, PlayerRef> onlinePlayers) {
        this.onlinePlayers = onlinePlayers;
    }

    /**
     * Handle player interaction event - check if they're using the tracker item
     */
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle "Use" (right-click) interactions
        if (event.getActionType() != InteractionType.Use &&
            event.getActionType() != InteractionType.Secondary) {
            return;
        }

        ItemStack itemInHand = event.getItemInHand();
        if (itemInHand == null) {
            return;
        }

        // Check if holding the tracker item
        String itemId = itemInHand.getItemId();
        if (itemId == null || !itemId.contains(TRACKER_ITEM)) {
            return;
        }

        // Get the player who used the item from the event
        PlayerRef user = null;
        UUID userUuid = null;

        // Find the PlayerRef by matching the player entity's UUID
        com.hypixel.hytale.server.core.entity.entities.Player playerEntity = event.getPlayer();
        if (playerEntity == null) {
            return;
        }

        // Find matching PlayerRef in our online players map
        for (Map.Entry<UUID, PlayerRef> entry : onlinePlayers.entrySet()) {
            if (entry.getValue().getReference().equals(event.getPlayerRef())) {
                user = entry.getValue();
                userUuid = entry.getKey();
                break;
            }
        }

        if (user == null) {
            return;
        }

        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(userUuid);
        if (lastUse != null && (now - lastUse) < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - lastUse)) / 1000 + 1;
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Cooldown: " + remaining + "s").color(java.awt.Color.GRAY));
            return;
        }

        // Set cooldown
        cooldowns.put(userUuid, now);

        // Find nearest other player
        PlayerRef nearestPlayer = findNearestPlayer(user, userUuid);
        if (nearestPlayer == null) {
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] No other players nearby!").color(java.awt.Color.YELLOW));
            return;
        }

        // Spawn particle trail to that player
        user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Finding path to " + nearestPlayer.getUsername() + "...").color(java.awt.Color.CYAN));
        spawnParticleTrail(user, nearestPlayer);
    }

    /**
     * Find the nearest other player to the given user
     */
    private PlayerRef findNearestPlayer(PlayerRef user, UUID userUuid) {
        Vector3d userPos = user.getTransform().getPosition();
        PlayerRef nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Map.Entry<UUID, PlayerRef> entry : onlinePlayers.entrySet()) {
            if (entry.getKey().equals(userUuid)) {
                continue; // Skip self
            }

            PlayerRef other = entry.getValue();
            Vector3d otherPos = other.getTransform().getPosition();

            double dist = distance(userPos, otherPos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = other;
            }
        }

        return nearest;
    }

    /**
     * Show particles along the entire A* path to the target.
     * Spawns all particles at once to show the complete route.
     */
    private void spawnTrackingOrb(PlayerRef user, PlayerRef target, CommandBuffer<EntityStore> commandBuffer) {
        Vector3d start = user.getTransform().getPosition();
        Vector3d end = target.getTransform().getPosition();

        double dist = distance(start, end);
        user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Target is " +
            String.format("%.0f", dist) + " blocks away").color(java.awt.Color.CYAN));

        if (dist < 1) {
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Player is right next to you!").color(java.awt.Color.GREEN));
            return;
        }

        // Get the world for pathfinding
        World world = null;
        try {
            world = Universe.get().getWorld(user.getWorldUuid());
        } catch (Exception e) {
            System.err.println("[Tracker] Could not get world: " + e.getMessage());
        }

        // Calculate A* path
        List<Vector3d> path = null;
        if (world != null) {
            try {
                // Find ground level under target
                double groundY = findGroundLevel(world, end.x, end.y + 10, end.z);
                Vector3d groundEnd = new Vector3d(end.x, groundY, end.z);

                AStarPathfinder pathfinder = new AStarPathfinder(world);
                path = pathfinder.findPath(start, groundEnd);
            } catch (Exception e) {
                System.err.println("[Tracker] Pathfinding error: " + e.getMessage());
            }
        }

        // If no path found, use straight line
        if (path == null || path.isEmpty()) {
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] No path found - showing direction").color(java.awt.Color.YELLOW));
            path = new java.util.ArrayList<>();
            // Create straight line path with points every 2 blocks
            int numPoints = (int)(dist / 2) + 1;
            for (int i = 0; i <= numPoints; i++) {
                double t = (double)i / numPoints;
                path.add(new Vector3d(
                    start.x + (end.x - start.x) * t,
                    start.y + (end.y - start.y) * t,
                    start.z + (end.z - start.z) * t
                ));
            }
        } else {
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Path found! (" + path.size() + " waypoints)").color(java.awt.Color.GREEN));
        }

        // Spawn particles along the entire path at once
        spawnPathParticles(user, path);
    }

    /**
     * Spawn a trail of particles from user to target player using A* pathfinding.
     * Returns true if path was found, false otherwise.
     */
    private boolean spawnParticleTrail(PlayerRef user, PlayerRef target) {
        Vector3d start = user.getTransform().getPosition();
        Vector3d end = target.getTransform().getPosition();

        double totalDist = distance(start, end);
        if (totalDist < 1) {
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Player is right next to you!").color(java.awt.Color.GREEN));
            return true;
        }

        // Get the world for pathfinding
        World world = null;
        try {
            world = Universe.get().getWorld(user.getWorldUuid());
        } catch (Exception e) {
            System.err.println("[Tracker] Could not get world: " + e.getMessage());
        }

        if (world == null) {
            spawnFailureEffect(user, start);
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Could not access world!").color(java.awt.Color.RED));
            return false;
        }

        // Find ground level under the target (in case they're floating)
        double groundY = findGroundLevel(world, end.x, end.y + 10, end.z);
        Vector3d groundEnd = new Vector3d(end.x, groundY, end.z);

        // Try A* pathfinding to ground position
        List<Vector3d> path = null;
        try {
            AStarPathfinder pathfinder = new AStarPathfinder(world);
            path = pathfinder.findPath(start, groundEnd);
        } catch (Exception e) {
            System.err.println("[Tracker] Pathfinding error: " + e.getMessage());
            e.printStackTrace();
        }

        // Check if path was found - if not, fall back to simple direction indicator
        if (path == null || path.isEmpty()) {
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] No ground path - showing direction...").color(java.awt.Color.YELLOW));
            spawnDirectionArrow(user, start, end);
            return true;
        }

        // Spawn animated projectile along the path
        spawnAnimatedProjectile(user, path);
        user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Path found! (" + path.size() + " waypoints)").color(java.awt.Color.GREEN));
        return true;
    }

    /**
     * Find ground level by iterating downward from startY until we find a solid block.
     */
    private double findGroundLevel(World world, double x, double startY, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int startBlockY = (int) Math.floor(startY);

        try {
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            var accessor = world.getChunkIfLoaded(chunkKey);

            if (accessor == null) {
                return startY;
            }

            for (int y = startBlockY; y > 0; y--) {
                var blockType = accessor.getBlockType(blockX, y, blockZ);
                if (blockType != null && blockType != com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY) {
                    return y + 1.0;
                }
            }
            return startY;
        } catch (Exception e) {
            return startY;
        }
    }

    /**
     * Spawn particles along the entire path at once, showing the complete route.
     * Uses different particle types to try and find one that works.
     */
    private void spawnPathParticles(PlayerRef user, List<Vector3d> path) {
        if (path.isEmpty()) return;

        // Sample the path to get evenly spaced points (every 1.5 blocks for better visibility)
        double spacing = 1.5;
        java.util.List<Vector3d> sampledPath = new java.util.ArrayList<>();
        double accumulatedDist = 0;
        Vector3d lastAdded = path.get(0);
        sampledPath.add(lastAdded);

        for (int i = 1; i < path.size(); i++) {
            Vector3d current = path.get(i);
            double segDist = distance(lastAdded, current);

            // Add intermediate points if segment is long
            if (segDist > spacing) {
                int numPoints = (int)(segDist / spacing);
                for (int j = 1; j <= numPoints; j++) {
                    double t = (double)j / (numPoints + 1);
                    sampledPath.add(new Vector3d(
                        lastAdded.x + (current.x - lastAdded.x) * t,
                        lastAdded.y + (current.y - lastAdded.y) * t,
                        lastAdded.z + (current.z - lastAdded.z) * t
                    ));
                }
            }
            sampledPath.add(current);
            lastAdded = current;
        }

        // Limit total particles
        int maxParticles = Math.min(sampledPath.size(), MAX_PARTICLES);

        // Use Torch_Fire for small, persistent flame-like markers
        String particleType = "Torch_Fire";

        System.out.println("[Tracker] Spawning " + maxParticles + " particles of type: " + particleType);

        // Spawn all particles at once
        for (int i = 0; i < maxParticles; i++) {
            Vector3d pos = sampledPath.get(i);

            // Vary color along path (blue to green gradient)
            int r = 50;
            int g = 100 + (int)(155.0 * i / maxParticles);
            int b = 255 - (int)(155.0 * i / maxParticles);

            spawnParticleOfType(user, particleType, pos.x, pos.y + 1.0, pos.z, r, g, b);
        }

        user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Spawned " + maxParticles + " markers").color(java.awt.Color.GREEN));
    }

    /**
     * Spawn a particle of a specific type at a position
     */
    private void spawnParticleOfType(PlayerRef viewer, String particleType, double x, double y, double z, int r, int g, int b) {
        try {
            Position position = new Position(x, y, z);
            Direction rotation = new Direction();
            Color color = new Color((byte)r, (byte)g, (byte)b);

            SpawnParticleSystem packet = new SpawnParticleSystem(
                particleType,
                position,
                rotation,
                PARTICLE_SCALE,
                color
            );

            viewer.getPacketHandler().write(packet);
        } catch (Exception e) {
            System.err.println("[Tracker] Failed to spawn particle " + particleType + ": " + e.getMessage());
        }
    }

    /**
     * Spawn a single orb that "moves" along the A* path.
     * Spawns one particle at a time, sequentially along waypoints.
     * The particle should fade before the next spawns, creating movement illusion.
     */
    private void spawnAnimatedProjectile(PlayerRef user, List<Vector3d> path) {
        if (path.isEmpty()) return;

        // Sample the path - take every Nth waypoint to control speed
        // At PROJECTILE_SPEED blocks/sec and SPAWN_INTERVAL_MS delay,
        // we need waypoints spaced PROJECTILE_SPEED * (SPAWN_INTERVAL_MS/1000) blocks apart
        double waypointSpacing = PROJECTILE_SPEED * (SPAWN_INTERVAL_MS / 1000.0);

        // Build a list of waypoints to visit
        java.util.List<Vector3d> sampledPath = new java.util.ArrayList<>();
        double accumulatedDist = 0;
        Vector3d lastAdded = path.get(0);
        sampledPath.add(lastAdded);

        for (int i = 1; i < path.size(); i++) {
            Vector3d current = path.get(i);
            double dist = distance(lastAdded, current);
            accumulatedDist += dist;

            if (accumulatedDist >= waypointSpacing) {
                sampledPath.add(current);
                lastAdded = current;
                accumulatedDist = 0;
            }
        }

        // Always include the final destination
        if (!sampledPath.get(sampledPath.size() - 1).equals(path.get(path.size() - 1))) {
            sampledPath.add(path.get(path.size() - 1));
        }

        // Limit total waypoints
        int maxWaypoints = Math.min(sampledPath.size(), MAX_PARTICLES);

        // Spawn ONE particle at a time, sequentially
        for (int i = 0; i < maxWaypoints; i++) {
            final Vector3d waypoint = sampledPath.get(i);

            scheduler.schedule(() -> {
                spawnParticle(user, waypoint.x, waypoint.y + 1.0, waypoint.z, 100, 200, 255);
            }, i * SPAWN_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Spawn a SINGLE moving orb that flies from player toward target.
     * Uses Direction to set the orb's movement direction.
     */
    private void spawnDirectionArrow(PlayerRef user, Vector3d start, Vector3d end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Normalize direction
        double dirX = dx / dist;
        double dirY = dy / dist;
        double dirZ = dz / dist;

        // Report distance
        user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Target is " +
            String.format("%.0f", dist) + " blocks away").color(java.awt.Color.CYAN));

        // Spawn position: 2 blocks in front of player at eye level
        double spawnX = start.x + dirX * 2.0;
        double spawnY = start.y + 1.5;
        double spawnZ = start.z + dirZ * 2.0;

        try {
            Position position = new Position(spawnX, spawnY, spawnZ);

            // Calculate pitch and yaw from direction vector
            float yaw = (float) Math.toDegrees(Math.atan2(dirX, dirZ));
            float pitch = (float) Math.toDegrees(-Math.asin(dirY));
            Direction rotation = new Direction(pitch, yaw, 0);

            Color color = new Color((byte)100, (byte)255, (byte)255); // Cyan

            SpawnParticleSystem packet = new SpawnParticleSystem(
                PARTICLE_ID,
                position,
                rotation,
                PARTICLE_SCALE,
                color
            );

            user.getPacketHandler().write(packet);
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Orb launched!").color(java.awt.Color.GREEN));
        } catch (Exception e) {
            System.err.println("[Tracker] Failed to spawn orb: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Spawn a failure effect (small explosion) at the user's position
     */
    private void spawnFailureEffect(PlayerRef user, Vector3d position) {
        try {
            // Spawn multiple particles in a burst pattern
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2 * i) / 8;
                double offsetX = Math.cos(angle) * 0.5;
                double offsetZ = Math.sin(angle) * 0.5;

                Position pos = new Position(
                    position.x + offsetX,
                    position.y + 1.5, // At eye level
                    position.z + offsetZ
                );
                Direction rotation = new Direction();
                Color color = new Color((byte)255, (byte)50, (byte)50); // Red

                SpawnParticleSystem packet = new SpawnParticleSystem(
                    FAIL_PARTICLE_ID,
                    pos,
                    rotation,
                    FAIL_PARTICLE_SCALE,
                    color
                );

                user.getPacketHandler().write(packet);
            }
        } catch (Exception e) {
            System.err.println("[Tracker] Failed to spawn failure effect: " + e.getMessage());
        }
    }

    /**
     * Spawn a single particle at a position
     */
    private void spawnParticle(PlayerRef viewer, double x, double y, double z, int r, int g, int b) {
        try {
            Position position = new Position(x, y, z);
            Direction rotation = new Direction();
            Color color = new Color((byte)r, (byte)g, (byte)b);

            SpawnParticleSystem packet = new SpawnParticleSystem(
                PARTICLE_ID,
                position,
                rotation,
                PARTICLE_SCALE,
                color
            );

            viewer.getPacketHandler().write(packet);
        } catch (Exception e) {
            System.err.println("[Tracker] Failed to spawn particle: " + e.getMessage());
        }
    }

    private double distance(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Track the nearest player from a Player entity (called from interaction system).
     * This is the main entry point from TrackerInteraction.
     */
    public void trackNearestPlayer(
            com.hypixel.hytale.server.core.entity.entities.Player playerEntity,
            Ref<EntityStore> entityRef,
            CommandBuffer<EntityStore> commandBuffer) {
        System.out.println("[Tracker] trackNearestPlayer called for: " + playerEntity);

        if (playerEntity == null) {
            System.out.println("[Tracker] Player entity is null");
            return;
        }

        // Find the PlayerRef for this player
        PlayerRef user = null;
        UUID userUuid = null;

        for (Map.Entry<UUID, PlayerRef> entry : onlinePlayers.entrySet()) {
            try {
                // Try to match by username or reference
                if (entry.getValue().getUsername().equals(playerEntity.getDisplayName())) {
                    user = entry.getValue();
                    userUuid = entry.getKey();
                    break;
                }
            } catch (Exception e) {
                // Continue searching
            }
        }

        if (user == null) {
            System.out.println("[Tracker] Could not find PlayerRef for player entity");
            return;
        }

        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(userUuid);
        if (lastUse != null && (now - lastUse) < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - lastUse)) / 1000 + 1;
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Cooldown: " + remaining + "s").color(java.awt.Color.GRAY));
            return;
        }

        // Set cooldown
        cooldowns.put(userUuid, now);

        // Find nearest other player
        PlayerRef nearestPlayer = findNearestPlayer(user, userUuid);
        if (nearestPlayer == null) {
            user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] No other players nearby!").color(java.awt.Color.YELLOW));
            return;
        }

        // Spawn tracking orb toward that player
        user.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Tracker] Tracking " + nearestPlayer.getUsername() + "...").color(java.awt.Color.CYAN));
        spawnTrackingOrb(user, nearestPlayer, commandBuffer);
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
