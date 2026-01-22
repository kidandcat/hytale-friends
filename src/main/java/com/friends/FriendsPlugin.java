package com.friends;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.friends.features.radar.PlayerRadarSystem;
import com.friends.features.balloon.BalloonSystem;
import com.friends.features.balloon.BalloonToggleInteraction;
import com.friends.commands.TestMarkerCommand;
import com.friends.commands.TestHudCommand;
import com.friends.commands.FakePlayerCommand;
import com.friends.commands.ListParticlesCommand;
import com.friends.commands.BalloonCommand;

/**
 * Hytale Friends Mod
 *
 * Features:
 * - Player radar: Shows all players on the HUD compass
 * - Hot Air Balloon: Rideable flying vehicle
 */
public class FriendsPlugin extends JavaPlugin {

    private static FriendsPlugin instance;
    private PlayerRadarSystem radarSystem;
    private BalloonSystem balloonSystem;

    public FriendsPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        System.out.println("[Friends] Setting up Hytale Friends mod...");

        // Register custom balloon toggle interaction
        try {
            var interactionRegistry = getCodecRegistry(Interaction.CODEC);
            interactionRegistry.register(
                "FriendsBalloonToggle",
                BalloonToggleInteraction.class,
                BalloonToggleInteraction.CODEC
            );
            System.out.println("[Friends] Registered BalloonToggle interaction");
        } catch (Exception e) {
            System.err.println("[Friends] Failed to register interaction: " + e.getMessage());
            e.printStackTrace();
        }

        // Initialize the player radar system
        radarSystem = new PlayerRadarSystem(this);

        // Initialize the hot air balloon system (shares players with radar)
        balloonSystem = new BalloonSystem(radarSystem.getOnlinePlayers());

        // Register commands
        getCommandRegistry().registerCommand(new TestMarkerCommand());
        getCommandRegistry().registerCommand(new TestHudCommand());
        getCommandRegistry().registerCommand(new FakePlayerCommand());
        getCommandRegistry().registerCommand(new ListParticlesCommand());
        getCommandRegistry().registerCommand(new BalloonCommand());

        // Register event listeners for player connect/disconnect
        getEventRegistry().register(PlayerConnectEvent.class, event -> {
            radarSystem.onPlayerConnect(event);
        });

        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            radarSystem.onPlayerDisconnect(event);
            // Clean up balloon state for disconnected player
            if (balloonSystem != null && event.getPlayerRef() != null) {
                balloonSystem.onPlayerDisconnect(event.getPlayerRef().getUuid());
            }
        });

        // Handle PlayerInteractEvent for F key on BlockEntities (brazier)
        // PlayerInteractEvent is keyed by player UUID string, use "*" for all players
        getEventRegistry().register(PlayerInteractEvent.class, "*", event -> {
            System.out.println("[Friends] PlayerInteractEvent fired!");
            System.out.println("[Friends] ActionType: " + event.getActionType());
            System.out.println("[Friends] TargetEntity: " + event.getTargetEntity());
            System.out.println("[Friends] TargetRef: " + event.getTargetRef());
            System.out.println("[Friends] TargetBlock: " + event.getTargetBlock());

            // Check if it's a Use interaction (F key) on an entity
            if (event.getActionType() == InteractionType.Use && balloonSystem != null) {
                var targetRef = event.getTargetRef();
                var targetEntity = event.getTargetEntity();

                if (targetRef != null) {
                    System.out.println("[Friends] Checking balloon interaction for ref: " + targetRef);
                    balloonSystem.onPlayerInteractRef(targetRef, null);
                } else if (targetEntity != null) {
                    System.out.println("[Friends] Checking balloon interaction for entity: " + targetEntity);
                    balloonSystem.onPlayerInteractEntity(targetEntity, null);
                }
            }
        });

        // UseBlockEvent for world blocks (F key interactions) - use registerGlobal
        getEventRegistry().registerGlobal(UseBlockEvent.Pre.class, event -> {
            // Log ALL UseBlockEvent.Pre events to see what's happening
            var blockType = event.getBlockType();
            String blockTypeKey = blockType != null ? blockType.getId() : null;
            System.out.println("[Friends] UseBlockEvent.Pre fired! Type=" + event.getInteractionType() + " Block=" + blockTypeKey + " Pos=" + event.getTargetBlock());

            // Check if it's a Use interaction (F key)
            if (event.getInteractionType() == InteractionType.Use && balloonSystem != null) {
                if (balloonSystem.onUseBlockEvent(event.getTargetBlock(), blockTypeKey)) {
                    System.out.println("[Friends] Balloon handled world block interaction!");
                }
            }
        });

        // Handle right-click near balloon to toggle flight
        // This is a fallback since F key interaction doesn't work on BlockEntities
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, event -> {
            // Log all mouse button events
            System.out.println("[Friends] MouseButtonEvent: " + event.getMouseButton().mouseButtonType + " " + event.getMouseButton().state);

            // Only handle right-click press (not release)
            if (event.getMouseButton().mouseButtonType != MouseButtonType.Right) {
                return;
            }
            if (event.getMouseButton().state != MouseButtonState.Pressed) {
                return;
            }

            var playerRef = event.getPlayerRefComponent();
            if (playerRef == null || balloonSystem == null) {
                return;
            }

            // Check if player is near a balloon (within 3 blocks)
            Integer nearestBalloon = balloonSystem.getNearestBalloonInRange(playerRef.getUuid(), 3.0);
            if (nearestBalloon != null) {
                System.out.println("[Friends] Right-click detected near balloon #" + nearestBalloon);
                balloonSystem.toggle(nearestBalloon, playerRef.getUuid());
            }
        });

        System.out.println("[Friends] Player radar and balloon initialized");
    }

    @Override
    protected void start() {
        System.out.println("[Friends] Hytale Friends v0.1.0 loaded!");
        System.out.println("[Friends] Players now visible on HUD compass");

        // Start the radar update loop
        radarSystem.start();

        // Start the balloon flight system
        balloonSystem.start();
        System.out.println("[Friends] Hot Air Balloon system ready! Use /balloon to spawn one.");
    }

    @Override
    protected void shutdown() {
        System.out.println("[Friends] Shutting down...");
        if (radarSystem != null) {
            radarSystem.stop();
        }
        if (balloonSystem != null) {
            balloonSystem.shutdown();
        }
    }

    public static FriendsPlugin getInstance() {
        return instance;
    }

    public PlayerRadarSystem getRadarSystem() {
        return radarSystem;
    }

    public BalloonSystem getBalloonSystem() {
        return balloonSystem;
    }
}
