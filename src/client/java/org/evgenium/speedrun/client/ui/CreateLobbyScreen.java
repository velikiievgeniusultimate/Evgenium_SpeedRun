package org.evgenium.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.lobby.LobbyService;

public final class CreateLobbyScreen extends Screen {
    private final Screen parent;
    private EditBox portBox;
    private String error = "";

    public CreateLobbyScreen(Screen parent) {
        super(Component.literal("Создать лобби"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.max(90, this.height / 2 - 45);

        this.portBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Порт"));
        this.portBox.setMaxLength(5);
        this.portBox.setValue("25565");
        this.addRenderableWidget(this.portBox);

        this.addRenderableWidget(Button.builder(Component.literal("СОЗДАТЬ"), button -> createLobby())
            .bounds(centerX - 100, y + 32, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("НАЗАД"), button -> Minecraft.getInstance().setScreen(parent))
            .bounds(centerX - 100, y + 58, 200, 20).build());
    }

    private void createLobby() {
        int port;
        try {
            port = Integer.parseInt(this.portBox.getValue().trim());
        } catch (NumberFormatException exception) {
            this.error = "Порт должен быть числом";
            return;
        }
        if (port < 1 || port > 65535) {
            this.error = "Допустимый порт: 1–65535";
            return;
        }

        String playerName = Minecraft.getInstance().getUser().getName();
        String failure = LobbyService.get().host(port, playerName);
        if (failure != null) {
            this.error = failure;
            return;
        }
        Minecraft.getInstance().setScreen(new LobbyRoomScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "СОЗДАТЬ ЛОББИ";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 45, 0xFFFFFFFF, true);
        String label = "Порт, который будет слушать этот компьютер";
        graphics.text(this.font, label, (this.width - this.font.width(label)) / 2, Math.max(70, this.height / 2 - 68), 0xFFAAAAAA, false);
        if (!this.error.isEmpty()) {
            graphics.text(this.font, this.error, (this.width - this.font.width(this.error)) / 2, this.height / 2 + 54, 0xFFFF7777, false);
        }
    }
}
