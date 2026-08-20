package org.evgenium.speedrun.spectator;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class SpectatorServerHooks {
    private SpectatorServerHooks() {
    }

    public static void install() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (!player.isSpectator()) {
                return;
            }
            player.getInventory().clearContent();
        });
    }
}
