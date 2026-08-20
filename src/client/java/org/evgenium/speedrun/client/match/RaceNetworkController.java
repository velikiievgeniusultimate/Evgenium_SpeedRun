package org.evgenium.speedrun.client.match;

import net.minecraft.client.Minecraft;
import org.evgenium.speedrun.client.ui.RaceConnectionScreen;

/** Applies the user-facing safety barrier when control-channel or host-time sync is unhealthy. */
public final class RaceNetworkController {
    private RaceNetworkController() {
    }

    public static void tick(Minecraft minecraft) {
        boolean blocked = shouldBlockRunner();

        if (blocked && RaceSession.isRunning() && !RaceSession.isLocalFinished()) {
            if (!(minecraft.gui.screen() instanceof RaceConnectionScreen)) {
                minecraft.gui.setScreen(new RaceConnectionScreen());
            }
            return;
        }

        if (!blocked && minecraft.gui.screen() instanceof RaceConnectionScreen) {
            minecraft.gui.setScreen(null);
        }
    }

    public static boolean shouldBlockRunner() {
        return RaceSession.hasRunConfig()
            && !RaceSession.isLocalFinished()
            && !RaceClockSync.isSafe();
    }
}
