package org.evgenium.speedrun.client.spectator;

import net.minecraft.client.Minecraft;
import org.evgenium.speedrun.client.match.RaceSession;
import org.evgenium.speedrun.client.ui.SpectatorTargetScreen;

public final class SpectatorController {
    private SpectatorController() {
    }

    public static void tick(Minecraft minecraft) {
        SpectatorRelayClient.tick(minecraft);
        if (!RaceSession.isLocalFinished()
            || minecraft.player == null
            || !minecraft.player.isSpectator()
            || minecraft.gui.screen() != null) {
            return;
        }

        // The finished-player inventory is intentionally virtual: vanilla spectator mode has
        // no normal hotbar. Right click always activates our single selector slot.
        if (minecraft.options.keyUse.consumeClick()) {
            minecraft.gui.setScreen(new SpectatorTargetScreen());
        }
    }
}
