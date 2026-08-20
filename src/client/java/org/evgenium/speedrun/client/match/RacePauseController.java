package org.evgenium.speedrun.client.match;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.server.IntegratedServer;
import org.evgenium.speedrun.EvgeniumSpeedRun;

/**
 * Single owner of integrated-server freeze state during a race.
 *
 * Freeze reasons are intentionally combined here so ESC cannot accidentally unfreeze a world
 * that is frozen for lost networking/time sync, and reconnect cannot unfreeze a world that is
 * still on the pre-GO waiting screen.
 */
public final class RacePauseController {
    private static volatile IntegratedServer frozenServer;
    private static volatile String frozenReason = "";

    private RacePauseController() {
    }

    public static void tick(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        boolean preStart = RaceSession.isWaitingForGo();
        boolean network = RaceNetworkController.shouldBlockRunner();
        boolean esc = RaceSession.isRunning()
            && minecraft.gui.screen() instanceof PauseScreen;

        boolean shouldFreeze = RaceSession.hasRunConfig()
            && !RaceSession.isLocalFinished()
            && server != null
            && (preStart || network || esc);

        String reason = preStart ? "PRE_START" : network ? "NETWORK_SYNC" : esc ? "ESC" : "";
        IntegratedServer previous = frozenServer;

        if (shouldFreeze) {
            if (previous == server) {
                frozenReason = reason;
                return;
            }
            if (previous != null) {
                setFrozen(previous, false);
            }
            frozenServer = server;
            frozenReason = reason;
            setFrozen(server, true);
            EvgeniumSpeedRun.LOGGER.info("Race simulation frozen: {}", reason);
            return;
        }

        if (previous != null) {
            frozenServer = null;
            String oldReason = frozenReason;
            frozenReason = "";
            setFrozen(previous, false);
            EvgeniumSpeedRun.LOGGER.info("Race simulation resumed; previous reason={}", oldReason);
        }
    }

    private static void setFrozen(IntegratedServer server, boolean frozen) {
        server.execute(() -> server.tickRateManager().setFrozen(frozen));
    }
}
