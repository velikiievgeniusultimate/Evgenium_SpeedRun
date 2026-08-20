package org.evgenium.speedrun.client.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import org.evgenium.speedrun.client.mcsr.AppleRngHooks;
import org.evgenium.speedrun.client.mcsr.FlintRngHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(BonusLevelTableCondition.class)
public abstract class BonusLevelTableConditionMixin {
    @Shadow @Final private Holder<Enchantment> enchantment;
    @Shadow @Final private List<Float> values;

    /**
     * Replaces only explicitly-scoped vanilla block-drop rolls. Each hook returns empty when the
     * current loot context is not its exact target or when MCSR Like is disabled, allowing vanilla
     * BonusLevelTableCondition.test to continue untouched.
     */
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void evgenium$competitiveBlockDropRolls(LootContext context, CallbackInfoReturnable<Boolean> cir) {
        Optional<Boolean> competitive = FlintRngHooks.tryRoll(context, this.enchantment, this.values);
        if (competitive.isEmpty()) {
            competitive = AppleRngHooks.tryRoll(context, this.enchantment, this.values);
        }
        competitive.ifPresent(cir::setReturnValue);
    }
}
