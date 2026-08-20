package org.evgenium.speedrun.client.lobby;

import java.util.List;

public record LobbySnapshot(
    List<LobbyPlayer> players,
    boolean cheatsEnabled,
    SpeedrunGoal goal,
    RandomizationType randomizationType
) {
    public LobbySnapshot {
        players = List.copyOf(players);
        if (goal == null) {
            goal = SpeedrunGoal.COMPLETE_MINECRAFT;
        }
        if (randomizationType == null) {
            randomizationType = RandomizationType.MCSR_LIKE;
        }
    }

    /**
     * Compatibility constructor for the host snapshot builder. The host-side selected
     * randomization is owned by LobbyService and injected here until LobbyHost itself
     * needs more randomization-specific state.
     */
    public LobbySnapshot(List<LobbyPlayer> players, boolean cheatsEnabled, SpeedrunGoal goal) {
        this(players, cheatsEnabled, goal, LobbyService.get().configuredRandomizationType());
    }

    public static LobbySnapshot empty() {
        return new LobbySnapshot(
            List.of(),
            false,
            SpeedrunGoal.COMPLETE_MINECRAFT,
            RandomizationType.MCSR_LIKE
        );
    }
}
