package org.evgenium.speedrun.client.mcsr;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.evgenium.speedrun.mcsr.RngStream;

import java.util.OptionalInt;

/** Competitive replacement for the vanilla 0..2 stick-count roll from a dead bush. */
public final class DeadBushRngHooks {
    private DeadBushRngHooks() {
    }

    public static OptionalInt tryCount(LootContext context, NumberProvider minProvider, NumberProvider maxProvider) {
        if (!McsrRules.active() || context == null || minProvider == null || maxProvider == null) {
            return OptionalInt.empty();
        }

        // Explosion decay remains vanilla and must not move DEAD_BUSH_STICK.
        if (context.hasParameter(LootContextParams.EXPLOSION_RADIUS)) {
            return OptionalInt.empty();
        }

        BlockState state = context.getOptionalParameter(LootContextParams.BLOCK_STATE);
        if (state == null || !state.is(Blocks.DEAD_BUSH)) {
            return OptionalInt.empty();
        }

        int min = minProvider.getInt(context);
        int max = maxProvider.getInt(context);

        // Scope the hook to the exact vanilla 26.2 dead-bush stick count. This avoids hijacking
        // a future datapack/mod using another uniform provider on the same block.
        if (min != 0 || max != 2) {
            return OptionalInt.empty();
        }

        return CompetitiveRng.nextInt(RngStream.DEAD_BUSH_STICK, min, max + 1);
    }
}
