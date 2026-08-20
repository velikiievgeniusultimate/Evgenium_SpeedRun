package org.evgenium.speedrun.client.mcsr;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.evgenium.speedrun.mcsr.DeterministicRngCore;
import org.evgenium.speedrun.mcsr.RngStream;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Competitive entity-drop bridge.
 * One relevant entity death owns exactly one stream event. Base count and Looting are subdraws of
 * that same event, so Looting can never move the next entity-drop index forward.
 */
public final class EntityDropRngHooks {
    private static final Map<LootContext, DropSession> SESSIONS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private EntityDropRngHooks() {
    }

    public static boolean trySetCount(ItemStack stack, LootContext context, NumberProvider count, boolean add) {
        if (!McsrRules.active() || stack == null || context == null || count == null) {
            return false;
        }

        Entity source = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (source == null) {
            return false;
        }

        if (source instanceof IronGolem && stack.getItem() == Items.IRON_INGOT) {
            int base = add ? stack.getCount() : 0;
            stack.setCount(base + 4);
            return true;
        }

        DropKind kind = classify(source, stack.getItem());
        if (kind == null || !isRunnerAttributedKill(context)) {
            return false;
        }

        DropSession session = session(context, kind);
        if (session == null) {
            return false;
        }

        if (!(count instanceof UniformGenerator uniform)) {
            return false;
        }

        int min = uniform.min().getInt(context);
        int max = uniform.max().getInt(context);
        if (min > max) {
            return false;
        }

        long raw = session.nextSubdraw();
        int deterministic = min == max ? min : DeterministicRngCore.boundedInt(raw, min, max + 1);
        int base = add ? stack.getCount() : 0;
        stack.setCount(base + deterministic);
        return true;
    }

    public static Optional<ItemStack> tryLooting(
        ItemStack stack,
        LootContext context,
        Holder<Enchantment> enchantment,
        NumberProvider count,
        int limit
    ) {
        if (!McsrRules.active() || stack == null || context == null || enchantment == null || count == null) {
            return Optional.empty();
        }

        Entity source = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (source == null) {
            return Optional.empty();
        }

        DropKind kind = classify(source, stack.getItem());
        if (kind == null || !isRunnerAttributedKill(context) || !(count instanceof UniformGenerator uniform)) {
            return Optional.empty();
        }

        DropSession session = session(context, kind);
        if (session == null) {
            return Optional.empty();
        }

        Entity killer = context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY);
        if (!(killer instanceof LivingEntity livingKiller)) {
            return Optional.of(stack);
        }

        int level = EnchantmentHelper.getEnchantmentLevel(enchantment, livingKiller);
        if (level == 0) {
            return Optional.of(stack);
        }

        float min = uniform.min().getFloat(context);
        float max = uniform.max().getFloat(context);
        if (min > max) {
            return Optional.empty();
        }

        long raw = session.nextSubdraw();
        float roll = min == max ? min : min + (max - min) * DeterministicRngCore.unitFloat(raw);
        stack.grow(Math.round(level * roll));
        if (limit > 0) {
            stack.limitSize(limit);
        }
        return Optional.of(stack);
    }

    private static boolean isRunnerAttributedKill(LootContext context) {
        return context.getOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER) != null;
    }

    private static DropSession session(LootContext context, DropKind kind) {
        synchronized (SESSIONS) {
            DropSession existing = SESSIONS.get(context);
            if (existing != null) {
                return existing.kind == kind ? existing : null;
            }

            Optional<CompetitiveRng.EventSeed> seed = CompetitiveRng.beginCompositeEvent(
                kind.stream,
                "ENTITY_DROP(" + kind.name() + ")"
            );
            if (seed.isEmpty()) {
                return null;
            }

            DropSession created = new DropSession(kind, seed.get().rawValue());
            SESSIONS.put(context, created);
            return created;
        }
    }

    private static DropKind classify(Entity entity, Item item) {
        if (entity instanceof Blaze && item == Items.BLAZE_ROD) {
            return DropKind.BLAZE;
        }
        if (entity instanceof EnderMan && item == Items.ENDER_PEARL) {
            return DropKind.ENDERMAN;
        }
        return isFoodDrop(entity, item) ? DropKind.FOOD : null;
    }

    private static boolean isFoodDrop(Entity entity, Item item) {
        if ((entity instanceof Cow || entity instanceof MushroomCow)
            && (item == Items.BEEF || item == Items.COOKED_BEEF)) {
            return true;
        }
        if ((entity instanceof Pig || entity instanceof Hoglin)
            && (item == Items.PORKCHOP || item == Items.COOKED_PORKCHOP)) {
            return true;
        }
        if (entity instanceof Sheep && (item == Items.MUTTON || item == Items.COOKED_MUTTON)) {
            return true;
        }
        if (entity instanceof Chicken && (item == Items.CHICKEN || item == Items.COOKED_CHICKEN)) {
            return true;
        }
        return entity instanceof Rabbit && (item == Items.RABBIT || item == Items.COOKED_RABBIT);
    }

    private enum DropKind {
        BLAZE(RngStream.BLAZE_DROP),
        ENDERMAN(RngStream.ENDERMAN_DROP),
        FOOD(RngStream.FOOD_DROP);

        private final RngStream stream;
        DropKind(RngStream stream) { this.stream = stream; }
    }

    private static final class DropSession {
        private final DropKind kind;
        private final long eventRaw;
        private long subIndex;

        private DropSession(DropKind kind, long eventRaw) {
            this.kind = kind;
            this.eventRaw = eventRaw;
        }

        private long nextSubdraw() {
            return DeterministicRngCore.derive(eventRaw, subIndex++);
        }
    }
}
