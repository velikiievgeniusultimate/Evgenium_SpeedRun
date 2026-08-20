package org.evgenium.speedrun.client.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import org.evgenium.speedrun.client.mcsr.CompetitiveRng;
import org.evgenium.speedrun.client.mcsr.McsrRules;
import org.evgenium.speedrun.mcsr.RngStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(ApplyStatusEffectsConsumeEffect.class)
public abstract class ApplyStatusEffectsConsumeEffectMixin {
    @Shadow public abstract List<MobEffectInstance> effects();
    @Shadow public abstract float probability();

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void evgenium$competitiveRottenFleshHunger(
        Level level,
        ItemStack stack,
        LivingEntity user,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!McsrRules.active() || stack == null || !stack.is(Items.ROTTEN_FLESH)) {
            return;
        }

        Optional<Boolean> apply = CompetitiveRng.chance(RngStream.ROTTEN_FLESH_HUNGER, this.probability());
        if (apply.isEmpty()) {
            return;
        }
        if (!apply.get()) {
            cir.setReturnValue(false);
            return;
        }

        boolean anyApplied = false;
        for (MobEffectInstance effect : this.effects()) {
            if (user.addEffect(new MobEffectInstance(effect))) {
                anyApplied = true;
            }
        }
        cir.setReturnValue(anyApplied);
    }
}
