package org.evgenium.speedrun.client;

import net.fabricmc.api.ClientModInitializer;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.runtime.ClientRuntime;

public final class EvgeniumSpeedRunClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientRuntime.initialize();
        EvgeniumSpeedRun.LOGGER.info("Evgenium SpeedRun client foundation initialized");
    }
}
