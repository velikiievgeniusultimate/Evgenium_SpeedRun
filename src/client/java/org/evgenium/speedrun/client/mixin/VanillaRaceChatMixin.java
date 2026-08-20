package org.evgenium.speedrun.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.evgenium.speedrun.client.match.RaceSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class VanillaRaceChatMixin {
    @Inject(
        method = "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void evgenium$filterManagedRaceMessages(Component message, CallbackInfo ci) {
        if (!RaceSession.isRunning() && !RaceSession.isLocalFinished()) {
            return;
        }
        if (!(message.getContents() instanceof TranslatableContents translatable)) {
            return;
        }

        String key = translatable.getKey();
        if (key.startsWith("chat.type.advancement.")
            || key.equals("multiplayer.player.joined")
            || key.equals("multiplayer.player.joined.renamed")
            || key.equals("multiplayer.player.left")) {
            ci.cancel();
        }
    }
}
