package com.friends.features.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

/**
 * Custom HUD that displays nearby players and their distances.
 */
public class FriendsHud extends CustomUIHud {

    public FriendsHud(PlayerRef player) {
        super(player);
    }

    @Override
    protected void build(UICommandBuilder builder) {
        // Try appending to our HUD's PlayerList container
        builder.append("#FriendsHud #Content #PlayerList", "Label #TestLabel {\n  Style: LabelStyle(FontSize: 14);\n}");
    }
}
