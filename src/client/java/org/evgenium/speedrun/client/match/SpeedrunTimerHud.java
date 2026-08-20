package org.evgenium.speedrun.client.match;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.evgenium.speedrun.EvgeniumSpeedRun;

import java.util.Locale;

public final class SpeedrunTimerHud {
    private SpeedrunTimerHud() {
    }

    public static void install() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(EvgeniumSpeedRun.MOD_ID, "speedrun_timer"),
            (graphics, deltaTracker) -> {
                if (!RaceSession.isRunning() || Minecraft.getInstance().level == null) {
                    return;
                }

                String text = formatElapsed(RaceSession.elapsedNanos());
                int textWidth = Minecraft.getInstance().font.width(text);
                graphics.fill(4, 4, 12 + textWidth, 19, 0x90000000);
                graphics.text(Minecraft.getInstance().font, text, 8, 7, 0xFFFFFFFF, true);
            }
        );
    }

    private static String formatElapsed(long elapsedNanos) {
        long totalMillis = elapsedNanos / 1_000_000L;
        long millis = totalMillis % 1000L;
        long totalSeconds = totalMillis / 1000L;
        long seconds = totalSeconds % 60L;
        long totalMinutes = totalSeconds / 60L;
        long minutes = totalMinutes % 60L;
        long hours = totalMinutes / 60L;

        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        }
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }
}
