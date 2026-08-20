package org.evgenium.speedrun.client.mcsr;

import org.evgenium.speedrun.mcsr.DeterministicRngCore;
import org.evgenium.speedrun.mcsr.RngSequence;
import org.evgenium.speedrun.mcsr.RngStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompetitiveRngSelfTest {
    private CompetitiveRngSelfTest() {
    }

    public static Report run(long currentSeed) {
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        check("same seed reproduces all streams", passed, failed, () -> testReproducibility(currentSeed));
        check("golden vectors survive restart/refactor", passed, failed, CompetitiveRngSelfTest::testGoldenVectors);
        check("streams are independent", passed, failed, CompetitiveRngSelfTest::testStreamIndependence);
        check("float is always [0,1)", passed, failed, CompetitiveRngSelfTest::testFloatRange);
        check("integer ranges are respected", passed, failed, CompetitiveRngSelfTest::testIntRanges);
        check("boolean/chance primitives", passed, failed, CompetitiveRngSelfTest::testChance);
        check("weighted choice is deterministic", passed, failed, CompetitiveRngSelfTest::testWeightedChoice);
        check("shuffle is deterministic permutation", passed, failed, CompetitiveRngSelfTest::testShuffle);
        check("extreme signed long seeds", passed, failed, CompetitiveRngSelfTest::testExtremeSeeds);

        return new Report(List.copyOf(passed), List.copyOf(failed));
    }

    private static void testReproducibility(long seed) {
        RngSequence left = new RngSequence(seed);
        RngSequence right = new RngSequence(seed);
        for (RngStream stream : RngStream.values()) {
            for (int i = 0; i < 256; i++) {
                long a = left.next(stream).rawValue();
                long b = right.next(stream).rawValue();
                require(a == b, "mismatch " + stream + " index=" + i);
            }
        }
    }

    /**
     * These exact outputs are part of MCSR-Like ruleset R1. A restart, JVM change or refactor
     * must not change them. If we ever intentionally change them, that requires a new ruleset.
     */
    private static void testGoldenVectors() {
        assertVector(0L, RngStream.FLINT, 0L, 54553388444833438L);
        assertVector(-1L, RngStream.BLAZE_DROP, 0L, 515844464882043509L);
        assertVector(Long.MIN_VALUE, RngStream.BARTER, 7L, 3591598609350414834L);
        assertVector(Long.MAX_VALUE, RngStream.EYE_BREAK, 9999L, 285930917994781591L);
        assertVector(123456789L, RngStream.ENDERMAN_DROP, 42L, 141079692125242005L);
    }

    private static void assertVector(long seed, RngStream stream, long index, long expected) {
        long actual = DeterministicRngCore.valueFor(seed, stream, index);
        require(
            actual == expected,
            "golden vector changed seed=" + seed + " stream=" + stream + " index=" + index
                + " expected=" + expected + " actual=" + actual
        );
    }

    private static void testStreamIndependence() {
        long seed = -7046029254386353131L;
        RngSequence noisy = new RngSequence(seed);
        RngSequence clean = new RngSequence(seed);

        for (int i = 0; i < 100; i++) {
            noisy.next(RngStream.FLINT);
        }

        long noisyBlaze = noisy.next(RngStream.BLAZE_DROP).rawValue();
        long cleanBlaze = clean.next(RngStream.BLAZE_DROP).rawValue();
        require(noisyBlaze == cleanBlaze, "100 FLINT events shifted BLAZE_DROP");
        require(noisy.counter(RngStream.FLINT) == 100L, "FLINT counter != 100");
        require(noisy.counter(RngStream.BLAZE_DROP) == 1L, "BLAZE counter != 1");
        require(clean.counter(RngStream.FLINT) == 0L, "clean FLINT counter moved");
    }

    private static void testFloatRange() {
        for (int i = 0; i < 20_000; i++) {
            long raw = DeterministicRngCore.valueFor(123456789L, RngStream.DEBUG, i);
            float value = DeterministicRngCore.unitFloat(raw);
            require(value >= 0.0F && value < 1.0F, "float outside range: " + value);
        }
    }

    private static void testIntRanges() {
        int[][] ranges = {
            {0, 1},
            {0, 2},
            {-5, 6},
            {100, 1000},
            {Integer.MIN_VALUE, Integer.MAX_VALUE}
        };
        for (int[] range : ranges) {
            for (int i = 0; i < 10_000; i++) {
                long raw = DeterministicRngCore.valueFor(-99L, RngStream.DEBUG, i);
                int value = DeterministicRngCore.boundedInt(raw, range[0], range[1]);
                require(value >= range[0] && value < range[1], "int outside range");
            }
        }
    }

    private static void testChance() {
        for (int i = 0; i < 1024; i++) {
            long raw = DeterministicRngCore.valueFor(42L, RngStream.DEBUG, i);
            require(!DeterministicRngCore.chance(raw, 0.0D), "p=0 returned true");
            require(DeterministicRngCore.chance(raw, 1.0D), "p=1 returned false");
            boolean first = DeterministicRngCore.chance(raw, 0.375D);
            boolean second = DeterministicRngCore.chance(raw, 0.375D);
            require(first == second, "chance not reproducible");
            require(DeterministicRngCore.booleanValue(raw) == DeterministicRngCore.booleanValue(raw), "boolean not reproducible");
        }
    }

    private static void testWeightedChoice() {
        double[] weights = {1.0D, 2.0D, 0.0D, 7.0D};
        for (int i = 0; i < 4096; i++) {
            long raw = DeterministicRngCore.valueFor(777L, RngStream.BARTER, i);
            int first = DeterministicRngCore.weightedIndex(raw, weights);
            int second = DeterministicRngCore.weightedIndex(raw, weights);
            require(first == second, "weighted result changed");
            require(first == 0 || first == 1 || first == 3, "zero-weight entry selected");
        }
    }

    private static void testShuffle() {
        List<Integer> source = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            source.add(i);
        }
        List<Integer> left = new ArrayList<>(source);
        List<Integer> right = new ArrayList<>(source);
        long raw = DeterministicRngCore.valueFor(Long.MIN_VALUE, RngStream.BARTER, 17L);
        DeterministicRngCore.shuffleInPlace(raw, left);
        DeterministicRngCore.shuffleInPlace(raw, right);
        require(left.equals(right), "same shuffle event produced different order");

        List<Integer> sorted = new ArrayList<>(left);
        Collections.sort(sorted);
        require(sorted.equals(source), "shuffle lost or duplicated values");
    }

    private static void testExtremeSeeds() {
        long[] seeds = {
            Long.MIN_VALUE,
            Long.MIN_VALUE + 1L,
            -1L,
            0L,
            1L,
            Long.MAX_VALUE - 1L,
            Long.MAX_VALUE
        };
        RngStream[] streams = {
            RngStream.FLINT,
            RngStream.BLAZE_DROP,
            RngStream.ENDERMAN_DROP,
            RngStream.BARTER,
            RngStream.EYE_BREAK
        };
        long[] indices = {0L, 1L, 2L, 7L, 9999L, 1_000_000L};

        for (long seed : seeds) {
            for (RngStream stream : streams) {
                for (long index : indices) {
                    long first = DeterministicRngCore.valueFor(seed, stream, index);
                    long second = DeterministicRngCore.valueFor(seed, stream, index);
                    require(first == second, "edge seed mismatch seed=" + seed + " stream=" + stream + " index=" + index);
                }
            }
        }
    }

    private static void check(String name, List<String> passed, List<String> failed, ThrowingRunnable test) {
        try {
            test.run();
            passed.add(name);
        } catch (Throwable throwable) {
            failed.add(name + ": " + throwable.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public record Report(List<String> passed, List<String> failed) {
        public boolean success() {
            return failed.isEmpty();
        }
    }
}
