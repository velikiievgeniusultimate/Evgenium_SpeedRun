package org.evgenium.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.lobby.LobbyEndpoint;
import org.evgenium.speedrun.client.lobby.LobbyService;

public final class JoinLobbyScreen extends Screen {
    private final Screen parent;
    private EditBox addressBox;
    private String error = "";

    public JoinLobbyScreen(Screen parent) {
        super(Component.literal("Зайти в лобби"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.max(90, this.height / 2 - 45);

        this.addressBox = new EditBox(this.font, centerX - 120, y, 240, 20, Component.literal("IP:порт"));
        this.addressBox.setMaxLength(128);
        this.addressBox.setValue("127.0.0.1:25565");
        this.addRenderableWidget(this.addressBox);

        this.addRenderableWidget(Button.builder(Component.literal("ПОДКЛЮЧИТЬСЯ"), button -> joinLobby())
            .bounds(centerX - 120, y + 32, 240, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("НАЗАД"), button -> Minecraft.getInstance().gui.setScreen(parent))
            .bounds(centerX - 120, y + 58, 240, 20).build());
    }

    private void joinLobby() {
        LobbyEndpoint endpoint;
        try {
            endpoint = LobbyEndpoint.parse(this.addressBox.getValue());
        } catch (IllegalArgumentException exception) {
            this.error = exception.getMessage();
            return;
        }

        String playerName = Minecraft.getInstance().getUser().getName();
        LobbyService.get().join(endpoint.host(), endpoint.port(), playerName);
        Minecraft.getInstance().gui.setScreen(new LobbyRoomScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "ЗАЙТИ В ЛОББИ";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 45, 0xFFFFFFFF, true);
        String label = "Адрес хозяина в формате IP:порт";
        graphics.text(this.font, label, (this.width - this.font.width(label)) / 2, Math.max(70, this.height / 2 - 68), 0xFFAAAAAA, false);
        if (!this.error.isEmpty()) {
            graphics.text(this.font, this.error, (this.width - this.font.width(this.error)) / 2, this.height / 2 + 54, 0xFFFF7777, false);
        }
    }
}
