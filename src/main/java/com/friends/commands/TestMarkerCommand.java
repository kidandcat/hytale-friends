package com.friends.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Test command to spawn fake markers with different image formats.
 *
 * Usage:
 *   /testmarker        - Spawn test markers with different image formats
 *   /testmarker clear  - Remove all test markers
 */
public class TestMarkerCommand extends AbstractPlayerCommand {

    // Different image path formats to try
    private static final String[] IMAGE_FORMATS = {
        "Player.png",                                    // 0: just filename
        "Common/UI/WorldMap/MapMarkers/Player.png",     // 1: full path
        "Death.png",                                     // 2: death marker
        "Portal.png"                                     // 3: portal marker
    };

    public TestMarkerCommand() {
        super("testmarker", "Spawn test markers to find correct image format");
    }

    @Override
    protected void execute(
            CommandContext context,
            Store<EntityStore> entityStore,
            Ref<EntityStore> entityRef,
            PlayerRef player,
            World world
    ) {
        String rawInput = context.getInputString();
        String args = rawInput.replace("/testmarker", "").replace("testmarker", "").trim().toLowerCase();

        if (args.equals("clear") || args.equals("remove")) {
            removeAllTestMarkers(player);
            player.sendMessage(Message.raw("All test markers removed.").color(Color.YELLOW));
            return;
        }

        Vector3d playerPos = player.getTransform().getPosition();
        List<MapMarker> markers = new ArrayList<>();

        // Create markers in a line, each with different image format
        for (int i = 0; i < IMAGE_FORMATS.length; i++) {
            String imageFormat = IMAGE_FORMATS[i];

            // Position markers in a line going north, 15 blocks apart
            double markerX = playerPos.x;
            double markerY = playerPos.y;
            double markerZ = playerPos.z - 20 - (i * 15);

            Position position = new Position(markerX, markerY, markerZ);
            Direction direction = new Direction();
            Transform transform = new Transform(position, direction);

            String markerId = "test_marker_" + i;
            int distance = 20 + (i * 15); // Distance from player
            String displayName = "Player" + i + " (" + distance + "m)";

            MapMarker marker = new MapMarker(
                    markerId,
                    displayName,
                    imageFormat,
                    transform,
                    null
            );
            markers.add(marker);
        }

        try {
            UpdateWorldMap packet = new UpdateWorldMap(
                    null,
                    markers.toArray(new MapMarker[0]),
                    new String[0]
            );
            player.getPacketHandler().write(packet);

            player.sendMessage(Message.raw("Spawned " + IMAGE_FORMATS.length + " test markers!").color(Color.GREEN));
            player.sendMessage(Message.raw("Each marker uses a different image format:").color(Color.GRAY));
            for (int i = 0; i < IMAGE_FORMATS.length; i++) {
                player.sendMessage(Message.raw("  " + i + ": " + IMAGE_FORMATS[i]).color(Color.WHITE));
            }
            player.sendMessage(Message.raw("Look north on your compass - find which one has correct icon!").color(Color.CYAN));
            player.sendMessage(Message.raw("Use '/testmarker clear' to remove them.").color(Color.GRAY));

        } catch (Exception e) {
            player.sendMessage(Message.raw("Error: " + e.getMessage()).color(Color.RED));
        }
    }

    private String getShortName(String format) {
        if (format.contains("/")) {
            String[] parts = format.split("/");
            return parts[parts.length - 1];
        }
        return format;
    }

    private void removeAllTestMarkers(PlayerRef player) {
        List<String> markerIds = new ArrayList<>();
        for (int i = 0; i < IMAGE_FORMATS.length; i++) {
            markerIds.add("test_marker_" + i);
        }

        try {
            UpdateWorldMap packet = new UpdateWorldMap(
                    null,
                    new MapMarker[0],
                    markerIds.toArray(new String[0])
            );
            player.getPacketHandler().write(packet);
        } catch (Exception e) {
            // Ignore
        }
    }
}
