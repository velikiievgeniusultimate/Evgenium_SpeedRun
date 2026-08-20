package org.evgenium.speedrun.client.mixin;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.evgenium.speedrun.client.mcsr.DeadBushRngHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(UniformGenerator.class)
public abstract class UniformGeneratorMixin {
    @Shadow @Final private NumberProvider min;
    @Shadow @Final private NumberProvider max;

    @Inject(method = "getInt", at = @At("HEAD"), cancellable = true)
    private void evgenium$competitiveDeadBushStickCount(LootContext context, CallbackInfoReturnable<Integer> cir) {
        OptionalInt competitive = DeadBushRngHooks.tryCount(context, this.min, this.max);
        if (competitive.isPresent()) {
            cir.setReturnValue(competitive.getAsInt());
        }
    }
}
