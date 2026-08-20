package org.evgenium.speedrun.client.lobby;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

final class LobbyClient implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 5_500;
    private static final long HEARTBEAT_INTERVAL_MILLIS = 1_000L;

    private final String host;
    private final int port;
    private final String playerName;
    private final Consumer<LobbySnapshot> snapshotConsumer;
    private final Consumer<LobbyRunConfig> runConsumer;
    private final Consumer<LobbyResumeState> resumeConsumer;
    private final LongConsumer goConsumer;
    private final Consumer<LobbyRaceResult> resultConsumer;
    private final Consumer<LobbyAdvancement> advancementConsumer;
    private final LongConsumer tunnelRequestConsumer;
    private final Consumer<String> statusConsumer;
    private final Consumer<Boolean> connectionConsumer;
    private final Consumer<LobbyTimeSample> timeSampleConsumer;
    private final AtomicLong nextPingId = new AtomicLong(1L);

    private volatile boolean running;
    private volatile String reconnectToken = "";
    private volatile Socket socket;
    private volatile DataOutputStream out;
    private volatile Thread connectionThread;
    private volatile boolean everConnected;

    LobbyClient(String host, int port, String playerName, Consumer<LobbySnapshot> snapshotConsumer,
                Consumer<LobbyRunConfig> runConsumer, Consumer<LobbyResumeState> resumeConsumer,
                LongConsumer goConsumer, Consumer<LobbyRaceResult> resultConsumer,
                Consumer<LobbyAdvancement> advancementConsumer, LongConsumer tunnelRequestConsumer,
                Consumer<String> statusConsumer, Consumer<Boolean> connectionConsumer,
                Consumer<LobbyTimeSample> timeSampleConsumer) {
        this.host = host;
        this.port = port;
        this.playerName = playerName;
        this.snapshotConsumer = snapshotConsumer;
        this.runConsumer = runConsumer;
        this.resumeConsumer = resumeConsumer;
        this.goConsumer = goConsumer;
        this.resultConsumer = resultConsumer;
        this.advancementConsumer = advancementConsumer;
        this.tunnelRequestConsumer = tunnelRequestConsumer;
        this.statusConsumer = statusConsumer;
        this.connectionConsumer = connectionConsumer;
        this.timeSampleConsumer = timeSampleConsumer;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    void startAsync() {
        running = true;
        Thread thread = new Thread(this::connectionLoop, "Evgenium-Lobby-Client");
        thread.setDaemon(true);
        connectionThread = thread;
        thread.start();
    }

    private void connectionLoop() {
        int attempt = 0;
        while (running) {
            attempt++;
            try {
                connectAndRead(attempt);
            } catch (EOFException exception) {
                if (running) {
                    statusConsumer.accept("Связь с хостом закрыта. Переподключение...");
                }
            } catch (SocketTimeoutException exception) {
                if (running) {
                    statusConsumer.accept("Heartbeat timeout. Переподключение...");
                }
            } catch (IOException exception) {
                if (running) {
                    statusConsumer.accept("Связь потеряна: " + exception.getMessage() + " • переподключение...");
                }
            } finally {
                closeSocket();
                out = null;
                if (running) {
                    connectionConsumer.accept(false);
                }
            }

            if (!running) {
                break;
            }

            long delay = Math.min(5_000L, 750L + Math.min(attempt, 6) * 500L);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
                // forceReconnect() intentionally wakes the retry loop.
            }
        }
    }

    private void connectAndRead(int attempt) throws IOException {
        statusConsumer.accept((everConnected ? "Переподключение" : "Подключение")
            + " к " + host + ":" + port + " (попытка " + attempt + ")...");

        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        newSocket.setTcpNoDelay(true);
        newSocket.setSoTimeout(READ_TIMEOUT_MILLIS);
        this.socket = newSocket;

        try (DataInputStream in = new DataInputStream(newSocket.getInputStream())) {
            DataOutputStream newOut = new DataOutputStream(newSocket.getOutputStream());
            this.out = newOut;
            LobbyProtocol.writeHello(newOut, playerName, reconnectToken);
            startHeartbeat(newSocket, newOut);

            while (running && socket == newSocket && !newSocket.isClosed()) {
                byte message = in.readByte();
                if (message == LobbyProtocol.SESSION) {
                    LobbyProtocol.SessionPayload session = LobbyProtocol.readSession(in);
                    reconnectToken = session.reconnectToken();
                    everConnected = true;
                    connectionConsumer.accept(true);
                    statusConsumer.accept(session.reconnected() ? "Переподключено. Синхронизация матча..." : "Подключено");
                } else if (message == LobbyProtocol.STATE) {
                    snapshotConsumer.accept(LobbyProtocol.readState(in));
                } else if (message == LobbyProtocol.START_RUN) {
                    runConsumer.accept(LobbyProtocol.readStartRun(in));
                } else if (message == LobbyProtocol.RESUME_RUN) {
                    resumeConsumer.accept(LobbyProtocol.readResumeRun(in));
                } else if (message == LobbyProtocol.GO) {
                    goConsumer.accept(LobbyProtocol.readGo(in));
                } else if (message == LobbyProtocol.PONG) {
                    LobbyProtocol.PongPayload pong = LobbyProtocol.readPong(in);
                    timeSampleConsumer.accept(new LobbyTimeSample(
                        pong.clientSendEpochMillis(),
                        pong.clientSendNano(),
                        pong.hostReceiveEpochMillis(),
                        pong.hostSendEpochMillis()
                    ));
                } else if (message == LobbyProtocol.FINISH_UPDATE) {
                    resultConsumer.accept(LobbyProtocol.readFinishUpdate(in));
                } else if (message == LobbyProtocol.ADVANCEMENT_BROADCAST) {
                    advancementConsumer.accept(LobbyProtocol.readAdvancementBroadcast(in));
                } else if (message == LobbyProtocol.OPEN_SPECTATOR_TUNNEL) {
                    tunnelRequestConsumer.accept(LobbyProtocol.readOpenSpectatorTunnel(in));
                } else if (message == LobbyProtocol.ERROR) {
                    throw new IOException(in.readUTF());
                } else {
                    throw new IOException("Неизвестное сообщение лобби: " + message);
                }
            }
        }
    }

    private void startHeartbeat(Socket connection, DataOutputStream stream) {
        Thread heartbeat = new Thread(() -> {
            while (running && socket == connection && !connection.isClosed()) {
                try {
                    long pingId = nextPingId.getAndIncrement();
                    long epochMillis = System.currentTimeMillis();
                    long nano = System.nanoTime();
                    synchronized (stream) {
                        LobbyProtocol.writePing(stream, pingId, epochMillis, nano);
                    }
                    Thread.sleep(HEARTBEAT_INTERVAL_MILLIS);
                } catch (InterruptedException ignored) {
                    return;
                } catch (IOException exception) {
                    closeQuietly(connection);
                    return;
                }
            }
        }, "Evgenium-Lobby-Heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    void sendReady() {
        DataOutputStream stream = this.out;
        if (stream == null) {
            return;
        }
        try {
            synchronized (stream) {
                LobbyProtocol.writeReady(stream);
            }
            statusConsumer.accept("Мир готов. Ждём остальных игроков");
        } catch (IOException exception) {
            statusConsumer.accept("Ошибка READY: " + exception.getMessage());
            forceReconnect();
        }
    }

    void sendFinish(long elapsedMillis) {
        DataOutputStream stream = this.out;
        if (stream == null) {
            return;
        }
        try {
            synchronized (stream) {
                LobbyProtocol.writeFinish(stream, elapsedMillis);
            }
        } catch (IOException exception) {
            statusConsumer.accept("Ошибка FINISH: " + exception.getMessage());
            forceReconnect();
        }
    }

    void sendAdvancement(String titleKey, String fallbackTitle) {
        DataOutputStream stream = this.out;
        if (stream == null) {
            return;
        }
        try {
            synchronized (stream) {
                LobbyProtocol.writeAdvancement(stream, titleKey, fallbackTitle);
            }
        } catch (IOException exception) {
            statusConsumer.accept("Ошибка ADVANCEMENT: " + exception.getMessage());
            forceReconnect();
        }
    }

    void forceReconnect() {
        closeSocket();
        Thread thread = connectionThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void close() {
        running = false;
        DataOutputStream stream = this.out;
        if (stream != null) {
            try {
                synchronized (stream) {
                    stream.writeByte(LobbyProtocol.LEAVE);
                    stream.flush();
                }
            } catch (IOException ignored) {
            }
        }
        closeSocket();
        Thread thread = connectionThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void closeSocket() {
        Socket current = this.socket;
        if (current != null) {
            closeQuietly(current);
            if (socket == current) {
                socket = null;
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
