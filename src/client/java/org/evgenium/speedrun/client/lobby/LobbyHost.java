package org.evgenium.speedrun.client.lobby;

import org.evgenium.speedrun.EvgeniumSpeedRun;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

final class LobbyHost implements AutoCloseable {
    private static final long COUNTDOWN_MILLIS = 4000L;
    private static final long TUNNEL_TIMEOUT_SECONDS = 12L;
    private static final int CONTROL_READ_TIMEOUT_MILLIS = 6_500;
    private static final long RECENT_HEARTBEAT_NANOS = 3_500_000_000L;

    private final int port;
    private final LobbyPlayer hostPlayer;
    private volatile boolean cheatsEnabled;
    private final Consumer<LobbySnapshot> snapshotConsumer;
    private final Consumer<LobbyRunConfig> runConsumer;
    private final LongConsumer goConsumer;
    private final Consumer<LobbyRaceResult> resultConsumer;
    private final Consumer<LobbyAdvancement> advancementConsumer;
    private final LongConsumer localTunnelConsumer;
    private final Map<String, Participant> participants = new LinkedHashMap<>();
    private final Map<String, LobbyRaceResult> finishes = new LinkedHashMap<>();
    private final ConcurrentHashMap<Long, PendingTunnel> tunnels = new ConcurrentHashMap<>();
    private final AtomicLong nextTunnelId = new AtomicLong(1L);

    private volatile boolean running;
    private volatile boolean preparingRun;
    private volatile boolean hostReady;
    private volatile boolean goIssued;
    private volatile long goAtEpochMillis = -1L;
    private volatile SpeedrunGoal goal = SpeedrunGoal.COMPLETE_MINECRAFT;
    private volatile LobbyRunConfig currentRunConfig;
    private ServerSocket serverSocket;

    LobbyHost(int port, String hostName, boolean cheatsEnabled, Consumer<LobbySnapshot> snapshotConsumer,
              Consumer<LobbyRunConfig> runConsumer, LongConsumer goConsumer,
              Consumer<LobbyRaceResult> resultConsumer, Consumer<LobbyAdvancement> advancementConsumer,
              LongConsumer localTunnelConsumer) {
        this.port = port;
        this.hostPlayer = new LobbyPlayer(hostName, true, true);
        this.cheatsEnabled = cheatsEnabled;
        this.snapshotConsumer = snapshotConsumer;
        this.runConsumer = runConsumer;
        this.goConsumer = goConsumer;
        this.resultConsumer = resultConsumer;
        this.advancementConsumer = advancementConsumer;
        this.localTunnelConsumer = localTunnelConsumer;
    }

    int port() {
        return port;
    }

    void start() throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        this.serverSocket = socket;
        this.running = true;
        publishSnapshot();

        Thread thread = new Thread(this::acceptLoop, "Evgenium-Lobby-Accept");
        thread.setDaemon(true);
        thread.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                Thread connectionThread = new Thread(() -> handleSocket(socket), "Evgenium-Lobby-Connection");
                connectionThread.setDaemon(true);
                connectionThread.start();
            } catch (SocketException exception) {
                if (running) {
                    EvgeniumSpeedRun.LOGGER.warn("Lobby accept socket failed", exception);
                }
            } catch (IOException exception) {
                if (running) {
                    EvgeniumSpeedRun.LOGGER.warn("Lobby accept failed", exception);
                }
            }
        }
    }

    private void handleSocket(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            LobbyProtocol.ConnectionHello hello = LobbyProtocol.readConnectionHello(in);
            if (hello.channel() == LobbyProtocol.CHANNEL_CONTROL) {
                handleControl(socket, in, hello.playerName(), hello.reconnectToken());
                return;
            }
            if (hello.channel() == LobbyProtocol.CHANNEL_SPECTATOR_SOURCE) {
                handleSpectatorSource(socket, hello.playerName(), hello.targetName());
                return;
            }
            if (hello.channel() == LobbyProtocol.CHANNEL_SPECTATOR_TARGET) {
                handleSpectatorTarget(socket, hello.tunnelId());
                return;
            }
            closeQuietly(socket);
        } catch (IOException exception) {
            closeQuietly(socket);
            if (running) {
                EvgeniumSpeedRun.LOGGER.info("Lobby connection rejected: {}", exception.getMessage());
            }
        }
    }

    private void handleControl(Socket socket, DataInputStream in, String playerName, String reconnectToken) {
        Participant participant = null;
        Peer peer = null;
        boolean reconnected = false;

        try (socket; DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(CONTROL_READ_TIMEOUT_MILLIS);

            synchronized (this) {
                participant = participants.get(playerName);
                if (participant == null) {
                    if (currentRunConfig != null || preparingRun || goIssued) {
                        LobbyProtocol.writeError(out, "Забег уже запущен; новое подключение невозможно");
                        return;
                    }
                    participant = new Participant(playerName, UUID.randomUUID().toString());
                    participants.put(playerName, participant);
                } else {
                    if (reconnectToken == null || reconnectToken.isEmpty() || !participant.reconnectToken.equals(reconnectToken)) {
                        LobbyProtocol.writeError(out, "Имя уже занято другим участником");
                        return;
                    }
                    reconnected = true;
                    if (participant.peer != null) {
                        closeQuietly(participant.peer.socket);
                    }
                }

                peer = new Peer(participant, socket, out);
                participant.peer = peer;
                participant.connected = true;
                participant.lastHeartbeatNano = System.nanoTime();
                peer.sendSession(reconnected);
                peer.send(snapshot());
                if (currentRunConfig != null) {
                    peer.sendResume(new LobbyResumeState(
                        currentRunConfig,
                        goIssued,
                        goAtEpochMillis,
                        new ArrayList<>(finishes.values())
                    ));
                }
            }

            EvgeniumSpeedRun.LOGGER.info(
                reconnected ? "Lobby participant reconnected: {}" : "Lobby participant connected: {}",
                playerName
            );
            broadcastSnapshot();

            while (running && !socket.isClosed()) {
                byte message = in.readByte();
                if (message == LobbyProtocol.LEAVE) {
                    break;
                }
                if (message == LobbyProtocol.PING) {
                    LobbyProtocol.PingPayload ping = LobbyProtocol.readPing(in);
                    long hostReceive = System.currentTimeMillis();
                    participant.lastHeartbeatNano = System.nanoTime();
                    peer.sendPong(ping, hostReceive, System.currentTimeMillis());
                    continue;
                }
                if (message == LobbyProtocol.READY) {
                    participant.ready = true;
                    maybeStartCountdown();
                    continue;
                }
                if (message == LobbyProtocol.FINISH) {
                    handleFinish(participant.playerName, LobbyProtocol.readFinish(in));
                    continue;
                }
                if (message == LobbyProtocol.ADVANCEMENT) {
                    handleAdvancement(participant.playerName, LobbyProtocol.readAdvancement(in));
                    continue;
                }
                throw new IOException("Неизвестное сообщение клиента: " + message);
            }
        } catch (SocketTimeoutException exception) {
            if (running) {
                EvgeniumSpeedRun.LOGGER.info("Lobby heartbeat timeout: {}", playerName);
            }
        } catch (EOFException ignored) {
            // Network changes and normal closes arrive here.
        } catch (IOException exception) {
            if (running) {
                EvgeniumSpeedRun.LOGGER.info("Lobby peer disconnected: {}: {}", playerName, exception.getMessage());
            }
        } finally {
            if (participant != null && peer != null) {
                synchronized (this) {
                    if (participant.peer == peer) {
                        participant.peer = null;
                        participant.connected = false;
                        if (preparingRun && !goIssued) {
                            participant.ready = false;
                        }
                        if (currentRunConfig == null && !preparingRun && !goIssued) {
                            participants.remove(participant.playerName);
                        }
                    }
                }
                broadcastSnapshot();
                maybeStartCountdown();
            }
        }
    }

    private void handleSpectatorSource(Socket source, String spectatorName, String targetName) {
        try {
            synchronized (this) {
                if (!goIssued || !finishes.containsKey(spectatorName)) {
                    sendTunnelError(source, "Наблюдать могут только уже финишировавшие игроки");
                    return;
                }
                if (finishes.containsKey(targetName)) {
                    sendTunnelError(source, "Этот игрок уже финишировал");
                    return;
                }
                if (!isParticipant(targetName)) {
                    sendTunnelError(source, "Игрок не найден в матче");
                    return;
                }
            }

            long tunnelId = nextTunnelId.getAndIncrement();
            PendingTunnel pending = new PendingTunnel(source);
            tunnels.put(tunnelId, pending);

            if (targetName.equals(hostPlayer.name())) {
                localTunnelConsumer.accept(tunnelId);
            } else {
                Peer target = findPeer(targetName);
                if (target == null) {
                    tunnels.remove(tunnelId);
                    sendTunnelError(source, "У игрока сейчас нет связи с хостом");
                    return;
                }
                target.sendOpenTunnel(tunnelId);
            }

            Socket targetSocket = pending.target.get(TUNNEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            tunnels.remove(tunnelId);
            EvgeniumSpeedRun.LOGGER.info("Spectator tunnel {}: {} -> {}", tunnelId, spectatorName, targetName);
            relay(source, targetSocket);
        } catch (Exception exception) {
            EvgeniumSpeedRun.LOGGER.info("Spectator tunnel failed: {}", exception.getMessage());
            closeQuietly(source);
        }
    }

    private void handleSpectatorTarget(Socket target, long tunnelId) {
        PendingTunnel pending = tunnels.get(tunnelId);
        if (pending == null || !pending.target.complete(target)) {
            closeQuietly(target);
        }
    }

    private void sendTunnelError(Socket socket, String message) {
        try (socket; DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            LobbyProtocol.writeError(out, message);
        } catch (IOException ignored) {
        }
    }

    private synchronized boolean isParticipant(String name) {
        return hostPlayer.name().equals(name) || participants.containsKey(name);
    }

    private synchronized Peer findPeer(String name) {
        Participant participant = participants.get(name);
        return participant == null ? null : participant.peer;
    }

    synchronized boolean setGoal(SpeedrunGoal newGoal) {
        if (preparingRun || goIssued) {
            return false;
        }
        this.goal = newGoal;
        broadcastSnapshot();
        return true;
    }

    synchronized boolean setCheatsEnabled(boolean enabled) {
        if (preparingRun || goIssued) {
            return false;
        }
        this.cheatsEnabled = enabled;
        broadcastSnapshot();
        return true;
    }

    synchronized void startRun(LobbyRunConfig config) {
        preparingRun = true;
        hostReady = false;
        goIssued = false;
        goAtEpochMillis = -1L;
        currentRunConfig = config;
        finishes.clear();
        for (Participant participant : participants.values()) {
            participant.ready = false;
        }

        for (Participant participant : participants.values()) {
            Peer peer = participant.peer;
            if (peer == null) {
                continue;
            }
            try {
                peer.sendRun(config);
            } catch (IOException exception) {
                closeQuietly(peer.socket);
            }
        }
        runConsumer.accept(config);
    }

    synchronized void markHostReady() {
        if (!preparingRun) {
            return;
        }
        hostReady = true;
        maybeStartCountdown();
    }

    synchronized void markHostFinished(long elapsedMillis) {
        handleFinish(hostPlayer.name(), elapsedMillis);
    }

    synchronized void markHostAdvancement(String titleKey, String fallbackTitle) {
        if (!goIssued || finishes.containsKey(hostPlayer.name())) {
            return;
        }
        broadcastAdvancement(new LobbyAdvancement(hostPlayer.name(), titleKey, fallbackTitle));
    }

    private synchronized void maybeStartCountdown() {
        if (!preparingRun || !hostReady || goIssued) {
            return;
        }
        long nowNano = System.nanoTime();
        for (Participant participant : participants.values()) {
            if (!participant.connected || participant.peer == null || !participant.ready) {
                return;
            }
            if (nowNano - participant.lastHeartbeatNano > RECENT_HEARTBEAT_NANOS) {
                return;
            }
        }

        preparingRun = false;
        goIssued = true;
        goAtEpochMillis = System.currentTimeMillis() + COUNTDOWN_MILLIS;
        EvgeniumSpeedRun.LOGGER.info("All runners READY; synchronized GO at {}", goAtEpochMillis);

        for (Participant participant : participants.values()) {
            Peer peer = participant.peer;
            if (peer == null) {
                continue;
            }
            try {
                peer.sendGo(goAtEpochMillis);
            } catch (IOException exception) {
                closeQuietly(peer.socket);
            }
        }
        goConsumer.accept(goAtEpochMillis);
    }

    private synchronized void handleFinish(String playerName, long elapsedMillis) {
        if (!goIssued || finishes.containsKey(playerName)) {
            return;
        }

        int place = finishes.size() + 1;
        int totalPlayers = participants.size() + 1;
        LobbyRaceResult result = new LobbyRaceResult(playerName, place, elapsedMillis, totalPlayers);
        finishes.put(playerName, result);
        EvgeniumSpeedRun.LOGGER.info("Runner finished: {} place={} time={}ms", playerName, place, elapsedMillis);

        for (Participant participant : participants.values()) {
            Peer peer = participant.peer;
            if (peer == null) {
                continue;
            }
            try {
                peer.sendResult(result);
            } catch (IOException exception) {
                closeQuietly(peer.socket);
            }
        }
        resultConsumer.accept(result);
    }

    private synchronized void handleAdvancement(String playerName, LobbyProtocol.AdvancementPayload payload) {
        if (!goIssued || finishes.containsKey(playerName)) {
            return;
        }
        broadcastAdvancement(new LobbyAdvancement(playerName, payload.titleKey(), payload.fallbackTitle()));
    }

    private synchronized void broadcastAdvancement(LobbyAdvancement advancement) {
        EvgeniumSpeedRun.LOGGER.info("Runner advancement: {} -> {}", advancement.playerName(), advancement.fallbackTitle());
        for (Participant participant : participants.values()) {
            Peer peer = participant.peer;
            if (peer == null) {
                continue;
            }
            try {
                peer.sendAdvancement(advancement);
            } catch (IOException exception) {
                closeQuietly(peer.socket);
            }
        }
        advancementConsumer.accept(advancement);
    }

    private synchronized LobbySnapshot snapshot() {
        List<LobbyPlayer> players = new ArrayList<>(participants.size() + 1);
        players.add(hostPlayer);
        for (Participant participant : participants.values()) {
            players.add(new LobbyPlayer(participant.playerName, false, participant.connected));
        }
        return new LobbySnapshot(players, cheatsEnabled, goal);
    }

    private void publishSnapshot() {
        snapshotConsumer.accept(snapshot());
    }

    private synchronized void broadcastSnapshot() {
        LobbySnapshot snapshot = snapshot();
        snapshotConsumer.accept(snapshot);
        for (Participant participant : participants.values()) {
            Peer peer = participant.peer;
            if (peer == null) {
                continue;
            }
            try {
                peer.send(snapshot);
            } catch (IOException exception) {
                closeQuietly(peer.socket);
            }
        }
    }

    private static void relay(Socket left, Socket right) throws IOException {
        left.setTcpNoDelay(true);
        right.setTcpNoDelay(true);
        Thread reverse = new Thread(() -> copy(right, left), "Evgenium-Spectator-Relay-Reverse");
        reverse.setDaemon(true);
        reverse.start();
        copy(left, right);
    }

    private static void copy(Socket from, Socket to) {
        try (from; to; InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        for (Participant participant : participants.values()) {
            if (participant.peer != null) {
                closeQuietly(participant.peer.socket);
            }
        }
        participants.clear();
        for (PendingTunnel pending : tunnels.values()) {
            closeQuietly(pending.source);
            pending.target.thenAccept(LobbyHost::closeQuietly);
        }
        tunnels.clear();
    }

    private static final class PendingTunnel {
        private final Socket source;
        private final CompletableFuture<Socket> target = new CompletableFuture<>();

        private PendingTunnel(Socket source) {
            this.source = source;
        }
    }

    private static final class Participant {
        private final String playerName;
        private final String reconnectToken;
        private volatile Peer peer;
        private volatile boolean connected;
        private volatile boolean ready;
        private volatile long lastHeartbeatNano;

        private Participant(String playerName, String reconnectToken) {
            this.playerName = playerName;
            this.reconnectToken = reconnectToken;
        }
    }

    private static final class Peer {
        private final Participant participant;
        private final Socket socket;
        private final DataOutputStream out;

        private Peer(Participant participant, Socket socket, DataOutputStream out) {
            this.participant = participant;
            this.socket = socket;
            this.out = out;
        }

        private synchronized void sendSession(boolean reconnected) throws IOException {
            LobbyProtocol.writeSession(out, participant.reconnectToken, reconnected);
        }

        private synchronized void send(LobbySnapshot snapshot) throws IOException {
            LobbyProtocol.writeState(out, snapshot);
        }

        private synchronized void sendRun(LobbyRunConfig config) throws IOException {
            LobbyProtocol.writeStartRun(out, config);
        }

        private synchronized void sendResume(LobbyResumeState state) throws IOException {
            LobbyProtocol.writeResumeRun(out, state);
        }

        private synchronized void sendGo(long startAtEpochMillis) throws IOException {
            LobbyProtocol.writeGo(out, startAtEpochMillis);
        }

        private synchronized void sendResult(LobbyRaceResult result) throws IOException {
            LobbyProtocol.writeFinishUpdate(out, result);
        }

        private synchronized void sendAdvancement(LobbyAdvancement advancement) throws IOException {
            LobbyProtocol.writeAdvancementBroadcast(out, advancement);
        }

        private synchronized void sendOpenTunnel(long tunnelId) throws IOException {
            LobbyProtocol.writeOpenSpectatorTunnel(out, tunnelId);
        }

        private synchronized void sendPong(LobbyProtocol.PingPayload ping, long hostReceive, long hostSend) throws IOException {
            LobbyProtocol.writePong(out, ping, hostReceive, hostSend);
        }
    }
}
