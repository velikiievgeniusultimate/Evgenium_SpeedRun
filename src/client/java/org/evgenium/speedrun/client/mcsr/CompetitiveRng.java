package org.evgenium.speedrun.client.mcsr;

import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.mcsr.DeterministicRngCore;
import org.evgenium.speedrun.mcsr.RngSequence;
import org.evgenium.speedrun.mcsr.RngStream;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Live competitive RNG controller for the active race.
 *
 * The deterministic math itself lives in the common DeterministicRngCore. This class adds the
 * MCSR-mode gate, per-stream live counters, structured logging and the last-50-event history.
 * In Vanilla mode every operation returns "not handled" and no counter advances.
 */
public final class CompetitiveRng {
    public static final int HISTORY_LIMIT = 50;

    private static final Object HISTORY_LOCK = new Object();
    private static final Deque<RngEvent> HISTORY = new ArrayDeque<>(HISTORY_LIMIT);
    private static final AtomicLong EVENT_ORDINAL = new AtomicLong();

    private static volatile RngSequence sequence;
    private static volatile boolean armed;

    private CompetitiveRng() {
    }

    public static void arm(long seed) {
        sequence = new RngSequence(seed);
        armed = true;
        clearHistory();
        EvgeniumSpeedRun.LOGGER.info(
            "[EVSR-RNG] init rngSeed={} ruleset={}",
            seed,
            McsrRules.MCSR_RULESET_VERSION
        );
    }

    public static OptionalLong nextLong(RngStream stream) {
        RngSequence.Draw draw = draw(stream);
        if (draw == null) {
            return OptionalLong.empty();
        }
        record(draw, "LONG", Long.toString(draw.rawValue()));
        return OptionalLong.of(draw.rawValue());
    }

    /** Returns a deterministic float in [0, 1). */
    public static Optional<Float> nextFloat(RngStream stream) {
        RngSequence.Draw draw = draw(stream);
        if (draw == null) {
            return Optional.empty();
        }
        float result = DeterministicRngCore.unitFloat(draw.rawValue());
        record(draw, "FLOAT", Float.toString(result));
        return Optional.of(result);
    }

    /** Returns a deterministic integer in [originInclusive, boundExclusive). */
    public static OptionalInt nextInt(RngStream stream, int originInclusive, int boundExclusive) {
        if (originInclusive >= boundExclusive) {
            throw new IllegalArgumentException("origin must be < bound");
        }
        RngSequence.Draw draw = draw(stream);
        if (draw == null) {
            return OptionalInt.empty();
        }
        int result = DeterministicRngCore.boundedInt(draw.rawValue(), originInclusive, boundExclusive);
        record(draw, "INT[" + originInclusive + "," + boundExclusive + ")", Integer.toString(result));
        return OptionalInt.of(result);
    }

    public static Optional<Boolean> nextBoolean(RngStream stream) {
        RngSequence.Draw draw = draw(stream);
        if (draw == null) {
            return Optional.empty();
        }
        boolean result = DeterministicRngCore.booleanValue(draw.rawValue());
        record(draw, "BOOLEAN", Boolean.toString(result));
        return Optional.of(result);
    }

    public static Optional<Boolean> chance(RngStream stream, double probability) {
        if (Double.isNaN(probability) || probability < 0.0D || probability > 1.0D) {
            throw new IllegalArgumentException("probability must be in [0, 1]");
        }
        RngSequence.Draw draw = draw(stream);
        if (draw == null) {
            return Optional.empty();
        }
        boolean result = DeterministicRngCore.chance(draw.rawValue(), probability);
        record(draw, "CHANCE(" + probability + ")", Boolean.toString(result));
        return Optional.of(result);
    }

    public static <T> Optional<T> weightedChoice(RngStream stream, List<Weighted<T>> choices) {
        if (choices == null || choices.isEmpty()) {
            throw new IllegalArgumentException("choices must not be empty");
        }
        double[] weights = new double[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            Weighted<T> choice = choices.get(i);
            if (choice == null) {
                throw new IllegalArgumentException("choice == null");
            }
            weights[i] = choice.weight();
        }

        // Validate before an event is consumed.
        DeterministicRngCore.weightedIndex(0L, weights);

        RngSequence.Draw draw = draw(stream);
        if (draw == null) {
            return Optional.empty();
        }
        int selected = DeterministicRngCore.weightedIndex(draw.rawValue(), weights);
        T value = choices.get(selected).value();
        record(draw, "WEIGHTED(" + choices.size() + ")", "index=" + selected + " value=" + String.valueOf(value));
        return Optional.ofNullable(value);
    }

    /**
     * Deterministically shuffles the supplied list using exactly one event index from the stream.
     * Returns false in Vanilla mode so the caller can fall back to vanilla RNG.
     */
    public static <T> boolean shuffleInPlace(RngStream stream, List<T> values) {
        if (values == null) {
            throw new IllegalArgumentException("values == null");
        }
        RngSequence.Draw draw = draw(stream);
        if (draw == null) {
            return false;
        }
        DeterministicRngCore.shuffleInPlace(draw.rawValue(), values);
        record(draw, "SHUFFLE", "size=" + values.size());
        return true;
    }

    public static long counter(RngStream stream) {
        RngSequence current = sequence;
        return current == null ? 0L : current.counter(stream);
    }

    public static Map<RngStream, Long> countersSnapshot() {
        RngSequence current = sequence;
        if (current == null) {
            return Map.of();
        }
        return current.countersSnapshot();
    }

    public static long totalEvents() {
        RngSequence current = sequence;
        return current == null ? 0L : current.totalEvents();
    }

    public static void resetCounters() {
        RngSequence current = sequence;
        if (current != null) {
            current.resetCounters();
        }
        clearHistory();
        if (armed) {
            EvgeniumSpeedRun.LOGGER.info("[EVSR-RNG] counters and history reset");
        }
    }

    public static long rngSeed() {
        RngSequence current = sequence;
        return current == null ? 0L : current.rngSeed();
    }

    public static List<RngEvent> historySnapshot() {
        synchronized (HISTORY_LOCK) {
            return List.copyOf(new ArrayList<>(HISTORY));
        }
    }

    private static RngSequence.Draw draw(RngStream stream) {
        if (!armed || !McsrRules.active()) {
            return null;
        }
        if (stream == null) {
            throw new IllegalArgumentException("stream == null");
        }
        RngSequence current = sequence;
        if (current == null) {
            return null;
        }
        return current.next(stream);
    }

    private static void record(RngSequence.Draw draw, String operation, String result) {
        long ordinal = EVENT_ORDINAL.getAndIncrement();
        RngEvent event = new RngEvent(ordinal, draw.stream(), draw.index(), operation, draw.rawValue(), result);
        synchronized (HISTORY_LOCK) {
            while (HISTORY.size() >= HISTORY_LIMIT) {
                HISTORY.removeFirst();
            }
            HISTORY.addLast(event);
        }
        EvgeniumSpeedRun.LOGGER.info(
            "[EVSR-RNG] stream={} index={} op={} raw={} result={}",
            draw.stream().name(),
            draw.index(),
            operation,
            draw.rawValue(),
            result
        );
    }

    private static void clearHistory() {
        synchronized (HISTORY_LOCK) {
            HISTORY.clear();
        }
        EVENT_ORDINAL.set(0L);
    }

    public record Weighted<T>(T value, double weight) {
        public Weighted {
            if (!Double.isFinite(weight) || weight < 0.0D) {
                throw new IllegalArgumentException("weight must be finite and >= 0");
            }
        }
    }
}
