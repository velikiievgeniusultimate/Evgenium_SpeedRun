package org.evgenium.speedrun.mcsr;

/**
 * Independent deterministic RNG domains used by the competitive ruleset.
 *
 * A stream owns its own monotonically increasing event index. Consuming one stream must never
 * move any other stream forward.
 */
public enum RngStream {
    DEBUG,
    FLINT,
    DEAD_BUSH_STICK,
    APPLE_DROP,
    BLAZE_DROP,
    ENDERMAN_DROP,
    FOOD_DROP,
    BARTER,
    EYE_BREAK,
    ROTTEN_FLESH_HUNGER,
    SUSPICIOUS_STEW,
    ENDERMITE,
    SHEEP_SHEAR,
    BLAZE_SPAWNER_DELAY,
    BLAZE_SPAWNER_POSITION,
    MAGMA_CUBE_SPAWN,
    WEATHER,
    VILLAGER_TRADES,
    DRAGON_DIRECTION,
    DRAGON_STRAFE,
    DRAGON_PERCH,
    DRAGON_TARGET_HEIGHT,
    NETHER_PORTAL
}
