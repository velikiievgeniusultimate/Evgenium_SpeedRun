package org.evgenium.speedrun.client.spectator;

import net.minecraft.client.Minecraft;
import org.evgenium.speedrun.client.match.RaceSession;
import org.evgenium.speedrun.client.ui.SpectatorTargetScreen;
import org.evgenium.speedrun.spectator.SpectatorItems;

public final class SpectatorController {
    private SpectatorController() {
    }

    public static void tick(Minecraft minecraft) {
        SpectatorRelayClient.tick(minecraft);
        if (!RaceSession.isLocalFinished() || minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }
        if (!minecraft.options.keyUse.consumeClick()) {
            return;
        }
        if (!SpectatorItems.isSelector(minecraft.player.getInventory().getSelectedItem())) {
            return;
        }
        minecraft.gui.setScreen(new SpectatorTargetScreen());
    }
}
