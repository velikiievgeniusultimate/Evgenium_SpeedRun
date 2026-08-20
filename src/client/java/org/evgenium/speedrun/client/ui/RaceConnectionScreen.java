package org.evgenium.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.lobby.LobbyService;
import org.evgenium.speedrun.client.match.RaceClockSync;

/** Blocks runner input while the lobby control/time-sync channel is unhealthy. */
public final class RaceConnectionScreen extends Screen {
    public RaceConnectionScreen() {
        super(Component.literal("Race connection lost"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("ПЕРЕПОДКЛЮЧИТЬСЯ СЕЙЧАС"), button ->
                LobbyService.get().forceReconnect())
            .bounds(centerX - 110, this.height / 2 + 42, 220, 20)
            .build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // Cannot be dismissed while the race connection is unsafe.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, this.width, this.height, 0xD0000000);

        String title = "СВЯЗЬ С ХОСТОМ НАРУШЕНА";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, this.height / 2 - 54, 0xFFFF7777, true);

        String line1 = "Мир и управление заморожены. Таймер продолжает идти.";
        graphics.text(this.font, line1, (this.width - this.font.width(line1)) / 2, this.height / 2 - 24, 0xFFFFFFFF, false);

        String line2 = RaceClockSync.statusText();
        graphics.text(this.font, line2, (this.width - this.font.width(line2)) / 2, this.height / 2 - 6, 0xFFFFFF88, false);

        String line3 = LobbyService.get().status();
        graphics.text(this.font, line3, (this.width - this.font.width(line3)) / 2, this.height / 2 + 12, 0xFFBBBBBB, false);
    }
}
