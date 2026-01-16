package com.friends.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.friends.features.hud.FriendsHud;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test command for CustomHud using HudManager.
 *
 * Usage:
 *   /testhud - Show the Friends HUD
 *   /testhud clear - Hide the HUD
 */
public class TestHudCommand extends AbstractPlayerCommand {

    private static final Map<UUID, FriendsHud> activeHuds = new HashMap<>();

    public TestHudCommand() {
        super("testhud", "Test Friends HUD display");
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
        String args = rawInput.replace("/testhud", "").replace("testhud", "").trim().toLowerCase();

        if (args.equals("clear") || args.equals("remove")) {
            // Remove HUD
            activeHuds.remove(player.getUuid());
            player.sendMessage(Message.raw("HUD cleared.").color(Color.YELLOW));
            return;
        }

        try {
            // Get the Player component to access HudManager
            Player playerEntity = entityStore.getComponent(entityRef, Player.getComponentType());

            if (playerEntity == null) {
                player.sendMessage(Message.raw("Error: Could not get player entity").color(Color.RED));
                return;
            }

            // Create and register the HUD
            FriendsHud hud = new FriendsHud(player);
            activeHuds.put(player.getUuid(), hud);

            // Register with HudManager and show
            playerEntity.getHudManager().setCustomHud(player, hud);
            hud.show();

            player.sendMessage(Message.raw("HUD shown! Look at top-right of screen.").color(Color.GREEN));
            player.sendMessage(Message.raw("Use '/testhud clear' to remove it.").color(Color.GRAY));

        } catch (Exception e) {
            player.sendMessage(Message.raw("Error: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
    }
}
