package org.evgenium.speedrun.client.match;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.evgenium.speedrun.EvgeniumSpeedRun;

public final class RaceNotificationHud {
    private static volatile String title = "";
    private static volatile String subtitle = "";
    private static volatile long visibleUntilMillis;

    private RaceNotificationHud() {
    }

    public static void install() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(EvgeniumSpeedRun.MOD_ID, "race_notification"),
            (graphics, deltaTracker) -> {
                if (System.currentTimeMillis() >= visibleUntilMillis || title.isEmpty()) {
                    return;
                }
                Minecraft minecraft = Minecraft.getInstance();
                int width = Math.max(minecraft.font.width(title), minecraft.font.width(subtitle)) + 16;
                int x = minecraft.getWindow().getGuiScaledWidth() - width - 8;
                int y = 8;
                graphics.fill(x, y, x + width, y + 38, 0xB0000000);
                graphics.text(minecraft.font, title, x + 8, y + 7, 0xFFFFFFFF, true);
                if (!subtitle.isEmpty()) {
                    graphics.text(minecraft.font, subtitle, x + 8, y + 22, 0xFFBBBBBB, false);
                }
            }
        );
    }

    public static void show(String newTitle, String newSubtitle) {
        title = newTitle == null ? "" : newTitle;
        subtitle = newSubtitle == null ? "" : newSubtitle;
        visibleUntilMillis = System.currentTimeMillis() + 6500L;
    }
}
