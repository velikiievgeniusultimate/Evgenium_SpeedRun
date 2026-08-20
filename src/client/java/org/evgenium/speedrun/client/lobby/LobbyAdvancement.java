package org.evgenium.speedrun.client.lobby;

public record LobbyAdvancement(String playerName, String titleKey, String fallbackTitle) {
    public LobbyAdvancement {
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("playerName is required");
        }
        titleKey = titleKey == null ? "" : titleKey;
        fallbackTitle = fallbackTitle == null ? "" : fallbackTitle;
        if (titleKey.isBlank() && fallbackTitle.isBlank()) {
            throw new IllegalArgumentException("advancement title is required");
        }
    }
}
