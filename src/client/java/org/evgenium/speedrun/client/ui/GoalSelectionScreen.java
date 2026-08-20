package org.evgenium.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.lobby.LobbyService;
import org.evgenium.speedrun.client.lobby.SpeedrunGoal;

public final class GoalSelectionScreen extends Screen {
    private String error = "";

    public GoalSelectionScreen() {
        super(Component.literal("Выбор цели спидрана"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.max(86, this.height / 2 - 42);

        for (SpeedrunGoal goal : SpeedrunGoal.values()) {
            this.addRenderableWidget(Button.builder(Component.literal(goal.displayName()), button -> select(goal))
                .bounds(centerX - 110, y, 220, 20)
                .build());
            y += 26;
        }

        this.addRenderableWidget(Button.builder(Component.literal("НАЗАД"), button ->
                Minecraft.getInstance().gui.setScreen(new RaceSettingsScreen()))
            .bounds(centerX - 110, y + 10, 220, 20)
            .build());
    }

    private void select(SpeedrunGoal goal) {
        String failure = LobbyService.get().selectGoal(goal);
        if (failure != null) {
            this.error = failure;
            return;
        }
        Minecraft.getInstance().gui.setScreen(new RaceSettingsScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "ЦЕЛЬ ПРОХОЖДЕНИЯ";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 38, 0xFFFFFFFF, true);

        String hint = "Сейчас доступна одна цель. Позже здесь появятся другие режимы.";
        graphics.text(this.font, hint, (this.width - this.font.width(hint)) / 2, 56, 0xFFAAAAAA, false);

        if (!this.error.isEmpty()) {
            graphics.text(this.font, this.error, (this.width - this.font.width(this.error)) / 2, this.height - 48, 0xFFFF7777, false);
        }
    }
}
