package org.evgenium.speedrun.client.lobby;

public record LobbyRaceResult(String winnerName, long elapsedMillis) {
    public LobbyRaceResult {
        if (winnerName == null || winnerName.isBlank()) {
            throw new IllegalArgumentException("winnerName");
        }
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("elapsedMillis");
        }
    }
}
