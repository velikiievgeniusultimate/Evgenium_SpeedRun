package org.evgenium.speedrun.client.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;

/** Routes the very first vanilla title screen into the Evgenium SpeedRun menu. */
public final class MenuRouter {
    private static boolean initialRedirectDone;

    private MenuRouter() {
    }

    public static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!initialRedirectDone && client.screen instanceof TitleScreen) {
                initialRedirectDone = true;
                client.setScreen(new EvgeniumMainScreen());
            }
        });
    }
}
