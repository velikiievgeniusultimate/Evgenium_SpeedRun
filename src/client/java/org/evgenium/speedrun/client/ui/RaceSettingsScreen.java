package org.evgenium.speedrun.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.lobby.LobbyService;
import org.evgenium.speedrun.client.lobby.LobbySnapshot;
import org.evgenium.speedrun.client.lobby.RandomizationType;

public final class RaceSettingsScreen extends Screen {
    private String error = "";

    public RaceSettingsScreen() {
        super(Component.literal("Настройки забега"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.max(88, this.height / 2 - 64);
        LobbyService service = LobbyService.get();
        LobbySnapshot snapshot = service.snapshot();
        boolean editable = service.isHosting();

        Button goal = Button.builder(goalLabel(snapshot), button ->
                Minecraft.getInstance().gui.setScreen(new GoalSelectionScreen()))
            .bounds(centerX - 130, y, 260, 20)
            .build();
        goal.active = editable;
        this.addRenderableWidget(goal);

        Button cheats = Button.builder(cheatsLabel(snapshot), button -> {
                boolean next = !LobbyService.get().snapshot().cheatsEnabled();
                String failure = LobbyService.get().setCheatsEnabled(next);
                if (failure != null) {
                    this.error = failure;
                    return;
                }
                this.error = "";
                button.setMessage(cheatsLabel(LobbyService.get().snapshot()));
            })
            .bounds(centerX - 130, y + 28, 260, 20)
            .build();
        cheats.active = editable;
        this.addRenderableWidget(cheats);

        Button randomization = Button.builder(randomizationLabel(snapshot), button -> {
                RandomizationType next = LobbyService.get().snapshot().randomizationType().next();
                String failure = LobbyService.get().setRandomizationType(next);
                if (failure != null) {
                    this.error = failure;
                    return;
                }
                this.error = "";
                button.setMessage(randomizationLabel(LobbyService.get().snapshot()));
            })
            .bounds(centerX - 130, y + 56, 260, 20)
            .build();
        randomization.active = editable;
        this.addRenderableWidget(randomization);

        Button globalEvent = Button.builder(Component.literal("ГЛОБАЛЬНЫЙ ИВЕНТ: СКОРО"), button -> {})
            .bounds(centerX - 130, y + 84, 260, 20)
            .build();
        globalEvent.active = false;
        this.addRenderableWidget(globalEvent);

        this.addRenderableWidget(Button.builder(Component.literal("НАЗАД"), button ->
                Minecraft.getInstance().gui.setScreen(new LobbyRoomScreen()))
            .bounds(centerX - 130, y + 122, 260, 20)
            .build());
    }

    private static Component goalLabel(LobbySnapshot snapshot) {
        return Component.literal("ЦЕЛЬ ПРОХОЖДЕНИЯ: " + snapshot.goal().displayName());
    }

    private static Component cheatsLabel(LobbySnapshot snapshot) {
        return Component.literal("ЧИТЫ: " + (snapshot.cheatsEnabled() ? "ВКЛ" : "ВЫКЛ"));
    }

    private static Component randomizationLabel(LobbySnapshot snapshot) {
        return Component.literal("ТИП РАНДОМИЗАЦИИ: " + snapshot.randomizationType().displayName());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String title = "НАСТРОЙКИ ЗАБЕГА";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 38, 0xFFFFFFFF, true);

        String hint = LobbyService.get().isHosting()
            ? "Настройки применятся ко всем игрокам этого лобби"
            : "Настройки меняет хозяин лобби";
        graphics.text(this.font, hint, (this.width - this.font.width(hint)) / 2, 56, 0xFFAAAAAA, false);

        if (!this.error.isEmpty()) {
            graphics.text(this.font, this.error, (this.width - this.font.width(this.error)) / 2, this.height - 38, 0xFFFF7777, false);
        }
    }
}
