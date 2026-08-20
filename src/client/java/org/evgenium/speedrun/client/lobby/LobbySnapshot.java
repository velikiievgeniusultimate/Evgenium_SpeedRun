package org.evgenium.speedrun.client.lobby;

import java.util.List;

public record LobbySnapshot(List<LobbyPlayer> players, boolean cheatsEnabled) {
    public LobbySnapshot {
        players = List.copyOf(players);
    }

    public static LobbySnapshot empty() {
        return new LobbySnapshot(List.of(), false);
    }
}
