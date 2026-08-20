package org.evgenium.speedrun.client.lobby;

import java.util.Arrays;

public enum SpeedrunGoal {
    COMPLETE_MINECRAFT("complete_minecraft", "Пройти Minecraft");

    private final String id;
    private final String displayName;

    SpeedrunGoal(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static SpeedrunGoal fromId(String id) {
        return Arrays.stream(values())
            .filter(goal -> goal.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Неизвестная цель спидрана: " + id));
    }
}
