package org.evgenium.speedrun.client.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.spectator.SpectatorRelayAuth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerMixin {
    @Shadow
    @Final
    private Connection connection;

    @Redirect(
        method = "handleHello",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"
        )
    )
    private boolean evgenium$skipSessionAuthForApprovedSpectatorRelay(MinecraftServer server) {
        SocketAddress remote = connection.getRemoteAddress();
        if (remote instanceof InetSocketAddress inet
            && inet.getAddress() != null
            && inet.getAddress().isLoopbackAddress()
            && SpectatorRelayAuth.consumeExpectedLocalLogin()) {
            EvgeniumSpeedRun.LOGGER.info("Accepted approved local spectator relay login without Mojang session authentication");
            return false;
        }

        return server.usesAuthentication();
    }
}
