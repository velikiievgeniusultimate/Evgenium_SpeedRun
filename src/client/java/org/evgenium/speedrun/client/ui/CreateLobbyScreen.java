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
    private boolean cheatsEnabled;
    private String error = "";

    public CreateLobbyScreen(Screen parent) {
        super(Component.literal("Создать лобби"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.max(86, this.height / 2 - 58);

        this.portBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Порт"));
        this.portBox.setMaxLength(5);
        this.portBox.setValue("25565");
        this.addRenderableWidget(this.portBox);

        this.addRenderableWidget(Button.builder(cheatsLabel(), button -> {
                this.cheatsEnabled = !this.cheatsEnabled;
                button.setMessage(cheatsLabel());
            })
            .bounds(centerX - 100, y + 30, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("СОЗДАТЬ"), button -> createLobby())
            .bounds(centerX - 100, y + 60, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("НАЗАД"), button -> Minecraft.getInstance().gui.setScreen(parent))
            .bounds(centerX - 100, y + 86, 200, 20).build());
    }

    private Component cheatsLabel() {
        return Component.literal("ЧИТЫ (ОТЛАДКА): " + (this.cheatsEnabled ? "ВКЛ" : "ВЫКЛ"));
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
        String failure = LobbyService.get().host(port, playerName, this.cheatsEnabled);
        if (failure != null) {
            this.error = failure;
            return;
        }
        Minecraft.getInstance().gui.setScreen(new LobbyRoomScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "СОЗДАТЬ ЛОББИ";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 38, 0xFFFFFFFF, true);
        String label = "Порт, который будет слушать этот компьютер";
        graphics.text(this.font, label, (this.width - this.font.width(label)) / 2, Math.max(64, this.height / 2 - 82), 0xFFAAAAAA, false);
        String debug = "Читы — временная настройка только для отладки";
        graphics.text(this.font, debug, (this.width - this.font.width(debug)) / 2, this.height / 2 + 45, 0xFFFFCC66, false);
        if (!this.error.isEmpty()) {
            graphics.text(this.font, this.error, (this.width - this.font.width(this.error)) / 2, this.height / 2 + 62, 0xFFFF7777, false);
        }
    }
}
