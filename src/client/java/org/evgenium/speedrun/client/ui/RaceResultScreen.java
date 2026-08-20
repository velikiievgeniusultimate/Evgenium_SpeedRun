package org.evgenium.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.lobby.LobbyRaceResult;

import java.util.Locale;

public final class RaceResultScreen extends Screen {
    private final LobbyRaceResult result;

    public RaceResultScreen(LobbyRaceResult result) {
        super(Component.literal("Результат забега"));
        this.result = result;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("ВЕРНУТЬСЯ В МИР"), button -> Minecraft.getInstance().gui.setScreen(null))
            .bounds(centerX - 100, this.height / 2 + 48, 200, 20)
            .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        boolean localWinner = result.winnerName().equals(Minecraft.getInstance().getUser().getName());
        String heading = localWinner ? "ПОБЕДА!" : "ЗАБЕГ ЗАВЕРШЁН";
        graphics.text(this.font, heading, (this.width - this.font.width(heading)) / 2, this.height / 2 - 52, localWinner ? 0xFFFFDD55 : 0xFFFFFFFF, true);

        String winner = "Победитель: " + result.winnerName();
        graphics.text(this.font, winner, (this.width - this.font.width(winner)) / 2, this.height / 2 - 22, 0xFFFFFFFF, true);

        String time = "Время: " + formatMillis(result.elapsedMillis());
        graphics.text(this.font, time, (this.width - this.font.width(time)) / 2, this.height / 2 - 4, 0xFFDDDDDD, false);
    }

    private static String formatMillis(long totalMillis) {
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
