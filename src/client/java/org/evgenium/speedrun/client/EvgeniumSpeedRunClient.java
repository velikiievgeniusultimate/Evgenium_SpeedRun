package org.evgenium.speedrun.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.runtime.ClientPhase;
import org.evgenium.speedrun.client.runtime.ClientRuntime;
import org.evgenium.speedrun.client.ui.MenuRouter;

public final class EvgeniumSpeedRunClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientRuntime.initialize();
        MenuRouter.install();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientRuntime.phase() == ClientPhase.PREPARING_WORLD) {
                ClientRuntime.transitionTo(ClientPhase.RUNNING);
            }
        });
        EvgeniumSpeedRun.LOGGER.info("Evgenium SpeedRun client initialized");
    }
}
