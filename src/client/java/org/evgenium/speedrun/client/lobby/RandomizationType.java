package org.evgenium.speedrun.client.lobby;

import java.util.Locale;

public enum RandomizationType {
    MCSR_LIKE("mcsr_like", "MCSR Like"),
    VANILLA("vanilla", "Ванильный");

    private final String id;
    private final String displayName;

    RandomizationType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public RandomizationType next() {
        return this == MCSR_LIKE ? VANILLA : MCSR_LIKE;
    }

    public static RandomizationType fromId(String id) {
        if (id != null) {
            for (RandomizationType value : values()) {
                if (value.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return value;
                }
            }
        }
        throw new IllegalArgumentException("Неизвестный тип рандомизации: " + id);
    }
}
