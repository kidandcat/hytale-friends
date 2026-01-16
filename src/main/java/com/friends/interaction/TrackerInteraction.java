package com.friends.interaction;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.friends.FriendsPlugin;

/**
 * Custom interaction that triggers the tracker functionality.
 * When the player uses an item with this interaction, it shows a particle trail
 * to the nearest other player.
 */
public class TrackerInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<TrackerInteraction> CODEC = BuilderCodec.builder(
        TrackerInteraction.class,
        TrackerInteraction::new,
        SimpleInstantInteraction.CODEC
    ).build();

    public TrackerInteraction() {
        super();
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        System.out.println("[Friends] TrackerInteraction.firstRun called! Type: " + type);

        // Get the entity from the context (following WarpHomeInteraction pattern)
        Ref<EntityStore> entityRef = context.getEntity();
        ComponentAccessor<EntityStore> commandBuffer = context.getCommandBuffer();

        if (entityRef == null || commandBuffer == null) {
            System.out.println("[Friends] Entity ref or command buffer is null");
            return;
        }

        Entity entity = EntityUtils.getEntity(entityRef, commandBuffer);
        if (!(entity instanceof Player)) {
            System.out.println("[Friends] Entity is not a player: " + entity);
            return;
        }

        Player player = (Player) entity;
        System.out.println("[Friends] Player found: " + player);

        // Get the CommandBuffer which has entity spawning capabilities
        com.hypixel.hytale.component.CommandBuffer<EntityStore> cmdBuffer =
            (com.hypixel.hytale.component.CommandBuffer<EntityStore>) commandBuffer;

        // Delegate to the tracker system with CommandBuffer for entity spawning
        FriendsPlugin plugin = FriendsPlugin.getInstance();
        if (plugin != null && plugin.getTrackerSystem() != null) {
            plugin.getTrackerSystem().trackNearestPlayer(player, entityRef, cmdBuffer);
        } else {
            System.out.println("[Friends] Plugin or tracker system not initialized");
        }
    }
}
