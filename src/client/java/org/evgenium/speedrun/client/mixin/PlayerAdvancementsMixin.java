package org.evgenium.speedrun.client.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.evgenium.speedrun.client.lobby.LobbyService;
import org.evgenium.speedrun.client.match.RaceSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Unique
    private boolean evgenium$wasDoneBeforeAward;

    @Inject(
        method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z",
        at = @At("HEAD")
    )
    private void evgenium$rememberPreviousState(
        AdvancementHolder advancement,
        String criterion,
        CallbackInfoReturnable<Boolean> cir
    ) {
        evgenium$wasDoneBeforeAward = getOrStartProgress(advancement).isDone();
    }

    @Inject(
        method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z",
        at = @At("RETURN")
    )
    private void evgenium$broadcastCompletedAdvancement(
        AdvancementHolder advancement,
        String criterion,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue() || evgenium$wasDoneBeforeAward || !getOrStartProgress(advancement).isDone()) {
            return;
        }
        if (!RaceSession.isRunning()) {
            return;
        }

        String localName = LobbyService.get().localPlayerName();
        if (!player.getGameProfile().name().equals(localName)) {
            // Remote spectators have their own PlayerAdvancements on this integrated server.
            // Their progress must never be reported as the runner's progress.
            return;
        }

        advancement.value().display().ifPresent(display -> {
            Component title = display.getTitle();
            String titleKey = title.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey()
                : "";
            LobbyService.get().reportAdvancement(titleKey, title.getString());
        });
    }
}
