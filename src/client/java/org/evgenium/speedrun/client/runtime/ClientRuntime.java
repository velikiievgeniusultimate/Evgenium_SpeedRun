package org.evgenium.speedrun.client.runtime;

import org.evgenium.speedrun.EvgeniumSpeedRun;

import java.util.Objects;

public final class ClientRuntime {
    private static ClientPhase phase = ClientPhase.BOOT;

    private ClientRuntime() {
    }

    public static void initialize() {
        transitionTo(ClientPhase.MENU);
    }

    public static ClientPhase phase() {
        return phase;
    }

    public static void transitionTo(ClientPhase nextPhase) {
        Objects.requireNonNull(nextPhase, "nextPhase");
        ClientPhase previous = phase;
        phase = nextPhase;
        EvgeniumSpeedRun.LOGGER.info("Client phase: {} -> {}", previous, nextPhase);
    }
}
