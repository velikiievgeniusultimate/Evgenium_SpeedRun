package org.evgenium.speedrun.client.mcsr;

/**
 * Independent competitive RNG domains. Gameplay hooks will consume only their own stream,
 * so unrelated random events can never shift another category's sequence.
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
