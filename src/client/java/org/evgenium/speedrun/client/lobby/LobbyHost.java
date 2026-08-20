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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

final class LobbyHost implements AutoCloseable {
    private static final long COUNTDOWN_MILLIS = 4000L;
    private static final long TUNNEL_TIMEOUT_SECONDS = 12L;

    private final int port;
    private final LobbyPlayer hostPlayer;
    private volatile boolean cheatsEnabled;
    private final Consumer<LobbySnapshot> snapshotConsumer;
    private final Consumer<LobbyRunConfig> runConsumer;
    private final LongConsumer goConsumer;
    private final Consumer<LobbyRaceResult> resultConsumer;
    private final Consumer<LobbyAdvancement> advancementConsumer;
    private final LongConsumer localTunnelConsumer;
    private final CopyOnWriteArrayList<Peer> peers = new CopyOnWriteArrayList<>();
    private final Map<String, LobbyRaceResult> finishes = new LinkedHashMap<>();
    private final ConcurrentHashMap<Long, PendingTunnel> tunnels = new ConcurrentHashMap<>();
    private final AtomicLong nextTunnelId = new AtomicLong(1L);
    private volatile boolean running;
    private volatile boolean preparingRun;
    private volatile boolean hostReady;
    private volatile boolean goIssued;
    private volatile SpeedrunGoal goal = SpeedrunGoal.COMPLETE_MINECRAFT;
    private ServerSocket serverSocket;

    LobbyHost(int port, String hostName, boolean cheatsEnabled, Consumer<LobbySnapshot> snapshotConsumer,
              Consumer<LobbyRunConfig> runConsumer, LongConsumer goConsumer,
              Consumer<LobbyRaceResult> resultConsumer, Consumer<LobbyAdvancement> advancementConsumer,
              LongConsumer localTunnelConsumer) {
        this.port = port;
        this.hostPlayer = new LobbyPlayer(hostName, true);
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
                handleControl(socket, in, hello.playerName());
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

    private void handleControl(Socket socket, DataInputStream in, String playerName) {
        Peer peer = null;
        try (socket; DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            if (preparingRun || goIssued) {
                LobbyProtocol.writeError(out, "Забег уже запущен");
                return;
            }

            peer = new Peer(playerName, socket, out);
            peers.add(peer);
            broadcastSnapshot();

            while (running && !socket.isClosed()) {
                byte message = in.readByte();
                if (message == LobbyProtocol.LEAVE) {
                    break;
                }
                if (message == LobbyProtocol.READY) {
                    peer.ready = true;
                    maybeStartCountdown();
                    continue;
                }
                if (message == LobbyProtocol.FINISH) {
                    handleFinish(peer.playerName, LobbyProtocol.readFinish(in));
                    continue;
                }
                if (message == LobbyProtocol.ADVANCEMENT) {
                    handleAdvancement(peer.playerName, LobbyProtocol.readAdvancement(in));
                    continue;
                }
                throw new IOException("Неизвестное сообщение клиента: " + message);
            }
        } catch (EOFException ignored) {
            // Normal disconnect.
        } catch (IOException exception) {
            if (running) {
                EvgeniumSpeedRun.LOGGER.info("Lobby peer disconnected: {}", exception.getMessage());
            }
        } finally {
            if (peer != null) {
                peers.remove(peer);
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
                    sendTunnelError(source, "Игрок отключился");
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

    private boolean isParticipant(String name) {
        if (hostPlayer.name().equals(name)) {
            return true;
        }
        return findPeer(name) != null;
    }

    private Peer findPeer(String name) {
        for (Peer peer : peers) {
            if (peer.playerName.equals(name)) {
                return peer;
            }
        }
        return null;
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
        finishes.clear();
        for (Peer peer : peers) {
            peer.ready = false;
        }

        for (Peer peer : peers) {
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
        if (!preparingRun || !hostReady) {
            return;
        }
        for (Peer peer : peers) {
            if (!peer.ready) {
                return;
            }
        }

        preparingRun = false;
        goIssued = true;
        long startAtEpochMillis = System.currentTimeMillis() + COUNTDOWN_MILLIS;
        EvgeniumSpeedRun.LOGGER.info("All runners READY; synchronized GO at {}", startAtEpochMillis);

        for (Peer peer : peers) {
            try {
                peer.sendGo(startAtEpochMillis);
            } catch (IOException exception) {
                closeQuietly(peer.socket);
            }
        }
        goConsumer.accept(startAtEpochMillis);
    }

    private synchronized void handleFinish(String playerName, long elapsedMillis) {
        if (!goIssued || finishes.containsKey(playerName)) {
            return;
        }

        int place = finishes.size() + 1;
        int totalPlayers = peers.size() + 1;
        LobbyRaceResult result = new LobbyRaceResult(playerName, place, elapsedMillis, totalPlayers);
        finishes.put(playerName, result);
        EvgeniumSpeedRun.LOGGER.info("Runner finished: {} place={} time={}ms", playerName, place, elapsedMillis);

        for (Peer peer : peers) {
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

    private void broadcastAdvancement(LobbyAdvancement advancement) {
        EvgeniumSpeedRun.LOGGER.info("Runner advancement: {} -> {}", advancement.playerName(), advancement.fallbackTitle());
        for (Peer peer : peers) {
            try {
                peer.sendAdvancement(advancement);
            } catch (IOException exception) {
                closeQuietly(peer.socket);
            }
        }
        advancementConsumer.accept(advancement);
    }

    private LobbySnapshot snapshot() {
        List<LobbyPlayer> players = new ArrayList<>(peers.size() + 1);
        players.add(hostPlayer);
        for (Peer peer : peers) {
            players.add(new LobbyPlayer(peer.playerName, false));
        }
        return new LobbySnapshot(players, cheatsEnabled, goal);
    }

    private void publishSnapshot() {
        snapshotConsumer.accept(snapshot());
    }

    private void broadcastSnapshot() {
        LobbySnapshot snapshot = snapshot();
        snapshotConsumer.accept(snapshot);
        for (Peer peer : peers) {
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
    public void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        for (Peer peer : peers) {
            closeQuietly(peer.socket);
        }
        peers.clear();
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

    private static final class Peer {
        private final String playerName;
        private final Socket socket;
        private final DataOutputStream out;
        private volatile boolean ready;

        private Peer(String playerName, Socket socket, DataOutputStream out) {
            this.playerName = playerName;
            this.socket = socket;
            this.out = out;
        }

        private synchronized void send(LobbySnapshot snapshot) throws IOException {
            LobbyProtocol.writeState(out, snapshot);
        }

        private synchronized void sendRun(LobbyRunConfig config) throws IOException {
            LobbyProtocol.writeStartRun(out, config);
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
    }
}
