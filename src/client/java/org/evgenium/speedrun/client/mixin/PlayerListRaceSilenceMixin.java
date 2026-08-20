package org.evgenium.speedrun.client.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.evgenium.speedrun.client.match.RaceSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class PlayerListRaceSilenceMixin {
    @Redirect(
        method = {"placeNewPlayer", "remove"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private void evgenium$hideRaceJoinLeaveMessages(PlayerList instance, Component message, boolean overlay) {
        if (RaceSession.isRunning()) {
            return;
        }
        instance.broadcastSystemMessage(message, overlay);
    }
}
