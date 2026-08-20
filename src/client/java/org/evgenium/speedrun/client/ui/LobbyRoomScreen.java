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

        this.addRenderableWidget(Button.builder(Component.literal("НАСТРОЙКИ ЗАБЕГА"), button ->
                Minecraft.getInstance().gui.setScreen(new RaceSettingsScreen()))
            .bounds(centerX - 110, 88, 220, 20)
            .build());

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
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 22, 0xFFFFFFFF, true);

        String endpoint = service.endpointText();
        graphics.text(this.font, endpoint, (this.width - this.font.width(endpoint)) / 2, 40, 0xFFBBBBBB, false);

        String baseRules = "Survival • Easy • Рандом: " + snapshot.randomizationType().displayName();
        graphics.text(this.font, baseRules, (this.width - this.font.width(baseRules)) / 2, 56, 0xFFAAAAAA, false);

        String selectedRules = "Цель: " + snapshot.goal().displayName()
            + " • Читы: " + (snapshot.cheatsEnabled() ? "ВКЛ" : "ВЫКЛ");
        graphics.text(
            this.font,
            selectedRules,
            (this.width - this.font.width(selectedRules)) / 2,
            72,
            snapshot.cheatsEnabled() ? 0xFFFFCC66 : 0xFFFFFFFF,
            false
        );

        String status = service.status();
        int statusColor = service.hasError() ? 0xFFFF7777 : 0xFFAAAAAA;
        graphics.text(this.font, status, (this.width - this.font.width(status)) / 2, 114, statusColor, false);

        int listX = Math.max(20, this.width / 2 - 150);
        int y = 138;
        graphics.text(this.font, "Игроки: " + snapshot.players().size(), listX, y, 0xFFFFFFFF, true);
        y += 18;
        for (LobbyPlayer player : snapshot.players()) {
            String line = (player.host() ? "★ " : "• ") + player.name();
            graphics.text(this.font, line, listX, y, player.host() ? 0xFFFFDD77 : 0xFFFFFFFF, false);
            y += 16;
        }
    }
}
