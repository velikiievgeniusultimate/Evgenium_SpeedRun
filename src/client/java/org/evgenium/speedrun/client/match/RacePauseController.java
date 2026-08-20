package org.evgenium.speedrun.client.match;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.server.IntegratedServer;
import org.evgenium.speedrun.EvgeniumSpeedRun;

/**
 * Replaces vanilla singleplayer pause semantics for active races.
 *
 * Runner worlds are intentionally published for spectator connections, which means vanilla
 * IntegratedServer pause no longer applies when ESC is opened. Instead we freeze gameplay
 * ticks with ServerTickRateManager. Vanilla tick freeze keeps players responsive, so remote
 * spectators can continue flying while mobs, redstone, projectiles, portals, random ticks,
 * game time, etc. remain frozen.
 *
 * The speedrun timer is client monotonic time and deliberately keeps running while frozen.
 */
public final class RacePauseController {
    private static volatile IntegratedServer frozenServer;

    private RacePauseController() {
    }

    public static void tick(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        boolean shouldFreeze = RaceSession.isRunning()
            && !RaceSession.isLocalFinished()
            && server != null
            && minecraft.gui.screen() instanceof PauseScreen;

        IntegratedServer previous = frozenServer;
        if (shouldFreeze) {
            if (previous == server) {
                return;
            }
            if (previous != null) {
                setFrozen(previous, false);
            }
            frozenServer = server;
            setFrozen(server, true);
            EvgeniumSpeedRun.LOGGER.info("Runner opened ESC menu; gameplay simulation frozen while network remains active");
            return;
        }

        if (previous != null) {
            frozenServer = null;
            setFrozen(previous, false);
            EvgeniumSpeedRun.LOGGER.info("Runner closed ESC menu; gameplay simulation resumed");
        }
    }

    private static void setFrozen(IntegratedServer server, boolean frozen) {
        server.execute(() -> server.tickRateManager().setFrozen(frozen));
    }
}
