package org.evgenium.speedrun.client.lobby;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

final class LobbyClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final String playerName;
    private final Consumer<LobbySnapshot> snapshotConsumer;
    private final Consumer<LobbyRunConfig> runConsumer;
    private final LongConsumer goConsumer;
    private final Consumer<LobbyRaceResult> resultConsumer;
    private final Consumer<LobbyAdvancement> advancementConsumer;
    private final LongConsumer tunnelRequestConsumer;
    private final Consumer<String> statusConsumer;
    private volatile boolean running;
    private Socket socket;
    private DataOutputStream out;

    LobbyClient(String host, int port, String playerName, Consumer<LobbySnapshot> snapshotConsumer,
                Consumer<LobbyRunConfig> runConsumer, LongConsumer goConsumer,
                Consumer<LobbyRaceResult> resultConsumer, Consumer<LobbyAdvancement> advancementConsumer,
                LongConsumer tunnelRequestConsumer, Consumer<String> statusConsumer) {
        this.host = host;
        this.port = port;
        this.playerName = playerName;
        this.snapshotConsumer = snapshotConsumer;
        this.runConsumer = runConsumer;
        this.goConsumer = goConsumer;
        this.resultConsumer = resultConsumer;
        this.advancementConsumer = advancementConsumer;
        this.tunnelRequestConsumer = tunnelRequestConsumer;
        this.statusConsumer = statusConsumer;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    void startAsync() {
        running = true;
        Thread thread = new Thread(this::connectAndRead, "Evgenium-Lobby-Client");
        thread.setDaemon(true);
        thread.start();
    }

    private void connectAndRead() {
        statusConsumer.accept("Подключение к " + host + ":" + port + "...");
        try {
            Socket newSocket = new Socket();
            newSocket.connect(new InetSocketAddress(host, port), 5000);
            newSocket.setTcpNoDelay(true);
            this.socket = newSocket;

            try (DataInputStream in = new DataInputStream(newSocket.getInputStream())) {
                this.out = new DataOutputStream(newSocket.getOutputStream());
                LobbyProtocol.writeHello(out, playerName);
                statusConsumer.accept("Подключено");

                while (running && !newSocket.isClosed()) {
                    byte message = in.readByte();
                    if (message == LobbyProtocol.STATE) {
                        snapshotConsumer.accept(LobbyProtocol.readState(in));
                    } else if (message == LobbyProtocol.START_RUN) {
                        runConsumer.accept(LobbyProtocol.readStartRun(in));
                    } else if (message == LobbyProtocol.GO) {
                        goConsumer.accept(LobbyProtocol.readGo(in));
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
        } catch (EOFException exception) {
            if (running) {
                statusConsumer.accept("Соединение закрыто хозяином");
            }
        } catch (IOException exception) {
            if (running) {
                statusConsumer.accept("Ошибка: " + exception.getMessage());
            }
        } finally {
            closeSocket();
        }
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
    }

    private void closeSocket() {
        Socket current = this.socket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
    }
}
