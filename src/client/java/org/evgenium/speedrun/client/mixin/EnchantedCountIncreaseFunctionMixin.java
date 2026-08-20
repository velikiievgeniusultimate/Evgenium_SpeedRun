package org.evgenium.speedrun.client.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.evgenium.speedrun.client.mcsr.EntityDropRngHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(EnchantedCountIncreaseFunction.class)
public abstract class EnchantedCountIncreaseFunctionMixin {
    @Shadow @Final private Holder<Enchantment> enchantment;
    @Shadow @Final private NumberProvider count;
    @Shadow @Final private int limit;

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void evgenium$competitiveEntityLooting(
        ItemStack stack,
        LootContext context,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        Optional<ItemStack> competitive = EntityDropRngHooks.tryLooting(
            stack,
            context,
            this.enchantment,
            this.count,
            this.limit
        );
        competitive.ifPresent(cir::setReturnValue);
    }
}
