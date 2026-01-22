package com.friends.features.balloon;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3d;
import com.friends.FriendsPlugin;

/**
 * Custom interaction for toggling balloon flight when pressing F on the brazier.
 * This is triggered when the player presses F on a block with this interaction type.
 *
 * Based on WarpCrystals mod pattern - extends SimpleInstantInteraction.
 */
public class BalloonToggleInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<BalloonToggleInteraction> CODEC =
        BuilderCodec.builder(BalloonToggleInteraction.class, BalloonToggleInteraction::new).build();

    public BalloonToggleInteraction() {
        super();
    }

    @Override
    protected void firstRun(
            InteractionType interactionType,
            InteractionContext context,
            CooldownHandler cooldownHandler
    ) {
        System.out.println("[BalloonToggle] ============================================");
        System.out.println("[BalloonToggle] firstRun() CALLED!");
        System.out.println("[BalloonToggle] InteractionType: " + interactionType);
        System.out.println("[BalloonToggle] ============================================");

        // Get the command buffer to access entity data
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            System.out.println("[BalloonToggle] CommandBuffer is null");
            return;
        }

        // Get the entity (player) from the interaction context
        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null) {
            System.out.println("[BalloonToggle] No entity ref");
            return;
        }

        System.out.println("[BalloonToggle] Entity ref: " + entityRef);

        // Get the store to access components
        Store<EntityStore> store = commandBuffer.getExternalData().getStore();

        // Get the transform component to find player location
        TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
        Vector3d position = null;
        if (transform != null) {
            position = transform.getPosition();
            System.out.println("[BalloonToggle] Player position: " + position.x + ", " + position.y + ", " + position.z);
        } else {
            System.out.println("[BalloonToggle] No transform component");
        }

        // Get the balloon system
        BalloonSystem balloonSystem = FriendsPlugin.getInstance().getBalloonSystem();
        if (balloonSystem == null) {
            System.out.println("[BalloonToggle] No balloon system");
            return;
        }

        // Try to find a balloon near the player and toggle it
        if (position != null) {
            Integer nearestBalloon = balloonSystem.getNearestBalloonInRangeByPosition(
                position.x,
                position.y,
                position.z,
                5.0
            );

            if (nearestBalloon != null) {
                System.out.println("[BalloonToggle] Found balloon #" + nearestBalloon + " near player, toggling!");
                balloonSystem.toggle(nearestBalloon, null); // Pass null for UUID, toggle will handle it
            } else {
                System.out.println("[BalloonToggle] No balloon found near player");
            }
        } else {
            System.out.println("[BalloonToggle] Cannot find balloon - no position available");
        }
    }
}
