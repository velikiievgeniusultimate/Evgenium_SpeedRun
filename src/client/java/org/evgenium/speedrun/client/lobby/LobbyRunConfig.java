package org.evgenium.speedrun.client.lobby;

public record LobbyRunConfig(
    long seed,
    boolean cheatsEnabled,
    SpeedrunGoal goal,
    RandomizationType randomizationType
) {
    public LobbyRunConfig {
        if (goal == null) {
            goal = SpeedrunGoal.COMPLETE_MINECRAFT;
        }
        if (randomizationType == null) {
            randomizationType = RandomizationType.MCSR_LIKE;
        }
    }
}
