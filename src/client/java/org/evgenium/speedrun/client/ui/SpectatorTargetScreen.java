package org.evgenium.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.lobby.LobbyService;
import org.evgenium.speedrun.client.spectator.SpectatorRelayClient;

import java.util.List;

public final class SpectatorTargetScreen extends Screen {
    public SpectatorTargetScreen() {
        super(Component.literal("Наблюдение за игроками"));
    }

    @Override
    protected void init() {
        List<String> targets = LobbyService.get().runningPlayerNames().stream()
            .filter(name -> !name.equals(LobbyService.get().localPlayerName()))
            .toList();

        int centerX = this.width / 2;
        int y = 64;
        for (String target : targets) {
            String prefix = target.equals(SpectatorRelayClient.currentTarget()) ? "● " : "▶ ";
            this.addRenderableWidget(Button.builder(Component.literal(prefix + target), button -> SpectatorRelayClient.watch(target))
                .bounds(centerX - 110, y, 220, 20)
                .build());
            y += 25;
        }

        this.addRenderableWidget(Button.builder(Component.literal("ЗАКРЫТЬ"), button -> Minecraft.getInstance().gui.setScreen(null))
            .bounds(centerX - 110, Math.min(this.height - 34, y + 10), 220, 20)
            .build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "ВЫБЕРИ ИГРОКА ДЛЯ НАБЛЮДЕНИЯ";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 26, 0xFFFFFFFF, true);

        List<String> targets = LobbyService.get().runningPlayerNames().stream()
            .filter(name -> !name.equals(LobbyService.get().localPlayerName()))
            .toList();
        if (targets.isEmpty()) {
            String empty = "Все остальные игроки уже финишировали";
            graphics.text(this.font, empty, (this.width - this.font.width(empty)) / 2, 48, 0xFFAAAAAA, false);
        } else {
            String hint = "Выбор другого мира пройдёт через relay хозяина лобби";
            graphics.text(this.font, hint, (this.width - this.font.width(hint)) / 2, 46, 0xFFAAAAAA, false);
        }
    }
}
