package org.evgenium.speedrun.client.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
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
     * Gravel's vanilla loot table reaches this condition only after the Silk Touch alternative has
     * failed. In MCSR Like we keep the exact vanilla Fortune chance table but source the final roll
     * from the independent FLINT stream. In Vanilla mode this injection is a complete no-op.
     */
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void evgenium$competitiveGravelFlint(LootContext context, CallbackInfoReturnable<Boolean> cir) {
        Optional<Boolean> competitive = FlintRngHooks.tryRoll(context, this.enchantment, this.values);
        competitive.ifPresent(cir::setReturnValue);
    }
}
