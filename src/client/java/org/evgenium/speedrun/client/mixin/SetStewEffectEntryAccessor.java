package org.evgenium.speedrun.client.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.storage.loot.functions.SetStewEffectFunction$EffectEntry")
public interface SetStewEffectEntryAccessor {
    @Accessor("effect")
    Holder<MobEffect> evgenium$getEffect();

    @Accessor("duration")
    NumberProvider evgenium$getDuration();
}
