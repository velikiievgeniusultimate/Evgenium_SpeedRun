package org.evgenium.speedrun.client.lobby;

import net.minecraft.client.Minecraft;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.match.SpeedrunWorldLauncher;
import org.evgenium.speedrun.client.runtime.ClientPhase;
import org.evgenium.speedrun.client.runtime.ClientRuntime;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public final class LobbyService {
    private static final LobbyService INSTANCE = new LobbyService();

    private volatile LobbySnapshot snapshot = LobbySnapshot.empty();
    private volatile String status = "Не подключено";
    private volatile boolean error;
    private volatile boolean hosting;
    private volatile String endpointText = "";
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
            LobbyHost newHost = new LobbyHost(port, playerName, cheatsEnabled, snapshot -> this.snapshot = snapshot, this::acceptRun);
            newHost.start();
            this.host = newHost;
            this.hosting = true;
            this.error = false;
            this.status = "Лобби открыто. Ожидание игроков";
            this.endpointText = "Адрес в локальной сети: " + NetworkAddresses.bestLocalIpv4() + ":" + port;
            ClientRuntime.transitionTo(ClientPhase.LOBBY);
            EvgeniumSpeedRun.LOGGER.info("Lobby opened on TCP port {} (cheats={})", port, cheatsEnabled);
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
        this.error = false;
        this.snapshot = LobbySnapshot.empty();
        this.endpointText = "Хозяин: " + hostName + ":" + port;
        this.status = "Подключение...";
        this.client = new LobbyClient(hostName, port, playerName, snapshot -> this.snapshot = snapshot, this::acceptRun, this::acceptClientStatus);
        this.client.startAsync();
        ClientRuntime.transitionTo(ClientPhase.LOBBY);
    }

    public synchronized String startRun() {
        if (!hosting || host == null) {
            return "Начать забег может только хозяин лобби";
        }

        long seed = ThreadLocalRandom.current().nextLong();
        LobbyRunConfig config = new LobbyRunConfig(seed, snapshot.cheatsEnabled());
        this.status = "Запуск забега. Seed: " + seed;
        this.error = false;
        EvgeniumSpeedRun.LOGGER.info("Starting speedrun with seed {} (cheats={})", seed, config.cheatsEnabled());
        host.startRun(config);
        return null;
    }

    private void acceptRun(LobbyRunConfig config) {
        this.status = "Создание мира. Seed: " + config.seed();
        this.error = false;
        Minecraft.getInstance().execute(() -> SpeedrunWorldLauncher.launch(config));
    }

    private void acceptClientStatus(String newStatus) {
        this.status = newStatus;
        this.error = newStatus.startsWith("Ошибка") || newStatus.contains("закрыто хозяином");
    }

    public synchronized void leave() {
        if (client != null) {
            client.close();
            client = null;
        }
        if (host != null) {
            host.close();
            host = null;
        }
        snapshot = LobbySnapshot.empty();
        status = "Не подключено";
        error = false;
        hosting = false;
        endpointText = "";
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
