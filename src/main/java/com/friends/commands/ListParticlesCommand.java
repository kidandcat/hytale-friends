package com.friends.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import java.awt.Color;

/**
 * Command to list all available particle system IDs.
 * Usage: /particles [filter]
 */
public class ListParticlesCommand extends AbstractPlayerCommand {

    public ListParticlesCommand() {
        super("particles", "List available particle systems");
        setPermissionGroups(new String[]{"default", "player"});
    }

    @Override
    protected void execute(
            CommandContext context,
            Store<EntityStore> entityStore,
            Ref<EntityStore> entityRef,
            PlayerRef player,
            World world
    ) {
        String input = context.getInputString().replace("/particles", "").trim();
        String filter = input.isEmpty() ? null : input.toLowerCase();

        try {
            DefaultAssetMap<String, ParticleSystem> assetMap = ParticleSystem.getAssetMap();

            if (assetMap == null) {
                player.sendMessage(Message.raw("[Particles] Asset map not available").color(Color.RED));
                return;
            }

            player.sendMessage(Message.raw("[Particles] Available particle systems:").color(Color.CYAN));

            int count = 0;
            int shown = 0;

            // Use entrySet or try to get the underlying collection
            try {
                // Try to iterate through available assets
                var assetStore = ParticleSystem.getAssetStore();
                if (assetStore != null) {
                    // Log what we can find
                    player.sendMessage(Message.raw("[Particles] AssetStore found, checking assets...").color(Color.GRAY));

                    // Try many common particle names to find what exists
                    String[] commonNames = {
                        // Original guesses
                        "Magic_Hit", "Flying_Orb", "Torch_Fire", "Campfire_Smoke",
                        "Water_Splash", "Dust_Cloud", "Sparkle", "Heal_Effect",
                        "Block_Break", "Fire", "Smoke", "Explosion", "Portal",
                        "Heart", "Crit", "Damage", "Enchant", "Flame",
                        "Lava", "Note", "Redstone", "Slime", "Snow",
                        "Spell", "Splash", "Wake", "Witch", "Cloud",
                        // Candle/torch variants
                        "Candle", "Candle_Flame", "CandleFlame", "Candle_Fire",
                        "Torch", "TorchFlame", "Torch_Flame", "TorchFire",
                        // Small effects
                        "Glow", "Glint", "Shimmer", "Twinkle", "Flicker",
                        "Ember", "Embers", "Spark", "Sparks", "Firefly",
                        // More variations
                        "Magic", "Orb", "Light", "Wisp", "Soul",
                        "Bubble", "Drip", "Drop", "Mist", "Fog",
                        // Hit effects
                        "Hit", "Impact", "Strike", "Burst",
                        // Nature
                        "Leaf", "Leaves", "Pollen", "Spore", "Dust",
                        // Status effects
                        "Healing", "Poison", "Regen", "Buff", "Aura"
                    };

                    for (String name : commonNames) {
                        try {
                            var asset = assetMap.getAsset(name);
                            if (asset != null) {
                                if (filter == null || name.toLowerCase().contains(filter)) {
                                    player.sendMessage(Message.raw("  ✓ " + name).color(Color.GREEN));
                                    shown++;
                                }
                                count++;
                            }
                        } catch (Exception e) {
                            // Asset doesn't exist, skip
                        }
                    }

                    player.sendMessage(Message.raw("[Particles] Found " + count + " valid particle types").color(Color.GRAY));
                }
            } catch (Exception e) {
                player.sendMessage(Message.raw("[Particles] Could not iterate assets: " + e.getMessage()).color(Color.YELLOW));
            }

        } catch (Exception e) {
            player.sendMessage(Message.raw("[Particles] Error: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
    }
}
