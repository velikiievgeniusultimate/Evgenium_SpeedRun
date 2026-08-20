package org.evgenium.speedrun.client.mcsr;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.Holder;
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
 *
 * One relevant entity death owns exactly one stream event. Base count and Looting are subdraws of
 * that same event, so using Looting can never move the next entity-drop index forward.
 */
public final class EntityDropRngHooks {
    private static final Map<LootContext, DropSession> SESSIONS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private EntityDropRngHooks() {
    }

    /** Returns true when the vanilla SetItemCountFunction was fully replaced. */
    public static boolean trySetCount(ItemStack stack, LootContext context, NumberProvider count, boolean add) {
        if (!McsrRules.active() || stack == null || context == null || count == null) {
            return false;
        }

        Entity source = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (source == null) {
            return false;
        }

        // MCSR rule: iron golems always yield exactly four iron. Poppies remain vanilla.
        if (source.getType() == EntityType.IRON_GOLEM && stack.getItem() == Items.IRON_INGOT) {
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
            // Rabbit food uses a constant base count. Starting the session here still ensures the
            // kill consumes exactly one FOOD_DROP event; vanilla may safely apply the constant.
            return false;
        }

        int min = uniform.min().getInt(context);
        int max = uniform.max().getInt(context);
        if (min > max) {
            return false;
        }

        long raw = session.nextSubdraw();
        int deterministic = min == max
            ? min
            : DeterministicRngCore.boundedInt(raw, min, max + 1);
        int base = add ? stack.getCount() : 0;
        stack.setCount(base + deterministic);
        return true;
    }

    /**
     * Returns an overridden ItemStack when the vanilla Looting function should be replaced.
     * Empty means the original vanilla function must run unchanged.
     */
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
        if (kind == null || !isRunnerAttributedKill(context)) {
            return Optional.empty();
        }

        if (!(count instanceof UniformGenerator uniform)) {
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
            // The outer entity event is still consumed, but no subdraw is needed for Looting 0.
            return Optional.of(stack);
        }

        float min = uniform.min().getFloat(context);
        float max = uniform.max().getFloat(context);
        if (min > max) {
            return Optional.empty();
        }

        long raw = session.nextSubdraw();
        float roll = min == max
            ? min
            : min + (max - min) * DeterministicRngCore.unitFloat(raw);
        float addition = level * roll;
        stack.grow(Math.round(addition));
        if (limit > 0) {
            stack.limitSize(limit);
        }
        return Optional.of(stack);
    }

    private static boolean isRunnerAttributedKill(LootContext context) {
        // This excludes environmental/other-entity deaths and spectator activity from consuming
        // the runner's competitive sequence. Fire Aspect and indirect kills still retain player
        // attribution through LAST_DAMAGE_PLAYER.
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
        EntityType<?> type = entity.getType();

        if (type == EntityType.BLAZE && item == Items.BLAZE_ROD) {
            return DropKind.BLAZE;
        }
        if (type == EntityType.ENDERMAN && item == Items.ENDER_PEARL) {
            // One global Enderman sequence across all dimensions, matching Ranked's documented
            // entity-drop category rather than creating dimension-specific streams.
            return DropKind.ENDERMAN;
        }

        if (isFoodDrop(type, item)) {
            return DropKind.FOOD;
        }
        return null;
    }

    private static boolean isFoodDrop(EntityType<?> type, Item item) {
        if ((type == EntityType.COW || type == EntityType.MOOSHROOM)
            && (item == Items.BEEF || item == Items.COOKED_BEEF)) {
            return true;
        }
        if ((type == EntityType.PIG || type == EntityType.HOGLIN)
            && (item == Items.PORKCHOP || item == Items.COOKED_PORKCHOP)) {
            return true;
        }
        if (type == EntityType.SHEEP && (item == Items.MUTTON || item == Items.COOKED_MUTTON)) {
            return true;
        }
        if (type == EntityType.CHICKEN && (item == Items.CHICKEN || item == Items.COOKED_CHICKEN)) {
            return true;
        }
        return type == EntityType.RABBIT && (item == Items.RABBIT || item == Items.COOKED_RABBIT);
    }

    private enum DropKind {
        BLAZE(RngStream.BLAZE_DROP),
        ENDERMAN(RngStream.ENDERMAN_DROP),
        FOOD(RngStream.FOOD_DROP);

        private final RngStream stream;

        DropKind(RngStream stream) {
            this.stream = stream;
        }
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
