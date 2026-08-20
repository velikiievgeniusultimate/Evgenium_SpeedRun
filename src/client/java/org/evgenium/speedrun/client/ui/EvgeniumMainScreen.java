package org.evgenium.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class EvgeniumMainScreen extends Screen {
    private static final int BUTTON_WIDTH = 240;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 7;

    public EvgeniumMainScreen() {
        super(Component.literal("Evgenium SpeedRun"));
    }

    @Override
    protected void init() {
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = Math.max(92, this.height / 2 - 48);

        this.addRenderableWidget(Button.builder(Component.literal("СОЗДАТЬ ЛОББИ"), button ->
                Minecraft.getInstance().gui.setScreen(new CreateLobbyScreen(this)))
            .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(Component.literal("ЗАЙТИ В ЛОББИ"), button ->
                Minecraft.getInstance().gui.setScreen(new JoinLobbyScreen(this)))
            .bounds(x, y + BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        Button training = Button.builder(Component.literal("ОДИНОЧНАЯ ТРЕНИРОВКА — СКОРО"), button -> {})
            .bounds(x, y + 2 * (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT).build();
        training.active = false;
        this.addRenderableWidget(training);

        this.addRenderableWidget(Button.builder(Component.literal("ВЕРНУТЬСЯ К ВАНИЛЬНОМУ МЕНЮ"), button ->
                Minecraft.getInstance().gui.setScreen(new TitleScreen()))
            .bounds(x, y + 3 * (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "EVGENIUM SPEEDRUN";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 42, 0xFFFFFFFF, true);
        String subtitle = "Direct-connect speedrun lobbies";
        graphics.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2, 60, 0xFFAAAAAA, false);
    }
}
