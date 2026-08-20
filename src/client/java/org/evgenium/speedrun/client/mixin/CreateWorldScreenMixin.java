package org.evgenium.speedrun.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import org.evgenium.speedrun.client.match.SpeedrunWorldLauncher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    protected CreateWorldScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    public abstract WorldCreationUiState getUiState();

    @Shadow
    private void onCreate() {
        throw new AssertionError();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void evgenium$launchPreparedSpeedrunWorld(CallbackInfo info) {
        SpeedrunWorldLauncher.RunLaunchRequest request = SpeedrunWorldLauncher.consumePending();
        if (request == null) {
            return;
        }

        WorldCreationUiState uiState = this.getUiState();
        uiState.setName("Evgenium SpeedRun " + request.seed());
        uiState.setSeed(Long.toString(request.seed()));
        uiState.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
        uiState.setDifficulty(Difficulty.EASY);
        uiState.setAllowCommands(request.cheatsEnabled());
        uiState.setGenerateStructures(true);
        uiState.setBonusChest(false);

        this.minecraft.execute(this::onCreate);
    }
}
