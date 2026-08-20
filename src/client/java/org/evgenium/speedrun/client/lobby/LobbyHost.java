package org.evgenium.speedrun.client.lobby;

import org.evgenium.speedrun.EvgeniumSpeedRun;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

final class LobbyHost implements AutoCloseable {
    private static final long COUNTDOWN_MILLIS = 4000L;

    private final int port;
    private final LobbyPlayer hostPlayer;
    private final boolean cheatsEnabled;
    private final Consumer<LobbySnapshot> snapshotConsumer;
    private final Consumer<LobbyRunConfig> runConsumer;
    private final LongConsumer goConsumer;
    private final Consumer<LobbyRaceResult> resultConsumer;
    private final CopyOnWriteArrayList<Peer> peers = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private volatile boolean preparingRun;
    private volatile boolean hostReady;
    private volatile boolean goIssued;
    private volatile boolean raceFinished;
    private volatile SpeedrunGoal goal = SpeedrunGoal.COMPLETE_MINECRAFT;
    private ServerSocket serverSocket;

    LobbyHost(int port, String hostName, boolean cheatsEnabled, Consumer<LobbySnapshot> snapshotConsumer,
              Consumer<LobbyRunConfig> runConsumer, LongConsumer goConsumer, Consumer<LobbyRaceResult> resultConsumer) {
        this.port = port;
        this.hostPlayer = new LobbyPlayer(hostName, true);
        this.cheatsEnabled = cheatsEnabled;
        this.snapshotConsumer = snapshotConsumer;
        this.runConsumer = runConsumer;
        this.goConsumer = goConsumer;
        this.resultConsumer = resultConsumer;
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
                Thread peerThread = new Thread(() -> handlePeer(socket), "Evgenium-Lobby-Peer");
                peerThread.setDaemon(true);
                peerThread.start();
            } catch (SocketException exception) {
                if (running) {
                    EvgeniumSpeedRun.LOGGER.warn("Lobby accept socket failed", exception);
                }
            } catch (IOException exception) {
                EvgeniumSpeedRun.LOGGER.warn("Lobby accept failed", exception);
            }
        }
    }

    private void handlePeer(Socket socket) {
        Peer peer = null;
        try (socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            String playerName = LobbyProtocol.readHello(in);
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

    synchronized boolean setGoal(SpeedrunGoal newGoal) {
        if (preparingRun || goIssued) {
            return false;
        }
        this.goal = newGoal;
        broadcastSnapshot();
        return true;
    }

    synchronized void startRun(LobbyRunConfig config) {
        preparingRun = true;
        hostReady = false;
        goIssued = false;
        raceFinished = false;
        for (Peer peer : peers) {
            peer.ready = false;
        }

        for (Peer peer : peers) {
            try {
                peer.sendRun(config);
            } catch (IOException exception) {
                try {
                    peer.socket.close();
                } catch (IOException ignored) {
                }
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
                try {
                    peer.socket.close();
                } catch (IOException ignored) {
                }
            }
        }
        goConsumer.accept(startAtEpochMillis);
    }

    private synchronized void handleFinish(String winnerName, long elapsedMillis) {
        if (!goIssued || raceFinished) {
            return;
        }
        raceFinished = true;
        LobbyRaceResult result = new LobbyRaceResult(winnerName, elapsedMillis);
        EvgeniumSpeedRun.LOGGER.info("Race finished: {} won in {} ms", winnerName, elapsedMillis);

        for (Peer peer : peers) {
            try {
                peer.sendResult(result);
            } catch (IOException exception) {
                try {
                    peer.socket.close();
                } catch (IOException ignored) {
                }
            }
        }
        resultConsumer.accept(result);
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
                try {
                    peer.socket.close();
                } catch (IOException ignored) {
                }
            }
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
            try {
                peer.socket.close();
            } catch (IOException ignored) {
            }
        }
        peers.clear();
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
            LobbyProtocol.writeRaceFinished(out, result);
        }
    }
}
