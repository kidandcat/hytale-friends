package com.friends.features.balloon;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.friends.FriendsPlugin;

/**
 * Custom interaction for toggling balloon flight when pressing F on the brazier.
 * This interaction is registered and used by block types that want balloon toggle functionality.
 */
public class BalloonToggleInteraction extends SimpleBlockInteraction {

    public static final BuilderCodec<BalloonToggleInteraction> CODEC =
        BuilderCodec.builder(BalloonToggleInteraction.class, BalloonToggleInteraction::new).build();

    public BalloonToggleInteraction() {
        super();
    }

    @Override
    protected void interactWithBlock(
            World world,
            CommandBuffer<EntityStore> commandBuffer,
            InteractionType interactionType,
            InteractionContext context,
            ItemStack itemStack,
            Vector3i blockPos,
            CooldownHandler cooldownHandler
    ) {
        System.out.println("[BalloonToggle] interactWithBlock called at " + blockPos);
        System.out.println("[BalloonToggle] InteractionType: " + interactionType);

        // Get the player from the interaction context
        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null) {
            System.out.println("[BalloonToggle] No entity ref");
            return;
        }

        // Get the player component
        Player player = commandBuffer.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            System.out.println("[BalloonToggle] No player component");
            return;
        }

        // Get the balloon system
        BalloonSystem balloonSystem = FriendsPlugin.getInstance().getBalloonSystem();
        if (balloonSystem == null) {
            System.out.println("[BalloonToggle] No balloon system");
            return;
        }

        // Find the balloon at this position and toggle it
        String posKey = blockPos.x + "," + blockPos.y + "," + blockPos.z;
        System.out.println("[BalloonToggle] Looking for balloon at " + posKey);

        // Call the balloon system to handle the toggle (pass null to skip block type check)
        if (balloonSystem.toggleBalloonAtPosition(blockPos)) {
            System.out.println("[BalloonToggle] Balloon toggled!");
        } else {
            System.out.println("[BalloonToggle] No balloon found at this position");
        }
    }

    @Override
    protected void simulateInteractWithBlock(
            InteractionType interactionType,
            InteractionContext context,
            ItemStack itemStack,
            World world,
            Vector3i blockPos
    ) {
        // Client-side simulation (visual feedback)
        System.out.println("[BalloonToggle] simulateInteractWithBlock at " + blockPos);
    }
}
