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

/** Competitive replacement for the vanilla apple table-bonus roll on oak/dark-oak leaves. */
public final class AppleRngHooks {
    private static final float[] VANILLA_APPLE_CHANCES = {
        0.005F,
        0.0055555557F,
        0.00625F,
        0.008333334F,
        0.025F
    };

    private AppleRngHooks() {
    }

    public static Optional<Boolean> tryRoll(
        LootContext context,
        Holder<Enchantment> enchantment,
        List<Float> chances
    ) {
        if (!McsrRules.active() || context == null || chances == null || chances.isEmpty()) {
            return Optional.empty();
        }

        // Explosion survival/decay is intentionally left completely vanilla. More importantly,
        // explosions must never conditionally advance a competitive block-drop stream.
        if (context.hasParameter(LootContextParams.EXPLOSION_RADIUS)) {
            return Optional.empty();
        }

        BlockState state = context.getOptionalParameter(LootContextParams.BLOCK_STATE);
        if (state == null || (!state.is(Blocks.OAK_LEAVES) && !state.is(Blocks.DARK_OAK_LEAVES))) {
            return Optional.empty();
        }

        // Oak/dark-oak loot tables contain several table_bonus conditions (sapling, sticks,
        // apple). Match the vanilla 26.2 apple chance table so only the apple decision is replaced.
        if (!isVanillaAppleChanceTable(chances)) {
            return Optional.empty();
        }

        ItemInstance tool = context.getOptionalParameter(LootContextParams.TOOL);
        int fortuneLevel = tool != null
            ? EnchantmentHelper.getItemEnchantmentLevel(enchantment, tool)
            : 0;
        float chance = chances.get(Math.min(fortuneLevel, chances.size() - 1));

        return CompetitiveRng.chance(RngStream.APPLE_DROP, chance);
    }

    private static boolean isVanillaAppleChanceTable(List<Float> chances) {
        if (chances.size() != VANILLA_APPLE_CHANCES.length) {
            return false;
        }
        for (int i = 0; i < VANILLA_APPLE_CHANCES.length; i++) {
            Float actual = chances.get(i);
            if (actual == null || Float.floatToIntBits(actual) != Float.floatToIntBits(VANILLA_APPLE_CHANCES[i])) {
                return false;
            }
        }
        return true;
    }
}
