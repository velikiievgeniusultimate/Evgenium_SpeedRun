package org.evgenium.speedrun.client.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import org.evgenium.speedrun.client.mcsr.CompetitiveRng;
import org.evgenium.speedrun.client.mcsr.McsrRules;
import org.evgenium.speedrun.mcsr.RngStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.OptionalInt;

@Mixin(EyeOfEnder.class)
public abstract class EyeOfEnderMixin {
    @Redirect(
        method = "signalTo",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"
        )
    )
    private int evgenium$competitiveEyeBreakRoll(RandomSource random, int bound) {
        if (!McsrRules.active() || bound != 5) {
            return random.nextInt(bound);
        }

        long throwIndex = CompetitiveRng.counter(RngStream.EYE_BREAK);
        OptionalInt roll = CompetitiveRng.nextInt(RngStream.EYE_BREAK, 0, 5);
        if (roll.isEmpty()) {
            return random.nextInt(bound);
        }

        // Ranked rule: the second actual throw never breaks. We still consume EYE_BREAK #1 so
        // throw numbering remains stable for all later eyes.
        if (throwIndex == 1L) {
            return 1;
        }
        return roll.getAsInt();
    }
}
