package org.evgenium.speedrun.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * First skeleton of the future Evgenium SpeedRun title screen.
 *
 * It is deliberately not installed as the game's title screen yet. The first milestone
 * is a clean, reproducible Fabric 26.2 build; title-screen interception comes next.
 */
public final class EvgeniumMainScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    public EvgeniumMainScreen() {
        super(Component.literal("Evgenium SpeedRun"));
    }

    @Override
    protected void init() {
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = Math.max(90, this.height / 2 - 45);

        addComingSoonButton("БЫСТРАЯ ИГРА", x, y);
        addComingSoonButton("СОЗДАТЬ ЛОББИ", x, y + (BUTTON_HEIGHT + BUTTON_GAP));
        addComingSoonButton("НАЙТИ ЛОББИ", x, y + 2 * (BUTTON_HEIGHT + BUTTON_GAP));
        addComingSoonButton("ОДИНОЧНАЯ ТРЕНИРОВКА", x, y + 3 * (BUTTON_HEIGHT + BUTTON_GAP));
    }

    private void addComingSoonButton(String label, int x, int y) {
        Button button = Button.builder(Component.literal(label), ignored -> {
        }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        button.active = false;
        this.addRenderableWidget(button);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String title = "EVGENIUM SPEEDRUN";
        int titleX = (this.width - this.font.width(title)) / 2;
        graphics.text(this.font, title, titleX, 42, 0xFFFFFFFF, true);

        String status = "foundation 0.1.0-alpha.1";
        int statusX = (this.width - this.font.width(status)) / 2;
        graphics.text(this.font, status, statusX, 60, 0xFFAAAAAA, false);
    }
}
