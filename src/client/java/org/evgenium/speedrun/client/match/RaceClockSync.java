package org.evgenium.speedrun.client.match;

import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.lobby.LobbyTimeSample;

/**
 * Maintains an authoritative host-time axis without requiring the machines' wall clocks to match.
 * The current mapping is anchored to System.nanoTime(), so local wall-clock adjustments after a
 * sample do not move the race timer.
 */
public final class RaceClockSync {
    private static final long PONG_STALE_NANOS = 3_500_000_000L;
    private static final long MAX_RTT_MILLIS = 2_000L;
    private static final long DRIFT_LIMIT_MILLIS = 750L;
    private static final long CANDIDATE_CLUSTER_MILLIS = 200L;

    private static volatile boolean hostMode;
    private static volatile boolean controlConnected;
    private static volatile boolean synchronizedClock;
    private static volatile boolean hasAnchor;
    private static volatile long anchorHostEpochNanos;
    private static volatile long anchorLocalNano;
    private static volatile long acceptedOffsetMillis;
    private static volatile long lastPongNano;
    private static volatile long lastRttMillis = -1L;
    private static volatile long lastDriftMillis;
    private static volatile int goodSamples;
    private static volatile long candidateOffsetMillis;
    private static volatile int candidateSamples;
    private static volatile String lastProblem = "Нет синхронизации";

    private RaceClockSync() {
    }

    public static synchronized void becomeHost() {
        resetInternal();
        hostMode = true;
        controlConnected = true;
        synchronizedClock = true;
        hasAnchor = true;
        long nowNano = System.nanoTime();
        anchorLocalNano = nowNano;
        anchorHostEpochNanos = System.currentTimeMillis() * 1_000_000L;
        lastPongNano = nowNano;
        lastRttMillis = 0L;
        goodSamples = 3;
        lastProblem = "HOST";
    }

    public static synchronized void beginGuest() {
        resetInternal();
        hostMode = false;
        controlConnected = false;
        synchronizedClock = false;
        lastProblem = "Подключение к хосту...";
    }

    public static synchronized void reset() {
        resetInternal();
    }

    private static void resetInternal() {
        hostMode = false;
        controlConnected = false;
        synchronizedClock = false;
        hasAnchor = false;
        anchorHostEpochNanos = 0L;
        anchorLocalNano = 0L;
        acceptedOffsetMillis = 0L;
        lastPongNano = 0L;
        lastRttMillis = -1L;
        lastDriftMillis = 0L;
        goodSamples = 0;
        candidateOffsetMillis = 0L;
        candidateSamples = 0;
        lastProblem = "Нет синхронизации";
    }

    public static synchronized void onControlConnected() {
        if (hostMode) {
            return;
        }
        controlConnected = true;
        synchronizedClock = false;
        goodSamples = 0;
        candidateSamples = 0;
        lastProblem = "Синхронизация времени...";
    }

    public static synchronized void onControlDisconnected(String reason) {
        if (hostMode) {
            return;
        }
        controlConnected = false;
        synchronizedClock = false;
        goodSamples = 0;
        candidateSamples = 0;
        lastProblem = reason == null || reason.isBlank() ? "Связь с хостом потеряна" : reason;
    }

    public static synchronized void acceptSample(LobbyTimeSample sample) {
        if (hostMode || sample == null) {
            return;
        }

        long receiveNano = System.nanoTime();
        long receiveEpochMillis = System.currentTimeMillis();
        long rawRttNanos = Math.max(0L, receiveNano - sample.clientSendNano());
        long serverProcessingMillis = Math.max(0L, sample.hostSendEpochMillis() - sample.hostReceiveEpochMillis());
        long rttMillis = Math.max(0L, rawRttNanos / 1_000_000L - serverProcessingMillis);
        lastPongNano = receiveNano;
        lastRttMillis = rttMillis;

        if (rttMillis > MAX_RTT_MILLIS) {
            synchronizedClock = false;
            goodSamples = 0;
            lastProblem = "Слишком высокий RTT: " + rttMillis + " ms";
            return;
        }

        long offsetMillis = ((sample.hostReceiveEpochMillis() - sample.clientSendEpochMillis())
            + (sample.hostSendEpochMillis() - receiveEpochMillis)) / 2L;

        if (!hasAnchor) {
            acceptOffset(offsetMillis, receiveEpochMillis, receiveNano);
            goodSamples = 1;
            synchronizedClock = false;
            lastProblem = "Синхронизация времени 1/2";
            return;
        }

        long drift = Math.abs(offsetMillis - acceptedOffsetMillis);
        lastDriftMillis = drift;
        if (drift <= DRIFT_LIMIT_MILLIS) {
            acceptOffset(offsetMillis, receiveEpochMillis, receiveNano);
            candidateSamples = 0;
            goodSamples = Math.min(3, goodSamples + 1);
            synchronizedClock = controlConnected && goodSamples >= 2;
            lastProblem = synchronizedClock ? "OK" : "Синхронизация времени 1/2";
            return;
        }

        synchronizedClock = false;
        goodSamples = 0;
        lastProblem = "Пересинхронизация часов: drift " + drift + " ms";

        if (candidateSamples == 0 || Math.abs(offsetMillis - candidateOffsetMillis) > CANDIDATE_CLUSTER_MILLIS) {
            candidateOffsetMillis = offsetMillis;
            candidateSamples = 1;
            return;
        }

        candidateOffsetMillis = (candidateOffsetMillis * candidateSamples + offsetMillis) / (candidateSamples + 1L);
        candidateSamples++;
        if (candidateSamples >= 3) {
            EvgeniumSpeedRun.LOGGER.warn(
                "Race clock offset changed by {} ms; accepting stable replacement offset {} ms",
                drift,
                candidateOffsetMillis
            );
            acceptOffset(candidateOffsetMillis, receiveEpochMillis, receiveNano);
            goodSamples = 2;
            candidateSamples = 0;
            synchronizedClock = controlConnected;
            lastProblem = "OK";
        }
    }

    private static void acceptOffset(long offsetMillis, long receiveEpochMillis, long receiveNano) {
        acceptedOffsetMillis = offsetMillis;
        anchorLocalNano = receiveNano;
        anchorHostEpochNanos = (receiveEpochMillis + offsetMillis) * 1_000_000L;
        hasAnchor = true;
    }

    public static boolean isSafe() {
        if (hostMode) {
            return true;
        }
        long now = System.nanoTime();
        return controlConnected
            && synchronizedClock
            && lastPongNano > 0L
            && now - lastPongNano <= PONG_STALE_NANOS
            && lastRttMillis >= 0L
            && lastRttMillis <= MAX_RTT_MILLIS;
    }

    /** Continues extrapolating host time during an outage so the race timer never pauses. */
    public static long hostNowNanos() {
        if (!hasAnchor) {
            return System.currentTimeMillis() * 1_000_000L;
        }
        return anchorHostEpochNanos + Math.max(0L, System.nanoTime() - anchorLocalNano);
    }

    public static long hostNowMillis() {
        return hostNowNanos() / 1_000_000L;
    }

    public static long lastRttMillis() {
        return lastRttMillis;
    }

    public static long lastDriftMillis() {
        return lastDriftMillis;
    }

    public static boolean controlConnected() {
        return hostMode || controlConnected;
    }

    public static boolean hostMode() {
        return hostMode;
    }

    public static String statusText() {
        if (hostMode) {
            return "HOST clock";
        }
        if (isSafe()) {
            return "Связь OK • RTT " + lastRttMillis + " ms • drift " + lastDriftMillis + " ms";
        }
        return lastProblem;
    }
}
