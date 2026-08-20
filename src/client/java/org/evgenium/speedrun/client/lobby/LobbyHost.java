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

final class LobbyHost implements AutoCloseable {
    private final int port;
    private final LobbyPlayer hostPlayer;
    private final Consumer<LobbySnapshot> snapshotConsumer;
    private final CopyOnWriteArrayList<Peer> peers = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private ServerSocket serverSocket;

    LobbyHost(int port, String hostName, Consumer<LobbySnapshot> snapshotConsumer) {
        this.port = port;
        this.hostPlayer = new LobbyPlayer(hostName, true);
        this.snapshotConsumer = snapshotConsumer;
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
            peer = new Peer(playerName, socket, out);
            peers.add(peer);
            broadcastSnapshot();

            while (running && !socket.isClosed()) {
                byte message = in.readByte();
                if (message == LobbyProtocol.LEAVE) {
                    break;
                }
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
            }
        }
    }

    private LobbySnapshot snapshot() {
        List<LobbyPlayer> players = new ArrayList<>(peers.size() + 1);
        players.add(hostPlayer);
        for (Peer peer : peers) {
            players.add(new LobbyPlayer(peer.playerName, false));
        }
        return new LobbySnapshot(players);
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

        private Peer(String playerName, Socket socket, DataOutputStream out) {
            this.playerName = playerName;
            this.socket = socket;
            this.out = out;
        }

        private synchronized void send(LobbySnapshot snapshot) throws IOException {
            LobbyProtocol.writeState(out, snapshot);
        }
    }
}
