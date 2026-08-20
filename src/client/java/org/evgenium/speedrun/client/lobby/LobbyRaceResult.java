package org.evgenium.speedrun.client.lobby;

public record LobbyRaceResult(String playerName, int place, long elapsedMillis, int totalPlayers) {
    public LobbyRaceResult {
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("playerName");
        }
        if (place < 1) {
            throw new IllegalArgumentException("place");
        }
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("elapsedMillis");
        }
        if (totalPlayers < place) {
            throw new IllegalArgumentException("totalPlayers");
        }
    }
}
