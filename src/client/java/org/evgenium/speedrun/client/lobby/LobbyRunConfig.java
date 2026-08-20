package org.evgenium.speedrun.client.lobby;

public record LobbyRunConfig(long seed, boolean cheatsEnabled, SpeedrunGoal goal) {
    public LobbyRunConfig {
        if (goal == null) {
            goal = SpeedrunGoal.COMPLETE_MINECRAFT;
        }
    }
}
