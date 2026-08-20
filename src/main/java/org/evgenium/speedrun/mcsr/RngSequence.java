package org.evgenium.speedrun.mcsr;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable sequence state for one competitive RNG seed.
 *
 * The only mutable data is one counter per named stream. No stream can advance another stream.
 */
public final class RngSequence {
    private final long rngSeed;
    private final EnumMap<RngStream, AtomicLong> counters = new EnumMap<>(RngStream.class);

    public RngSequence(long rngSeed) {
        this.rngSeed = rngSeed;
        for (RngStream stream : RngStream.values()) {
            counters.put(stream, new AtomicLong());
        }
    }

    public Draw next(RngStream stream) {
        AtomicLong counter = counters.get(stream);
        if (counter == null) {
            throw new IllegalArgumentException("Unknown RNG stream: " + stream);
        }
        long index = counter.getAndIncrement();
        return new Draw(stream, index, DeterministicRngCore.valueFor(rngSeed, stream, index));
    }

    public long peek(RngStream stream, long index) {
        return DeterministicRngCore.valueFor(rngSeed, stream, index);
    }

    public long counter(RngStream stream) {
        AtomicLong counter = counters.get(stream);
        if (counter == null) {
            throw new IllegalArgumentException("Unknown RNG stream: " + stream);
        }
        return counter.get();
    }

    public Map<RngStream, Long> countersSnapshot() {
        EnumMap<RngStream, Long> snapshot = new EnumMap<>(RngStream.class);
        for (RngStream stream : RngStream.values()) {
            snapshot.put(stream, counter(stream));
        }
        return Map.copyOf(snapshot);
    }

    public long totalEvents() {
        long total = 0L;
        for (AtomicLong counter : counters.values()) {
            total += counter.get();
        }
        return total;
    }

    public void resetCounters() {
        for (AtomicLong counter : counters.values()) {
            counter.set(0L);
        }
    }

    public long rngSeed() {
        return rngSeed;
    }

    public record Draw(RngStream stream, long index, long rawValue) {
    }
}
