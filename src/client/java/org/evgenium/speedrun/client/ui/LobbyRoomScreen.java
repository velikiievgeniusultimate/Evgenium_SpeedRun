package org.evgenium.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.lobby.LobbyPlayer;
import org.evgenium.speedrun.client.lobby.LobbyService;
import org.evgenium.speedrun.client.lobby.LobbySnapshot;

public final class LobbyRoomScreen extends Screen {
    public LobbyRoomScreen() {
        super(Component.literal("SpeedRun Lobby"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        LobbyService service = LobbyService.get();

        Button start = Button.builder(Component.literal(service.isHosting() ? "НАЧАТЬ ЗАБЕГ" : "ЖДЁМ ХОЗЯИНА"), button -> {
                button.active = false;
                String failure = LobbyService.get().startRun();
                if (failure != null) {
                    button.active = true;
                }
            })
            .bounds(centerX - 110, this.height - 58, 220, 20).build();
        start.active = service.isHosting();
        this.addRenderableWidget(start);

        this.addRenderableWidget(Button.builder(Component.literal("ВЫЙТИ ИЗ ЛОББИ"), button -> leaveLobby())
            .bounds(centerX - 110, this.height - 32, 220, 20).build());
    }

    private void leaveLobby() {
        LobbyService.get().leave();
        Minecraft.getInstance().gui.setScreen(new EvgeniumMainScreen());
    }

    @Override
    public void onClose() {
        leaveLobby();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        LobbyService service = LobbyService.get();
        LobbySnapshot snapshot = service.snapshot();

        String title = service.isHosting() ? "ЛОББИ — ХОЗЯИН" : "ЛОББИ";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 26, 0xFFFFFFFF, true);

        String endpoint = service.endpointText();
        graphics.text(this.font, endpoint, (this.width - this.font.width(endpoint)) / 2, 44, 0xFFBBBBBB, false);

        String rules = "Режим: Survival • Сложность: Easy • Читы: " + (snapshot.cheatsEnabled() ? "ВКЛ (ОТЛАДКА)" : "ВЫКЛ");
        graphics.text(this.font, rules, (this.width - this.font.width(rules)) / 2, 60, snapshot.cheatsEnabled() ? 0xFFFFCC66 : 0xFFAAAAAA, false);

        String status = service.status();
        int statusColor = service.hasError() ? 0xFFFF7777 : 0xFFAAAAAA;
        graphics.text(this.font, status, (this.width - this.font.width(status)) / 2, 76, statusColor, false);

        int listX = Math.max(20, this.width / 2 - 150);
        int y = 102;
        graphics.text(this.font, "Игроки: " + snapshot.players().size(), listX, y, 0xFFFFFFFF, true);
        y += 18;
        for (LobbyPlayer player : snapshot.players()) {
            String line = (player.host() ? "★ " : "• ") + player.name();
            graphics.text(this.font, line, listX, y, player.host() ? 0xFFFFDD77 : 0xFFFFFFFF, false);
            y += 16;
        }
    }
}
