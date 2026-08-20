package org.evgenium.speedrun.client.spectator;

import org.evgenium.speedrun.client.match.RaceNotificationHud;

public final class RaceSpectatorNotifications {
    private RaceSpectatorNotifications() {
    }

    public static void error(String message) {
        RaceNotificationHud.show("Наблюдение недоступно", message);
    }
}
