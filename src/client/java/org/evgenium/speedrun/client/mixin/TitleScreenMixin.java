package org.evgenium.speedrun.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.ui.EvgeniumMainScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void evgenium$addSpeedrunLobbyButton(CallbackInfo info) {
        this.addRenderableWidget(Button.builder(Component.literal("Выбрать лобби спидранов"), button ->
                Minecraft.getInstance().gui.setScreen(new EvgeniumMainScreen()))
            .bounds(4, 4, 210, 20)
            .build());
    }
}
