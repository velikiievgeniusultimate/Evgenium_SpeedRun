package org.evgenium.speedrun.client.match;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.lobby.LobbyRaceResult;
import org.evgenium.speedrun.client.lobby.LobbyRunConfig;
import org.evgenium.speedrun.client.lobby.LobbyService;
import org.evgenium.speedrun.client.lobby.RandomizationType;
import org.evgenium.speedrun.client.lobby.SpeedrunGoal;
import org.evgenium.speedrun.client.mcsr.CompetitiveRng;
import org.evgenium.speedrun.client.runtime.ClientPhase;
import org.evgenium.speedrun.client.runtime.ClientRuntime;
import org.evgenium.speedrun.client.spectator.SpectatorRelayClient;
import org.evgenium.speedrun.client.ui.RaceWaitingScreen;

import java.util.Locale;
import java.util.UUID;

public final class RaceSession {
    private static volatile LobbyRunConfig config;
    private static volatile long worldSeed;
    private static volatile long rngSeed;
    private static volatile boolean cheatsEnabled;
    private static volatile RandomizationType randomizationType = RandomizationType.MCSR_LIKE;
    private static volatile boolean waitingForGo;
    private static volatile boolean readyReported;
    private static volatile boolean running;
    private static volatile boolean finishReported;
    private static volatile boolean localFinished;
    private static volatile long goAtEpochMillis = -1L;
    private static volatile long startNanoTime = -1L;
    private static volatile long finalElapsedNanos = -1L;
    private static volatile int localPlace = -1;

    private RaceSession() {
    }

    public static void arm(LobbyRunConfig newConfig) {
        config = newConfig;
        worldSeed = newConfig.worldSeed();
        rngSeed = newConfig.rngSeed();
        cheatsEnabled = newConfig.cheatsEnabled();
        randomizationType = newConfig.randomizationType();
        CompetitiveRng.arm(rngSeed);

        waitingForGo = false;
        readyReported = false;
        running = false;
        finishReported = false;
        localFinished = false;
        goAtEpochMillis = -1L;
        startNanoTime = -1L;
        finalElapsedNanos = -1L;
        localPlace = -1;

        EvgeniumSpeedRun.LOGGER.info(
            "RaceSession armed worldSeed={} rngSeed={} randomization={} cheats={}",
            worldSeed,
            rngSeed,
            randomizationType.id(),
            cheatsEnabled
        );
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

        server.execute(() -> normalizeSpawnAndPrepareNetwork(minecraft, server, playerId));
    }

    private static void normalizeSpawnAndPrepareNetwork(Minecraft minecraft, MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            minecraft.execute(() -> SpectatorRelayClient.prepareLocalRunnerWorld(RaceSession::reportReady));
            return;
        }

        BlockPos spawn = server.getRespawnData().pos();
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5D;

        player.connection.teleport(x, y, z, 0.0F, 0.0F);
        EvgeniumSpeedRun.LOGGER.info("Normalized speedrun spawn to {}, {}, {}", x, y, z);

        // READY is delayed until the integrated server has been published for spectator relay.
        minecraft.execute(() -> SpectatorRelayClient.prepareLocalRunnerWorld(RaceSession::reportReady));
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
        if (!running || localFinished || finishReported || currentConfig == null) {
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

    public static void onFinishUpdate(LobbyRaceResult result) {
        String localName = Minecraft.getInstance().getUser().getName();
        if (result.playerName().equals(localName)) {
            finishLocal(result);
            return;
        }

        String title = result.playerName() + " занял " + result.place() + " место";
        if (running) {
            int nextPlace = result.place() + 1;
            RaceNotificationHud.show(title, "Ты продолжаешь гонку за " + nextPlace + " место");
        } else {
            RaceNotificationHud.show(title, "Гонка продолжается");
        }
    }

    private static void finishLocal(LobbyRaceResult result) {
        if (localFinished) {
            return;
        }

        localFinished = true;
        running = false;
        waitingForGo = false;
        localPlace = result.place();
        finalElapsedNanos = result.elapsedMillis() * 1_000_000L;
        ClientRuntime.transitionTo(ClientPhase.FINISHED);
        RaceNotificationHud.show(
            "ФИНИШ — " + result.place() + " место",
            "Время: " + formatMillis(result.elapsedMillis()) + " • теперь ты наблюдатель"
        );

        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null && minecraft.player != null) {
            UUID playerId = minecraft.player.getUUID();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return;
                }

                player.getInventory().clearContent();
                player.setGameMode(GameType.SPECTATOR);
            });
        }

        minecraft.execute(() -> minecraft.gui.setScreen(null));
    }

    public static boolean hasRunConfig() {
        return config != null;
    }

    public static long worldSeed() {
        return worldSeed;
    }

    public static long rngSeed() {
        return rngSeed;
    }

    public static boolean cheatsEnabled() {
        return cheatsEnabled;
    }

    public static RandomizationType randomizationType() {
        return randomizationType;
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

    public static boolean isLocalFinished() {
        return localFinished;
    }

    public static int localPlace() {
        return localPlace;
    }

    public static boolean shouldShowTimer() {
        return running || localFinished;
    }

    public static long elapsedNanos() {
        if (localFinished && finalElapsedNanos >= 0L) {
            return finalElapsedNanos;
        }
        if (!running || startNanoTime <= 0L) {
            return 0L;
        }
        return Math.max(0L, System.nanoTime() - startNanoTime);
    }

    private static String formatMillis(long totalMillis) {
        long millis = totalMillis % 1000L;
        long totalSeconds = totalMillis / 1000L;
        long seconds = totalSeconds % 60L;
        long totalMinutes = totalSeconds / 60L;
        long minutes = totalMinutes % 60L;
        long hours = totalMinutes / 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        }
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }
}
