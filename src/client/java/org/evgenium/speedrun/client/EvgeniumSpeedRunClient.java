package org.evgenium.speedrun.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.WinScreen;
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
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof WinScreen) {
                RaceSession.onWinScreenOpened();
            }
        });

        EvgeniumSpeedRun.LOGGER.info("Evgenium SpeedRun client initialized");
    }
}
