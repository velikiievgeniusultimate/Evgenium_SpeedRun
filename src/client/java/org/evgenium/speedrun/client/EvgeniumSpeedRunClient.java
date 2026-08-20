package org.evgenium.speedrun.client;

import net.fabricmc.api.ClientModInitializer;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.runtime.ClientRuntime;
import org.evgenium.speedrun.client.ui.MenuRouter;

public final class EvgeniumSpeedRunClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientRuntime.initialize();
        MenuRouter.install();
        EvgeniumSpeedRun.LOGGER.info("Evgenium SpeedRun client initialized");
    }
}
