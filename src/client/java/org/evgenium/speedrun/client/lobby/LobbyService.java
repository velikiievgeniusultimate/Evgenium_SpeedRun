package org.evgenium.speedrun.client.lobby;

import net.minecraft.client.Minecraft;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.match.AdvancementChat;
import org.evgenium.speedrun.client.match.RaceClockSync;
import org.evgenium.speedrun.client.match.RaceSession;
import org.evgenium.speedrun.client.match.SpeedrunWorldLauncher;
import org.evgenium.speedrun.client.spectator.SpectatorRelayClient;
import org.evgenium.speedrun.client.runtime.ClientPhase;
import org.evgenium.speedrun.client.runtime.ClientRuntime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public final class LobbyService {
    private static final LobbyService INSTANCE = new LobbyService();

    private volatile LobbySnapshot snapshot = LobbySnapshot.empty();
    private final CopyOnWriteArrayList<LobbyRaceResult> results = new CopyOnWriteArrayList<>();
    private volatile String status = "Не подключено";
    private volatile boolean error;
    private volatile boolean hosting;
    private volatile String endpointText = "";
    private volatile int lobbyPort = -1;
    private volatile String remoteLobbyHost = "";
    private volatile RandomizationType configuredRandomizationType = RandomizationType.MCSR_LIKE;
    private LobbyHost host;
    private LobbyClient client;

    private LobbyService() {
    }

    public static LobbyService get() {
        return INSTANCE;
    }

    public synchronized String host(int port, String playerName, boolean cheatsEnabled) {
        leave();
        try {
            LobbyHost newHost = new LobbyHost(
                port,
                playerName,
                cheatsEnabled,
                snapshot -> this.snapshot = snapshot,
                this::acceptRun,
                this::acceptGo,
                this::acceptFinishUpdate,
                this::acceptAdvancement,
                tunnelId -> SpectatorRelayClient.openTargetTunnel("127.0.0.1", port, tunnelId)
            );
            newHost.start();
            this.host = newHost;
            this.hosting = true;
            this.lobbyPort = port;
            this.remoteLobbyHost = "127.0.0.1";
            this.error = false;
            this.status = "Лобби открыто. Ожидание игроков";
            this.endpointText = "Адрес в локальной сети: " + NetworkAddresses.bestLocalIpv4() + ":" + port;
            RaceClockSync.becomeHost();
            ClientRuntime.transitionTo(ClientPhase.LOBBY);
            EvgeniumSpeedRun.LOGGER.info(
                "Lobby opened on TCP port {} (cheats={}, randomization={})",
                port,
                cheatsEnabled,
                configuredRandomizationType.id()
            );
            return null;
        } catch (IOException exception) {
            this.error = true;
            this.status = "Ошибка: " + exception.getMessage();
            return "Не удалось открыть порт " + port + ": " + exception.getMessage();
        }
    }

    public synchronized void join(String hostName, int port, String playerName) {
        leave();
        this.hosting = false;
        this.lobbyPort = port;
        this.remoteLobbyHost = hostName;
        this.error = false;
        this.snapshot = LobbySnapshot.empty();
        this.endpointText = "Хозяин: " + hostName + ":" + port;
        this.status = "Подключение...";
        RaceClockSync.beginGuest();
        this.client = new LobbyClient(
            hostName,
            port,
            playerName,
            snapshot -> this.snapshot = snapshot,
            this::acceptRun,
            this::acceptResume,
            this::acceptGo,
            this::acceptFinishUpdate,
            this::acceptAdvancement,
            tunnelId -> SpectatorRelayClient.openTargetTunnel(hostName, port, tunnelId),
            this::acceptClientStatus,
            this::acceptClientConnection,
            RaceClockSync::acceptSample
        );
        this.client.startAsync();
        ClientRuntime.transitionTo(ClientPhase.LOBBY);
    }

    public synchronized String selectGoal(SpeedrunGoal goal) {
        if (!hosting || host == null) {
            return "Менять цель может только хозяин лобби";
        }
        if (!host.setGoal(goal)) {
            return "Нельзя менять цель после начала подготовки забега";
        }
        this.status = "Цель выбрана: " + goal.displayName();
        this.error = false;
        return null;
    }

    public synchronized String setCheatsEnabled(boolean enabled) {
        if (!hosting || host == null) {
            return "Менять читы может только хозяин лобби";
        }
        if (!host.setCheatsEnabled(enabled)) {
            return "Нельзя менять читы после начала подготовки забега";
        }
        this.status = "Читы: " + (enabled ? "ВКЛ" : "ВЫКЛ");
        this.error = false;
        return null;
    }

    public synchronized String setRandomizationType(RandomizationType type) {
        if (!hosting || host == null) {
            return "Менять рандомизацию может только хозяин лобби";
        }
        RandomizationType previous = configuredRandomizationType;
        configuredRandomizationType = type == null ? RandomizationType.MCSR_LIKE : type;

        if (!host.setGoal(snapshot.goal())) {
            configuredRandomizationType = previous;
            return "Нельзя менять рандомизацию после начала подготовки забега";
        }

        this.status = "Рандомизация: " + configuredRandomizationType.displayName();
        this.error = false;
        return null;
    }

    RandomizationType configuredRandomizationType() {
        return configuredRandomizationType;
    }

    public synchronized String startRun() {
        if (!hosting || host == null) {
            return "Начать забег может только хозяин лобби";
        }

        results.clear();
        long worldSeed = ThreadLocalRandom.current().nextLong();
        long rngSeed = worldSeed;

        LobbyRunConfig config = new LobbyRunConfig(
            worldSeed,
            rngSeed,
            snapshot.cheatsEnabled(),
            snapshot.goal(),
            snapshot.randomizationType()
        );
        this.status = "Создание миров. World Seed: " + worldSeed;
        this.error = false;
        EvgeniumSpeedRun.LOGGER.info(
            "Preparing synchronized speedrun worldSeed={} rngSeed={} (goal={}, cheats={}, randomization={})",
            worldSeed,
            rngSeed,
            config.goal().id(),
            config.cheatsEnabled(),
            config.randomizationType().id()
        );
        host.startRun(config);
        return null;
    }

    private void acceptRun(LobbyRunConfig config) {
        results.clear();
        this.status = "Создание мира. World Seed: " + config.worldSeed() + " • RNG Seed: " + config.rngSeed();
        this.error = false;
        Minecraft.getInstance().execute(() -> SpeedrunWorldLauncher.launch(config));
    }

    private synchronized void acceptResume(LobbyResumeState resume) {
        LobbyRunConfig config = resume.config();
        this.status = "Связь восстановлена. Синхронизация забега...";
        this.error = false;

        Minecraft.getInstance().execute(() -> {
            if (!RaceSession.hasRunConfig()) {
                SpeedrunWorldLauncher.launch(config);
                return;
            }
            if (!RaceSession.matchesConfig(config)) {
                this.status = "Ошибка: reconnect получил другой конфиг забега";
                this.error = true;
                return;
            }

            for (LobbyRaceResult result : resume.results()) {
                boolean exists = results.stream().anyMatch(existing -> existing.playerName().equals(result.playerName()));
                if (!exists) {
                    results.add(result);
                    RaceSession.onFinishUpdate(result);
                }
            }

            if (resume.goIssued()) {
                RaceSession.resynchronizeGo(resume.goAtEpochMillis());
            } else {
                RaceSession.clearGoWhilePreparing();
                LobbyClient current = client;
                if (current != null && RaceSession.isReadyReported()) {
                    current.sendReady();
                }
            }
        });
    }

    public synchronized void reportWorldReady() {
        this.status = "Мир готов. Ждём остальных игроков";
        this.error = false;
        if (hosting && host != null) {
            host.markHostReady();
        } else if (client != null) {
            client.sendReady();
        }
    }

    private void acceptGo(long startAtEpochMillis) {
        this.status = "Все готовы. Старт!";
        this.error = false;
        Minecraft.getInstance().execute(() -> RaceSession.scheduleGo(startAtEpochMillis));
    }

    public synchronized void reportRaceFinish(long elapsedMillis) {
        this.status = "Финиш отправлен хозяину...";
        this.error = false;
        if (hosting && host != null) {
            host.markHostFinished(elapsedMillis);
        } else if (client != null) {
            client.sendFinish(elapsedMillis);
        }
    }

    public synchronized void reportAdvancement(String titleKey, String fallbackTitle) {
        if (!RaceSession.isRunning()) {
            return;
        }
        if (hosting && host != null) {
            host.markHostAdvancement(titleKey, fallbackTitle);
        } else if (client != null) {
            client.sendAdvancement(titleKey, fallbackTitle);
        }
    }

    private void acceptFinishUpdate(LobbyRaceResult result) {
        boolean exists = results.stream().anyMatch(existing -> existing.playerName().equals(result.playerName()));
        if (exists) {
            return;
        }
        results.add(result);
        this.status = result.playerName() + " занял " + result.place() + " место";
        this.error = false;
        Minecraft.getInstance().execute(() -> RaceSession.onFinishUpdate(result));
    }

    private void acceptAdvancement(LobbyAdvancement advancement) {
        Minecraft.getInstance().execute(() -> AdvancementChat.show(advancement));
    }

    private void acceptClientStatus(String newStatus) {
        this.status = newStatus;
        this.error = newStatus.startsWith("Ошибка") || newStatus.contains("потеряна") || newStatus.contains("timeout");
    }

    private void acceptClientConnection(boolean connected) {
        if (connected) {
            RaceClockSync.onControlConnected();
        } else {
            RaceClockSync.onControlDisconnected("Связь с хостом потеряна");
        }
    }

    public synchronized void forceReconnect() {
        if (client != null) {
            status = "Принудительное переподключение...";
            client.forceReconnect();
        }
    }

    public List<LobbyRaceResult> results() {
        return List.copyOf(results);
    }

    public List<String> runningPlayerNames() {
        List<String> running = new ArrayList<>();
        for (LobbyPlayer player : snapshot.players()) {
            if (!player.connected()) {
                continue;
            }
            boolean finished = results.stream().anyMatch(result -> result.playerName().equals(player.name()));
            if (!finished) {
                running.add(player.name());
            }
        }
        return running;
    }

    public boolean hasFinished(String playerName) {
        return results.stream().anyMatch(result -> result.playerName().equals(playerName));
    }

    public String localPlayerName() {
        return Minecraft.getInstance().getUser().getName();
    }

    public String relayHost() {
        return hosting ? "127.0.0.1" : remoteLobbyHost;
    }

    public int relayPort() {
        return lobbyPort;
    }

    public synchronized void leave() {
        SpectatorRelayClient.closeLocalProxy();
        if (client != null) {
            client.close();
            client = null;
        }
        if (host != null) {
            host.close();
            host = null;
        }
        RaceClockSync.reset();
        configuredRandomizationType = RandomizationType.MCSR_LIKE;
        snapshot = LobbySnapshot.empty();
        results.clear();
        status = "Не подключено";
        error = false;
        hosting = false;
        endpointText = "";
        lobbyPort = -1;
        remoteLobbyHost = "";
        if (ClientRuntime.phase() == ClientPhase.LOBBY) {
            ClientRuntime.transitionTo(ClientPhase.MENU);
        }
    }

    public LobbySnapshot snapshot() {
        return snapshot;
    }

    public String status() {
        return status;
    }

    public boolean hasError() {
        return error;
    }

    public boolean isHosting() {
        return hosting;
    }

    public String endpointText() {
        return endpointText;
    }
}
