package org.evgenium.speedrun.client.lobby;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

final class LobbyClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final String playerName;
    private final Consumer<LobbySnapshot> snapshotConsumer;
    private final Consumer<String> statusConsumer;
    private volatile boolean running;
    private Socket socket;
    private DataOutputStream out;

    LobbyClient(String host, int port, String playerName, Consumer<LobbySnapshot> snapshotConsumer, Consumer<String> statusConsumer) {
        this.host = host;
        this.port = port;
        this.playerName = playerName;
        this.snapshotConsumer = snapshotConsumer;
        this.statusConsumer = statusConsumer;
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

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(newSocket.getInputStream()))) {
                this.out = new DataOutputStream(new BufferedOutputStream(newSocket.getOutputStream()));
                LobbyProtocol.writeHello(out, playerName);
                statusConsumer.accept("Подключено");

                while (running && !newSocket.isClosed()) {
                    byte message = in.readByte();
                    if (message == LobbyProtocol.STATE) {
                        snapshotConsumer.accept(LobbyProtocol.readState(in));
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
