package com.friends;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.friends.features.radar.PlayerRadarSystem;
import com.friends.features.tracker.PlayerTrackerSystem;
import com.friends.commands.TestMarkerCommand;
import com.friends.commands.TestHudCommand;
import com.friends.commands.FakePlayerCommand;
import com.friends.commands.ListParticlesCommand;
import com.friends.interaction.TrackerInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

/**
 * Hytale Friends Mod
 *
 * Quality of life improvements for playing with friends:
 * - Player radar: Automatically shows all players on the top HUD compass
 *   (same place where portals, death markers, etc. appear)
 */
public class FriendsPlugin extends JavaPlugin {

    private static FriendsPlugin instance;
    private PlayerRadarSystem radarSystem;
    private PlayerTrackerSystem trackerSystem;

    public FriendsPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        System.out.println("[Friends] Setting up Hytale Friends mod...");

        // Register the tracker interaction type
        getCodecRegistry(Interaction.CODEC).register(
            "FriendsTracker",
            TrackerInteraction.class,
            TrackerInteraction.CODEC
        );
        System.out.println("[Friends] Registered TrackerInteraction");

        // Initialize the player radar system
        radarSystem = new PlayerRadarSystem(this);

        // Register test commands
        getCommandRegistry().registerCommand(new TestMarkerCommand());
        getCommandRegistry().registerCommand(new TestHudCommand());
        getCommandRegistry().registerCommand(new FakePlayerCommand());
        getCommandRegistry().registerCommand(new ListParticlesCommand());

        // Initialize the player tracker system (shares players with radar)
        trackerSystem = new PlayerTrackerSystem(radarSystem.getOnlinePlayers());

        // Register event listeners for player connect/disconnect
        getEventRegistry().register(PlayerConnectEvent.class, event -> {
            radarSystem.onPlayerConnect(event);
        });

        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            radarSystem.onPlayerDisconnect(event);
        });

        // Register interact event for tracker item (global to catch all interactions)
        getEventRegistry().registerGlobal(PlayerInteractEvent.class, event -> {
            System.out.println("[Friends] PlayerInteractEvent fired! Action: " + event.getActionType());
            trackerSystem.onPlayerInteract(event);
        });

        System.out.println("[Friends] Player radar and tracker initialized");
    }

    @Override
    protected void start() {
        System.out.println("[Friends] Hytale Friends v0.1.0 loaded!");
        System.out.println("[Friends] Players now visible on HUD compass");

        // Start the radar update loop
        radarSystem.start();
    }

    @Override
    protected void shutdown() {
        System.out.println("[Friends] Shutting down...");
        if (radarSystem != null) {
            radarSystem.stop();
        }
        if (trackerSystem != null) {
            trackerSystem.shutdown();
        }
    }

    public static FriendsPlugin getInstance() {
        return instance;
    }

    public PlayerRadarSystem getRadarSystem() {
        return radarSystem;
    }

    public PlayerTrackerSystem getTrackerSystem() {
        return trackerSystem;
    }
}
