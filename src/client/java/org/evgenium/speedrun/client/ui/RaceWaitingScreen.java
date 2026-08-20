package org.evgenium.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.match.RaceClockSync;
import org.evgenium.speedrun.client.match.RaceSession;

public final class RaceWaitingScreen extends Screen {
    public RaceWaitingScreen() {
        super(Component.literal("Speedrun start"));
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (!RaceSession.isWaitingForGo()) {
            super.onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, this.width, this.height, 0xB8000000);

        String title = RaceSession.isReadyReported() ? "МИР ГОТОВ" : "ПОДГОТОВКА МИРА";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, this.height / 2 - 56, 0xFFFFFFFF, true);

        String sync = RaceClockSync.statusText();
        int syncColor = RaceClockSync.isSafe() ? 0xFF77FF99 : 0xFFFF7777;
        graphics.text(this.font, sync, (this.width - this.font.width(sync)) / 2, this.height / 2 - 36, syncColor, false);

        if (!RaceSession.isGoScheduled()) {
            String waiting;
            if (!RaceClockSync.isSafe()) {
                waiting = "Связь/время не синхронизированы. Автопереподключение...";
            } else {
                waiting = RaceSession.isReadyReported() ? "Ждём остальных игроков..." : "Выравниваем точку спавна...";
            }
            graphics.text(this.font, waiting, (this.width - this.font.width(waiting)) / 2, this.height / 2 - 12, 0xFFBBBBBB, false);
            return;
        }

        long remaining = RaceSession.countdownMillis();
        long seconds = (remaining + 999L) / 1000L;
        String countdown = seconds > 0L ? Long.toString(seconds) : (RaceClockSync.isSafe() ? "GO!" : "SYNC");
        int scaleWidth = this.font.width(countdown);
        graphics.text(this.font, countdown, (this.width - scaleWidth) / 2, this.height / 2 - 8, 0xFFFFFF55, true);

        String subtitle = RaceClockSync.isSafe()
            ? "Старт по общей шкале времени хоста"
            : "GO получен, но управление заблокировано до восстановления синхронизации";
        graphics.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2, this.height / 2 + 18, 0xFFBBBBBB, false);
    }
}
