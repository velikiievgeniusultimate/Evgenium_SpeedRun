package org.evgenium.speedrun.client.mcsr;

import org.evgenium.speedrun.EvgeniumSpeedRun;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic, stream-separated competitive RNG foundation.
 *
 * Gameplay hooks must call this class only after checking/accepting the Optional result. In
 * Vanilla mode no counter advances and no MCSR RNG value is produced.
 */
public final class CompetitiveRng {
    private static final EnumMap<RngStream, AtomicLong> COUNTERS = new EnumMap<>(RngStream.class);
    private static volatile long rngSeed;
    private static volatile boolean armed;

    static {
        for (RngStream stream : RngStream.values()) {
            COUNTERS.put(stream, new AtomicLong());
        }
    }

    private CompetitiveRng() {
    }

    public static void arm(long seed) {
        rngSeed = seed;
        armed = true;
        resetCounters();
        EvgeniumSpeedRun.LOGGER.info(
            "[EVSR-RNG] init rngSeed={} ruleset={}",
            seed,
            McsrRules.MCSR_RULESET_VERSION
        );
    }

    public static OptionalLong nextLong(RngStream stream) {
        if (!armed || !McsrRules.active()) {
            return OptionalLong.empty();
        }

        AtomicLong counter = COUNTERS.get(stream);
        long index = counter.getAndIncrement();
        long value = valueFor(rngSeed, stream, index);
        EvgeniumSpeedRun.LOGGER.info(
            "[EVSR-RNG] stream={} index={} value={}",
            stream.name(),
            index,
            value
        );
        return OptionalLong.of(value);
    }

    public static long counter(RngStream stream) {
        return COUNTERS.get(stream).get();
    }

    public static Map<RngStream, Long> countersSnapshot() {
        EnumMap<RngStream, Long> result = new EnumMap<>(RngStream.class);
        for (RngStream stream : RngStream.values()) {
            result.put(stream, counter(stream));
        }
        return Map.copyOf(result);
    }

    public static long totalEvents() {
        long total = 0L;
        for (AtomicLong counter : COUNTERS.values()) {
            total += counter.get();
        }
        return total;
    }

    public static void resetCounters() {
        for (AtomicLong counter : COUNTERS.values()) {
            counter.set(0L);
        }
        if (armed) {
            EvgeniumSpeedRun.LOGGER.info("[EVSR-RNG] counters reset");
        }
    }

    public static long rngSeed() {
        return rngSeed;
    }

    private static long valueFor(long seed, RngStream stream, long index) {
        long streamSalt = fnv1a64(stream.name());
        long indexed = mix64(index + 0x9E3779B97F4A7C15L);
        return mix64(seed ^ streamSalt ^ indexed);
    }

    private static long fnv1a64(String text) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
