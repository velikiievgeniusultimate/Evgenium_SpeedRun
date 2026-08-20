package org.evgenium.speedrun.mcsr;

import java.util.Collections;
import java.util.List;

/**
 * Pure deterministic RNG primitives.
 *
 * There is deliberately no java.util.Random instance here. Every event is addressed by the
 * tuple (rngSeed, stream, eventIndex). Composite operations such as shuffle derive all of their
 * internal draws from that single event value and therefore never consume another stream or an
 * unpredictable number of event indices.
 */
public final class DeterministicRngCore {
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private DeterministicRngCore() {
    }

    public static long valueFor(long rngSeed, RngStream stream, long eventIndex) {
        if (stream == null) {
            throw new IllegalArgumentException("stream == null");
        }
        if (eventIndex < 0L) {
            throw new IllegalArgumentException("eventIndex must be >= 0");
        }

        long streamSalt = fnv1a64(stream.name());
        long indexed = mix64(eventIndex + GOLDEN_GAMMA);
        return mix64(rngSeed ^ streamSalt ^ indexed);
    }

    public static float unitFloat(long rawValue) {
        // Exactly 24 random mantissa bits -> [0, 1), matching the useful precision of float.
        return (float) ((rawValue >>> 40) * 0x1.0p-24);
    }

    public static double unitDouble(long rawValue) {
        return (rawValue >>> 11) * 0x1.0p-53;
    }

    public static boolean booleanValue(long rawValue) {
        return (rawValue & 1L) != 0L;
    }

    public static boolean chance(long rawValue, double probability) {
        if (Double.isNaN(probability) || probability < 0.0D || probability > 1.0D) {
            throw new IllegalArgumentException("probability must be in [0, 1]");
        }
        if (probability <= 0.0D) {
            return false;
        }
        if (probability >= 1.0D) {
            return true;
        }
        return unitDouble(rawValue) < probability;
    }

    public static int boundedInt(long rawValue, int originInclusive, int boundExclusive) {
        if (originInclusive >= boundExclusive) {
            throw new IllegalArgumentException("origin must be < bound");
        }

        long range = (long) boundExclusive - originInclusive;
        long candidate = rawValue;

        // Rejection sampling without consuming another event index. Rejected candidates are
        // deterministically re-mixed from the same event value.
        while (true) {
            long u = candidate >>> 1;
            long result = u % range;
            if (u + (range - 1L) - result >= 0L) {
                return (int) (originInclusive + result);
            }
            candidate = mix64(candidate + GOLDEN_GAMMA);
        }
    }

    public static int weightedIndex(long rawValue, double[] weights) {
        if (weights == null || weights.length == 0) {
            throw new IllegalArgumentException("weights must not be empty");
        }

        double total = 0.0D;
        for (double weight : weights) {
            if (!Double.isFinite(weight) || weight < 0.0D) {
                throw new IllegalArgumentException("weights must be finite and >= 0");
            }
            total += weight;
        }
        if (!(total > 0.0D) || !Double.isFinite(total)) {
            throw new IllegalArgumentException("total weight must be finite and > 0");
        }

        double target = unitDouble(rawValue) * total;
        double accumulated = 0.0D;
        int lastPositive = -1;
        for (int i = 0; i < weights.length; i++) {
            double weight = weights[i];
            if (weight <= 0.0D) {
                continue;
            }
            lastPositive = i;
            accumulated += weight;
            if (target < accumulated) {
                return i;
            }
        }
        return lastPositive;
    }

    public static <T> void shuffleInPlace(long rawValue, List<T> values) {
        if (values == null) {
            throw new IllegalArgumentException("values == null");
        }
        long state = rawValue;
        for (int i = values.size() - 1; i > 0; i--) {
            state = mix64(state + GOLDEN_GAMMA);
            int j = boundedInt(state, 0, i + 1);
            Collections.swap(values, i, j);
        }
    }

    static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static long fnv1a64(String text) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
