package org.evgenium.speedrun.client.lobby;

import java.util.List;

public record LobbySnapshot(List<LobbyPlayer> players, boolean cheatsEnabled, SpeedrunGoal goal) {
    public LobbySnapshot {
        players = List.copyOf(players);
        if (goal == null) {
            goal = SpeedrunGoal.COMPLETE_MINECRAFT;
        }
    }

    public static LobbySnapshot empty() {
        return new LobbySnapshot(List.of(), false, SpeedrunGoal.COMPLETE_MINECRAFT);
    }
}
