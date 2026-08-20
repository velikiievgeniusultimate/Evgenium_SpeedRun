package org.evgenium.speedrun.client.match;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.evgenium.speedrun.client.lobby.LobbyRunConfig;
import org.evgenium.speedrun.client.runtime.ClientPhase;
import org.evgenium.speedrun.client.runtime.ClientRuntime;
import org.evgenium.speedrun.client.ui.LobbyRoomScreen;

import java.util.concurrent.atomic.AtomicReference;

public final class SpeedrunWorldLauncher {
    private static final AtomicReference<RunLaunchRequest> PENDING = new AtomicReference<>();

    private SpeedrunWorldLauncher() {
    }

    public static void launch(LobbyRunConfig config) {
        Minecraft minecraft = Minecraft.getInstance();
        RunLaunchRequest request = new RunLaunchRequest(config.worldSeed(), config.cheatsEnabled());
        if (!PENDING.compareAndSet(null, request)) {
            return;
        }

        RaceSession.arm(config);
        ClientRuntime.transitionTo(ClientPhase.PREPARING_WORLD);
        CreateWorldScreen.openFresh(minecraft, () -> {
            PENDING.compareAndSet(request, null);
            if (minecraft.level == null) {
                ClientRuntime.transitionTo(ClientPhase.LOBBY);
                minecraft.gui.setScreen(new LobbyRoomScreen());
            }
        });
    }

    public static RunLaunchRequest consumePending() {
        return PENDING.getAndSet(null);
    }

    public record RunLaunchRequest(long seed, boolean cheatsEnabled) {
    }
}
