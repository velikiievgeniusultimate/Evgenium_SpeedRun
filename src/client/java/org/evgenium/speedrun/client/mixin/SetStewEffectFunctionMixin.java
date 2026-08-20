package org.evgenium.speedrun.client.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.SetStewEffectFunction;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.evgenium.speedrun.client.mcsr.CompetitiveRng;
import org.evgenium.speedrun.client.mcsr.McsrRules;
import org.evgenium.speedrun.mcsr.DeterministicRngCore;
import org.evgenium.speedrun.mcsr.RngStream;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(SetStewEffectFunction.class)
public abstract class SetStewEffectFunctionMixin {
    @Shadow @Final private List<?> effects;

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void evgenium$competitiveSuspiciousStew(
        ItemStack stack,
        LootContext context,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!McsrRules.active() || stack == null || !stack.is(Items.SUSPICIOUS_STEW) || this.effects.isEmpty()) {
            return;
        }

        List<SetStewEffectEntryAccessor> allowed = new ArrayList<>();
        for (Object rawEntry : this.effects) {
            SetStewEffectEntryAccessor entry = (SetStewEffectEntryAccessor) rawEntry;
            if (!entry.evgenium$getEffect().equals(MobEffects.POISON)) {
                allowed.add(entry);
            }
        }

        // Competitive rule: random suspicious stew may never choose Poison. If a datapack somehow
        // provides Poison as the only entry, produce a stew with no random effect rather than
        // violating the ruleset.
        if (allowed.isEmpty()) {
            cir.setReturnValue(stack);
            return;
        }

        Optional<CompetitiveRng.EventSeed> event = CompetitiveRng.beginCompositeEvent(
            RngStream.SUSPICIOUS_STEW,
            "SUSPICIOUS_STEW_EFFECT"
        );
        if (event.isEmpty()) {
            return;
        }

        long raw = event.get().rawValue();
        int selectedIndex = DeterministicRngCore.boundedInt(
            DeterministicRngCore.derive(raw, 0L),
            0,
            allowed.size()
        );
        SetStewEffectEntryAccessor selected = allowed.get(selectedIndex);
        Holder<MobEffect> effect = selected.evgenium$getEffect();
        int duration = deterministicDuration(selected.evgenium$getDuration(), context, DeterministicRngCore.derive(raw, 1L));
        if (!effect.value().isInstantaneous()) {
            duration *= 20;
        }

        SuspiciousStewEffects.Entry newEntry = new SuspiciousStewEffects.Entry(effect, duration);
        stack.update(
            DataComponents.SUSPICIOUS_STEW_EFFECTS,
            SuspiciousStewEffects.EMPTY,
            newEntry,
            SuspiciousStewEffects::withEffectAdded
        );
        cir.setReturnValue(stack);
    }

    private static int deterministicDuration(NumberProvider provider, LootContext context, long raw) {
        if (provider instanceof UniformGenerator uniform) {
            int min = uniform.min().getInt(context);
            int max = uniform.max().getInt(context);
            if (min <= max) {
                return min == max ? min : DeterministicRngCore.boundedInt(raw, min, max + 1);
            }
        }
        return provider.getInt(context);
    }
}
