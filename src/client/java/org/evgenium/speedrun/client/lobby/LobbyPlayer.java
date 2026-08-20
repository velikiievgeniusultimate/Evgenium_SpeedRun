package org.evgenium.speedrun.client.lobby;

public record LobbyPlayer(String name, boolean host, boolean connected) {
    public LobbyPlayer(String name, boolean host) {
        this(name, host, true);
    }
}
