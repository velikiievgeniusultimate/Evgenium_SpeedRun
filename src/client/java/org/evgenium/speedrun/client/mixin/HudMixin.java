package org.evgenium.speedrun.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.waypoints.ClientWaypointManager;
import org.evgenium.speedrun.client.match.RaceSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Redirect(
        method = "nextContextualInfoState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/waypoints/ClientWaypointManager;hasWaypoints()Z"
        )
    )
    private boolean evgenium$hideLocatorBarForFinishedSpectator(ClientWaypointManager waypointManager) {
        Minecraft minecraft = Minecraft.getInstance();
        if (RaceSession.isLocalFinished()
            && minecraft.player != null
            && minecraft.player.isSpectator()) {
            return false;
        }
        return waypointManager.hasWaypoints();
    }
}
