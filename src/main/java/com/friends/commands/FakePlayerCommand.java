package com.friends.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.friends.FriendsPlugin;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;

/**
 * Command to add/remove a fake player for testing the tracker.
 *
 * Usage:
 *   /fakeplayer [distance] - Add fake player at distance blocks ahead (default: 50)
 *   /fakeplayer remove     - Remove the fake player
 *   /fakeplayer x y z      - Add fake player at specific coordinates
 */
public class FakePlayerCommand extends AbstractPlayerCommand {

    private static FakePlayerRef fakePlayer = null;
    private static final UUID FAKE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public FakePlayerCommand() {
        super("fakeplayer", "Add a fake player for testing tracker");
    }

    @Override
    protected void execute(
            CommandContext context,
            Store<EntityStore> entityStore,
            Ref<EntityStore> entityRef,
            PlayerRef player,
            World world
    ) {
        String input = context.getInputString();
        String args = input.replace("/fakeplayer", "").replace("fakeplayer", "").trim();

        Map<UUID, PlayerRef> onlinePlayers = FriendsPlugin.getInstance().getRadarSystem().getOnlinePlayers();

        // Remove command
        if (args.equalsIgnoreCase("remove") || args.equalsIgnoreCase("clear")) {
            if (fakePlayer != null) {
                onlinePlayers.remove(FAKE_UUID);
                fakePlayer = null;
                player.sendMessage(Message.raw("[Test] Fake player removed.").color(Color.YELLOW));
            } else {
                player.sendMessage(Message.raw("[Test] No fake player to remove.").color(Color.GRAY));
            }
            return;
        }

        // Get player position
        Vector3d playerPos = player.getTransform().getPosition();
        double x, y, z;

        String[] parts = args.split("\\s+");

        if (parts.length >= 3) {
            // Specific coordinates: /fakeplayer x y z
            try {
                x = Double.parseDouble(parts[0]);
                y = Double.parseDouble(parts[1]);
                z = Double.parseDouble(parts[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(Message.raw("[Test] Invalid coordinates!").color(Color.RED));
                return;
            }
        } else {
            // No args: spawn at player's current position
            x = playerPos.x;
            y = playerPos.y;
            z = playerPos.z;
        }

        // Remove old fake player if exists
        if (fakePlayer != null) {
            onlinePlayers.remove(FAKE_UUID);
        }

        // Create fake player
        Vector3d fakePos = new Vector3d(x, y, z);
        fakePlayer = new FakePlayerRef(FAKE_UUID, "FakePlayer", fakePos);

        // Add to online players map
        onlinePlayers.put(FAKE_UUID, fakePlayer);

        player.sendMessage(Message.raw("[Test] Fake player added at (" +
            String.format("%.1f", x) + ", " +
            String.format("%.1f", y) + ", " +
            String.format("%.1f", z) + ")").color(Color.GREEN));
        player.sendMessage(Message.raw("[Test] Distance: " +
            String.format("%.1f", distance(playerPos, fakePos)) + " blocks").color(Color.CYAN));
        player.sendMessage(Message.raw("[Test] Use '/fakeplayer remove' to remove it.").color(Color.GRAY));
    }

    private double distance(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Find ground level by iterating downward from startY until we find a solid block.
     * Returns the Y coordinate just above the ground, or -1 if no ground found.
     */
    private double findGroundLevel(World world, double x, double startY, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int startBlockY = (int) Math.floor(startY);

        try {
            // Get chunk accessor for this position
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            BlockAccessor accessor = world.getChunkIfLoaded(chunkKey);

            if (accessor == null) {
                System.out.println("[FakePlayer] Chunk not loaded at " + blockX + ", " + blockZ);
                return startY; // Fallback to original Y if chunk not loaded
            }

            // Iterate downward to find ground
            for (int y = startBlockY; y > 0; y--) {
                BlockType blockType = accessor.getBlockType(blockX, y, blockZ);

                // Check if this block is solid (not empty/air)
                if (blockType != null && blockType != BlockType.EMPTY) {
                    // Found solid ground, return position just above it
                    System.out.println("[FakePlayer] Found ground at Y=" + y + " (block: " + blockType + ")");
                    return y + 1.0;
                }
            }

            System.out.println("[FakePlayer] No ground found, using original Y");
            return startY;
        } catch (Exception e) {
            System.err.println("[FakePlayer] Error finding ground: " + e.getMessage());
            return startY; // Fallback to original Y on error
        }
    }

    /**
     * Minimal fake PlayerRef implementation for testing
     */
    private static class FakePlayerRef extends PlayerRef {
        private final UUID uuid;
        private final String username;
        private final Vector3d position;

        public FakePlayerRef(UUID uuid, String username, Vector3d position) {
            super(null, uuid, username, "en", null, null);
            this.uuid = uuid;
            this.username = username;
            this.position = position;
        }

        @Override
        public UUID getUuid() {
            return uuid;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public com.hypixel.hytale.math.vector.Transform getTransform() {
            return new com.hypixel.hytale.math.vector.Transform(position);
        }

        @Override
        public com.hypixel.hytale.component.Ref<EntityStore> getReference() {
            return null; // Fake player has no real entity ref
        }
    }
}
