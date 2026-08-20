package org.evgenium.speedrun.client.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import org.evgenium.speedrun.client.mcsr.CompetitiveRng;
import org.evgenium.speedrun.client.mcsr.McsrRules;
import org.evgenium.speedrun.mcsr.RngStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
    @Redirect(
        method = "onHit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextFloat()F"
        )
    )
    private float evgenium$competitiveEndermiteRoll(RandomSource random) {
        if (!McsrRules.active()) {
            return random.nextFloat();
        }

        Level level = ((ThrownEnderpearl)(Object)this).level();
        RngStream stream;
        if (level.dimension() == Level.OVERWORLD) {
            stream = RngStream.ENDERMITE_OVERWORLD;
        } else if (level.dimension() == Level.NETHER) {
            stream = RngStream.ENDERMITE_NETHER;
        } else if (level.dimension() == Level.END) {
            stream = RngStream.ENDERMITE_END;
        } else {
            return random.nextFloat();
        }

        Optional<Float> roll = CompetitiveRng.nextFloat(stream);
        return roll.orElseGet(random::nextFloat);
    }
}
