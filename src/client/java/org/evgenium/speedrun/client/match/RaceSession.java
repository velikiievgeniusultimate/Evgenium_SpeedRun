package org.evgenium.speedrun.client.match;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.lobby.LobbyRaceResult;
import org.evgenium.speedrun.client.lobby.LobbyRunConfig;
import org.evgenium.speedrun.client.lobby.LobbyService;
import org.evgenium.speedrun.client.lobby.SpeedrunGoal;
import org.evgenium.speedrun.client.runtime.ClientPhase;
import org.evgenium.speedrun.client.runtime.ClientRuntime;
import org.evgenium.speedrun.client.ui.RaceResultScreen;
import org.evgenium.speedrun.client.ui.RaceWaitingScreen;

import java.util.UUID;

public final class RaceSession {
    private static volatile LobbyRunConfig config;
    private static volatile boolean waitingForGo;
    private static volatile boolean readyReported;
    private static volatile boolean running;
    private static volatile boolean finishReported;
    private static volatile boolean finished;
    private static volatile long goAtEpochMillis = -1L;
    private static volatile long startNanoTime = -1L;
    private static volatile long finalElapsedNanos = -1L;

    private RaceSession() {
    }

    public static void arm(LobbyRunConfig newConfig) {
        config = newConfig;
        waitingForGo = false;
        readyReported = false;
        running = false;
        finishReported = false;
        finished = false;
        goAtEpochMillis = -1L;
        startNanoTime = -1L;
        finalElapsedNanos = -1L;
    }

    public static void onWorldJoined(Minecraft minecraft) {
        if (ClientRuntime.phase() != ClientPhase.PREPARING_WORLD || config == null || minecraft.player == null) {
            return;
        }

        waitingForGo = true;
        minecraft.gui.setScreen(new RaceWaitingScreen());

        UUID playerId = minecraft.player.getUUID();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            EvgeniumSpeedRun.LOGGER.warn("Speedrun world joined without an integrated server; reporting READY without spawn normalization");
            reportReady();
            return;
        }

        server.execute(() -> normalizeSpawnAndReportReady(minecraft, server, playerId));
    }

    private static void normalizeSpawnAndReportReady(Minecraft minecraft, MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            minecraft.execute(RaceSession::reportReady);
            return;
        }

        BlockPos spawn = server.getRespawnData().pos();
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5D;

        player.connection.teleport(x, y, z, 0.0F, 0.0F);
        EvgeniumSpeedRun.LOGGER.info("Normalized speedrun spawn to {}, {}, {}", x, y, z);
        minecraft.execute(RaceSession::reportReady);
    }

    private static void reportReady() {
        if (readyReported) {
            return;
        }
        readyReported = true;
        LobbyService.get().reportWorldReady();
    }

    public static void scheduleGo(long startAtEpochMillis) {
        if (config == null) {
            return;
        }
        goAtEpochMillis = startAtEpochMillis;
        waitingForGo = true;
    }

    public static void tick(Minecraft minecraft) {
        if (!waitingForGo || goAtEpochMillis <= 0L) {
            return;
        }

        if (System.currentTimeMillis() < goAtEpochMillis) {
            return;
        }

        waitingForGo = false;
        running = true;
        startNanoTime = System.nanoTime();
        ClientRuntime.transitionTo(ClientPhase.RUNNING);

        if (minecraft.gui.screen() instanceof RaceWaitingScreen) {
            minecraft.gui.setScreen(null);
        }
    }

    public static void onWinScreenOpened() {
        LobbyRunConfig currentConfig = config;
        if (!running || finished || finishReported || currentConfig == null) {
            return;
        }
        if (currentConfig.goal() != SpeedrunGoal.COMPLETE_MINECRAFT) {
            return;
        }

        finishReported = true;
        long elapsedMillis = elapsedNanos() / 1_000_000L;
        EvgeniumSpeedRun.LOGGER.info("Completed Minecraft goal locally in {} ms; reporting finish", elapsedMillis);
        LobbyService.get().reportRaceFinish(elapsedMillis);
    }

    public static void finish(LobbyRaceResult result) {
        if (finished) {
            return;
        }
        finished = true;
        running = false;
        waitingForGo = false;
        finalElapsedNanos = result.elapsedMillis() * 1_000_000L;
        ClientRuntime.transitionTo(ClientPhase.FINISHED);
        Minecraft.getInstance().gui.setScreen(new RaceResultScreen(result));
    }

    public static boolean isWaitingForGo() {
        return waitingForGo;
    }

    public static boolean isReadyReported() {
        return readyReported;
    }

    public static boolean isGoScheduled() {
        return goAtEpochMillis > 0L;
    }

    public static long countdownMillis() {
        if (goAtEpochMillis <= 0L) {
            return -1L;
        }
        return Math.max(0L, goAtEpochMillis - System.currentTimeMillis());
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isFinished() {
        return finished;
    }

    public static long elapsedNanos() {
        if (finished && finalElapsedNanos >= 0L) {
            return finalElapsedNanos;
        }
        if (!running || startNanoTime <= 0L) {
            return 0L;
        }
        return Math.max(0L, System.nanoTime() - startNanoTime);
    }
}
