package org.evgenium.speedrun.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.match.RaceSession;
import org.evgenium.speedrun.client.match.SpeedrunTimerHud;
import org.evgenium.speedrun.client.ui.MenuRouter;

public final class EvgeniumSpeedRunClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        org.evgenium.speedrun.client.runtime.ClientRuntime.initialize();
        MenuRouter.install();
        SpeedrunTimerHud.install();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> RaceSession.onWorldJoined(client));
        ClientTickEvents.END_CLIENT_TICK.register(RaceSession::tick);

        EvgeniumSpeedRun.LOGGER.info("Evgenium SpeedRun client initialized");
    }
}
