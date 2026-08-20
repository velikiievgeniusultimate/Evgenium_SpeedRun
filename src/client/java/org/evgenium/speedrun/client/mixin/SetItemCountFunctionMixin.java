package org.evgenium.speedrun.client.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.evgenium.speedrun.client.mcsr.EntityDropRngHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SetItemCountFunction.class)
public abstract class SetItemCountFunctionMixin {
    @Shadow @Final private NumberProvider count;
    @Shadow @Final private boolean add;

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void evgenium$competitiveEntityBaseCount(
        ItemStack stack,
        LootContext context,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (EntityDropRngHooks.trySetCount(stack, context, this.count, this.add)) {
            cir.setReturnValue(stack);
        }
    }
}
