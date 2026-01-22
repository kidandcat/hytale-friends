package com.friends.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.friends.FriendsPlugin;
import com.friends.features.balloon.BalloonSystem;

import java.awt.Color;

/**
 * Command to spawn or manage Hot Air Balloons.
 *
 * Usage:
 *   /balloon        - Spawn a balloon 5 units in front of you
 *   /balloon on     - Turn on the nearest balloon (starts flying)
 *   /balloon off    - Turn off the nearest balloon (stops flying)
 *   /balloon remove - Remove all balloons
 *   /balloon list   - List all balloon IDs
 */
public class BalloonCommand extends AbstractPlayerCommand {

    public BalloonCommand() {
        super("balloon", "Spawn or manage Hot Air Balloons");
        setAllowsExtraArguments(true);
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
        String args = input.replace("/balloon", "").replace("balloon", "").trim().toLowerCase();

        BalloonSystem balloonSystem = FriendsPlugin.getInstance().getBalloonSystem();
        if (balloonSystem == null) {
            player.sendMessage(Message.raw("[Balloon] Error: Balloon system not initialized").color(Color.RED));
            return;
        }

        // Handle subcommands
        if (args.equals("remove") || args.equals("clear")) {
            // Remove all balloons
            int count = 0;
            for (Integer id : balloonSystem.getBalloonIds().toArray(new Integer[0])) {
                balloonSystem.removeBalloon(id);
                count++;
            }
            player.sendMessage(Message.raw("[Balloon] Removed " + count + " balloon(s).").color(Color.YELLOW));
            return;
        }

        if (args.equals("list")) {
            // List all balloons
            var ids = balloonSystem.getBalloonIds();
            if (ids.isEmpty()) {
                player.sendMessage(Message.raw("[Balloon] No balloons in the world.").color(Color.GRAY));
            } else {
                player.sendMessage(Message.raw("[Balloon] Active balloons: " + ids).color(Color.CYAN));
            }
            return;
        }

        if (args.equals("on") || args.isEmpty()) {
            // Toggle the nearest balloon - if it's off, turn on; if on, turn off
            Integer nearestId = balloonSystem.getNearestBalloon(player.getUuid());
            if (nearestId == null) {
                // No balloon nearby - spawn a new one
                if (!args.isEmpty()) {
                    player.sendMessage(Message.raw("[Balloon] No balloons nearby.").color(Color.RED));
                    return;
                }
                // Fall through to spawn logic below
            } else {
                // Toggle the nearest balloon
                balloonSystem.toggle(nearestId, player.getUuid());
                return;
            }
        }

        if (args.equals("off")) {
            // Turn off nearest balloon
            Integer nearestId = balloonSystem.getNearestBalloon(player.getUuid());
            if (nearestId == null) {
                player.sendMessage(Message.raw("[Balloon] No balloons nearby.").color(Color.RED));
                return;
            }
            balloonSystem.turnOff(nearestId, player.getUuid());
            return;
        }

        if (args.equals("on")) {
            // Turn on nearest balloon (explicit)
            Integer nearestId = balloonSystem.getNearestBalloon(player.getUuid());
            if (nearestId == null) {
                player.sendMessage(Message.raw("[Balloon] No balloons nearby.").color(Color.RED));
                return;
            }
            balloonSystem.turnOn(nearestId, player.getUuid());
            return;
        }

        // Default: spawn a balloon in front of player
        try {
            Vector3d playerPos = player.getTransform().getPosition();
            float playerYaw = player.getTransform().getRotation().getYaw();

            // Spawn the balloon entity using the entity store
            int balloonId = balloonSystem.spawnBalloon(playerPos, player.getWorldUuid(), entityStore, playerYaw);

            if (balloonId > 0) {
                player.sendMessage(Message.raw("[Balloon] Spawned balloon #" + balloonId + " in front of you!").color(Color.GREEN));
                player.sendMessage(Message.raw("[Balloon] Right-click the center block to toggle flight!").color(Color.GRAY));
            } else {
                player.sendMessage(Message.raw("[Balloon] Failed to spawn balloon.").color(Color.RED));
            }

        } catch (Exception e) {
            player.sendMessage(Message.raw("[Balloon] Error: " + e.getMessage()).color(Color.RED));
            System.err.println("[Balloon] Command error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
