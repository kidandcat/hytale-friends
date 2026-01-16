package com.friends.features.radar;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * PlayerRadarSystem - Shows all players on the HUD compass
 *
 * Automatically displays other players on the top HUD compass bar
 * (the same place where portals, death markers, etc. appear).
 *
 * This is always enabled for all players - no commands needed.
 */
public class PlayerRadarSystem {

    private final JavaPlugin plugin;

    // All online players
    private final Map<UUID, PlayerRef> onlinePlayers = new ConcurrentHashMap<>();

    // Track which markers each player currently has (viewer UUID -> Set of target UUIDs)
    private final Map<UUID, Set<UUID>> activeMarkers = new ConcurrentHashMap<>();

    // Radar update scheduler
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> updateTask;

    // Configuration
    private static final long UPDATE_INTERVAL_MS = 500; // Update positions every 500ms
    private static final String MARKER_IMAGE = "Player.png"; // Icon for player markers
    private static final String MARKER_PREFIX = "friend_"; // Prefix for marker IDs

    // Track previous marker IDs to remove them on next update
    private final Map<UUID, Set<String>> previousMarkerIds = new ConcurrentHashMap<>();

    // Update counter to force unique marker IDs
    private long updateCounter = 0;

    public PlayerRadarSystem(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the radar update loop
     */
    public void start() {
        if (updateTask != null && !updateTask.isCancelled()) {
            return;
        }

        updateTask = scheduler.scheduleAtFixedRate(
                this::updateAllPlayerMarkers,
                0,
                UPDATE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        System.out.println("[Friends] Player radar started (updates every " + UPDATE_INTERVAL_MS + "ms)");
    }

    /**
     * Stop the radar update loop
     */
    public void stop() {
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
        scheduler.shutdown();
        System.out.println("[Friends] Player radar stopped");
    }

    /**
     * Main update loop - sends player positions to all players' HUD compass
     */
    private void updateAllPlayerMarkers() {
        try {
            updateCounter++; // Increment for unique marker IDs

            // For each online player, send them markers for all other players
            for (Map.Entry<UUID, PlayerRef> viewerEntry : onlinePlayers.entrySet()) {
                PlayerRef viewer = viewerEntry.getValue();
                updateMarkersForViewer(viewer);
            }
        } catch (Exception e) {
            System.err.println("[Friends] Error updating player markers: " + e.getMessage());
        }
    }

    /**
     * Update the HUD compass markers for a specific viewer
     */
    private void updateMarkersForViewer(PlayerRef viewer) {
        UUID viewerUuid = viewer.getUuid();
        Vector3d viewerPos = viewer.getTransform().getPosition();
        List<MapMarker> markersToAdd = new ArrayList<>();
        Set<String> newMarkerIds = ConcurrentHashMap.newKeySet();

        // Create markers for all other players
        for (Map.Entry<UUID, PlayerRef> targetEntry : onlinePlayers.entrySet()) {
            UUID targetUuid = targetEntry.getKey();
            PlayerRef target = targetEntry.getValue();

            // Skip self - don't show yourself on the compass
            if (targetUuid.equals(viewerUuid)) {
                continue;
            }

            // Create a marker for this player with distance (unique ID each update)
            MapMarker marker = createPlayerMarker(target, viewerPos);
            markersToAdd.add(marker);
            newMarkerIds.add(marker.id);
        }

        // Get previous marker IDs to remove
        Set<String> oldMarkerIds = previousMarkerIds.getOrDefault(viewerUuid, Set.of());

        // Send the update packet to the viewer
        sendMarkerUpdate(viewer, markersToAdd, oldMarkerIds);

        // Store current marker IDs for next update
        previousMarkerIds.put(viewerUuid, newMarkerIds);
    }

    /**
     * Create a MapMarker for a player to show on the HUD compass
     */
    private MapMarker createPlayerMarker(PlayerRef player, Vector3d viewerPos) {
        Vector3d pos = player.getTransform().getPosition();

        // Calculate distance from viewer to target
        double dx = pos.x - viewerPos.x;
        double dy = pos.y - viewerPos.y;
        double dz = pos.z - viewerPos.z;
        int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Create position for the marker
        Position position = new Position(pos.x, pos.y, pos.z);

        // Create orientation (facing direction)
        Direction direction = new Direction();

        // Create transform with position and orientation
        Transform transform = new Transform(position, direction);

        // Create the marker
        // ID must be unique per update to force refresh
        String markerId = MARKER_PREFIX + player.getUuid().toString() + "_" + updateCounter;
        String displayName = player.getUsername() + " (" + distance + "m)";

        return new MapMarker(
                markerId,           // Unique ID
                displayName,        // Player's username + distance shown on compass
                MARKER_IMAGE,       // Icon to display
                transform,          // Position in world
                null                // No context menu items
        );
    }

    /**
     * Send marker update packet to a player
     */
    private void sendMarkerUpdate(PlayerRef viewer, List<MapMarker> markers, Set<String> oldMarkerIds) {
        try {
            // Remove old markers first (from previous update)
            if (!oldMarkerIds.isEmpty()) {
                UpdateWorldMap removePacket = new UpdateWorldMap(
                        null,
                        new MapMarker[0],
                        oldMarkerIds.toArray(new String[0])
                );
                viewer.getPacketHandler().write(removePacket);
            }

            // Add new markers with updated data
            if (!markers.isEmpty()) {
                UpdateWorldMap addPacket = new UpdateWorldMap(
                        null,
                        markers.toArray(new MapMarker[0]),
                        new String[0]
                );
                viewer.getPacketHandler().write(addPacket);
            }

        } catch (Exception e) {
            System.err.println("[Friends] Failed to send marker update to " +
                    viewer.getUsername() + ": " + e.getMessage());
        }
    }

    /**
     * Remove a player's marker from all viewers
     */
    private void removePlayerMarkerFromAll(PlayerRef removedPlayer) {
        UUID removedUuid = removedPlayer.getUuid();

        for (Map.Entry<UUID, PlayerRef> viewerEntry : onlinePlayers.entrySet()) {
            UUID viewerUuid = viewerEntry.getKey();
            PlayerRef viewer = viewerEntry.getValue();

            try {
                // Find and remove markers for this player from the viewer's previous IDs
                Set<String> prevIds = previousMarkerIds.get(viewerUuid);
                if (prevIds != null) {
                    String[] markersToRemove = prevIds.stream()
                            .filter(id -> id.contains(removedUuid.toString()))
                            .toArray(String[]::new);

                    if (markersToRemove.length > 0) {
                        UpdateWorldMap packet = new UpdateWorldMap(
                                null,
                                new MapMarker[0],
                                markersToRemove
                        );
                        viewer.getPacketHandler().write(packet);

                        // Remove from tracked IDs
                        for (String id : markersToRemove) {
                            prevIds.remove(id);
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore errors when removing markers
            }
        }
    }

    // ==================== Event Handlers ====================

    /**
     * Handle player connect - add to tracking and create their marker for others
     */
    public void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        UUID uuid = player.getUuid();

        // Add to tracking
        onlinePlayers.put(uuid, player);
        activeMarkers.put(uuid, ConcurrentHashMap.newKeySet());

        System.out.println("[Friends] Player connected: " + player.getUsername() +
                " (tracking " + onlinePlayers.size() + " players)");

        // Send existing players to the new player immediately
        updateMarkersForViewer(player);
    }

    /**
     * Handle player disconnect - remove from tracking and remove their marker
     */
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        UUID uuid = player.getUuid();

        // Remove their marker from everyone else's compass
        removePlayerMarkerFromAll(player);

        // Remove from tracking
        onlinePlayers.remove(uuid);
        activeMarkers.remove(uuid);

        System.out.println("[Friends] Player disconnected: " + player.getUsername() +
                " (tracking " + onlinePlayers.size() + " players)");
    }

    /**
     * Get the total number of online players being tracked
     */
    public int getOnlinePlayerCount() {
        return onlinePlayers.size();
    }

    /**
     * Get the map of online players (for other systems to use)
     */
    public Map<UUID, PlayerRef> getOnlinePlayers() {
        return onlinePlayers;
    }
}
