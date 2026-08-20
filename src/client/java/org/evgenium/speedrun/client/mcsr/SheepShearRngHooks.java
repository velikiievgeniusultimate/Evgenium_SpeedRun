package org.evgenium.speedrun.client.mcsr;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.evgenium.speedrun.mcsr.RngStream;

import java.util.OptionalInt;

/** Competitive replacement for the vanilla 1..3 wool-roll when an adult sheep is sheared. */
public final class SheepShearRngHooks {
    private SheepShearRngHooks() {
    }

    public static OptionalInt tryCount(LootContext context, NumberProvider minProvider, NumberProvider maxProvider) {
        if (!McsrRules.active() || context == null || minProvider == null || maxProvider == null) {
            return OptionalInt.empty();
        }

        Entity source = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (!(source instanceof Sheep)) {
            return OptionalInt.empty();
        }

        ItemInstance tool = context.getOptionalParameter(LootContextParams.TOOL);
        if (!(tool instanceof ItemStack stack) || !stack.is(Items.SHEARS)) {
            return OptionalInt.empty();
        }

        int min = minProvider.getInt(context);
        int max = maxProvider.getInt(context);
        if (min != 1 || max != 3) {
            return OptionalInt.empty();
        }

        return CompetitiveRng.nextInt(RngStream.SHEEP_SHEAR, 1, 4);
    }
}
