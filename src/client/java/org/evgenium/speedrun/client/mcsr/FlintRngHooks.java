package org.evgenium.speedrun.client.mcsr;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.evgenium.speedrun.mcsr.RngStream;

import java.util.List;
import java.util.Optional;

/**
 * Competitive replacement for the single table_bonus roll used by gravel -> flint.
 *
 * Everything around the roll deliberately remains vanilla: the gravel loot table decides whether
 * the table-bonus condition is reached at all (so Silk Touch never consumes FLINT), and Minecraft's
 * own enchantment helper/condition values choose the Fortune-dependent chance. We only replace the
 * final random decision while MCSR Like is active.
 */
public final class FlintRngHooks {
    private FlintRngHooks() {
    }

    public static Optional<Boolean> tryRoll(
        LootContext context,
        Holder<Enchantment> enchantment,
        List<Float> chances
    ) {
        if (!McsrRules.active() || context == null || chances == null || chances.isEmpty()) {
            return Optional.empty();
        }

        BlockState state = context.getOptionalParameter(LootContextParams.BLOCK_STATE);
        if (state == null || !state.is(Blocks.GRAVEL)) {
            return Optional.empty();
        }

        ItemInstance tool = context.getOptionalParameter(LootContextParams.TOOL);
        int fortuneLevel = tool != null
            ? EnchantmentHelper.getItemEnchantmentLevel(enchantment, tool)
            : 0;
        float chance = chances.get(Math.min(fortuneLevel, chances.size() - 1));

        return CompetitiveRng.chance(RngStream.FLINT, chance);
    }
}
