package org.evgenium.speedrun.client.lobby;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class LobbyProtocol {
    static final int MAGIC = 0x45565352; // EVSR
    static final int VERSION = 10;

    static final byte CHANNEL_CONTROL = 1;
    static final byte CHANNEL_SPECTATOR_SOURCE = 2;
    static final byte CHANNEL_SPECTATOR_TARGET = 3;

    static final byte STATE = 10;
    static final byte ERROR = 11;
    static final byte START_RUN = 12;
    static final byte GO = 13;
    static final byte FINISH_UPDATE = 14;
    static final byte OPEN_SPECTATOR_TUNNEL = 15;
    static final byte ADVANCEMENT_BROADCAST = 16;
    static final byte SESSION = 17;
    static final byte PONG = 18;
    static final byte RESUME_RUN = 19;

    static final byte LEAVE = 20;
    static final byte READY = 21;
    static final byte FINISH = 22;
    static final byte ADVANCEMENT = 23;
    static final byte PING = 24;

    private LobbyProtocol() {
    }

    static void writeHello(DataOutputStream out, String playerName, String reconnectToken) throws IOException {
        writeHeader(out, CHANNEL_CONTROL);
        out.writeUTF(playerName);
        out.writeUTF(reconnectToken == null ? "" : reconnectToken);
        out.flush();
    }

    static void writeSpectatorSourceHello(DataOutputStream out, String spectatorName, String targetName) throws IOException {
        writeHeader(out, CHANNEL_SPECTATOR_SOURCE);
        out.writeUTF(spectatorName);
        out.writeUTF(targetName);
        out.flush();
    }

    static void writeSpectatorTargetHello(DataOutputStream out, long tunnelId) throws IOException {
        writeHeader(out, CHANNEL_SPECTATOR_TARGET);
        out.writeLong(tunnelId);
        out.flush();
    }

    private static void writeHeader(DataOutputStream out, byte channel) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeByte(channel);
    }

    static ConnectionHello readConnectionHello(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("Это не Evgenium SpeedRun соединение");
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("Несовместимая версия протокола: " + version);
        }
        byte channel = in.readByte();
        if (channel == CHANNEL_CONTROL) {
            String name = validatePlayerName(in.readUTF());
            String reconnectToken = validateText(in.readUTF(), "reconnect token", 160);
            return new ConnectionHello(channel, name, null, -1L, reconnectToken);
        }
        if (channel == CHANNEL_SPECTATOR_SOURCE) {
            String spectatorName = validatePlayerName(in.readUTF());
            String targetName = validatePlayerName(in.readUTF());
            return new ConnectionHello(channel, spectatorName, targetName, -1L, "");
        }
        if (channel == CHANNEL_SPECTATOR_TARGET) {
            long tunnelId = in.readLong();
            if (tunnelId <= 0L) {
                throw new IOException("Некорректный tunnel id");
            }
            return new ConnectionHello(channel, null, null, tunnelId, "");
        }
        throw new IOException("Неизвестный тип соединения: " + channel);
    }

    private static String validatePlayerName(String raw) throws IOException {
        String name = raw.trim();
        if (name.isEmpty() || name.length() > 32) {
            throw new IOException("Некорректное имя игрока");
        }
        return name;
    }

    private static String validateText(String raw, String field, int maxLength) throws IOException {
        String text = raw == null ? "" : raw.trim();
        if (text.length() > maxLength) {
            throw new IOException("Слишком длинное поле " + field);
        }
        return text;
    }

    static void writeSession(DataOutputStream out, String reconnectToken, boolean reconnected) throws IOException {
        out.writeByte(SESSION);
        out.writeUTF(reconnectToken);
        out.writeBoolean(reconnected);
        out.flush();
    }

    static SessionPayload readSession(DataInputStream in) throws IOException {
        String token = validateText(in.readUTF(), "session token", 160);
        if (token.isEmpty()) {
            throw new IOException("Пустой session token");
        }
        return new SessionPayload(token, in.readBoolean());
    }

    static void writeState(DataOutputStream out, LobbySnapshot snapshot) throws IOException {
        out.writeByte(STATE);
        out.writeBoolean(snapshot.cheatsEnabled());
        out.writeUTF(snapshot.goal().id());
        out.writeUTF(snapshot.randomizationType().id());
        out.writeInt(snapshot.players().size());
        for (LobbyPlayer player : snapshot.players()) {
            out.writeUTF(player.name());
            out.writeBoolean(player.host());
            out.writeBoolean(player.connected());
        }
        out.flush();
    }

    static LobbySnapshot readState(DataInputStream in) throws IOException {
        boolean cheatsEnabled = in.readBoolean();
        SpeedrunGoal goal;
        RandomizationType randomizationType;
        try {
            goal = SpeedrunGoal.fromId(in.readUTF());
            randomizationType = RandomizationType.fromId(in.readUTF());
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        int count = in.readInt();
        if (count < 0 || count > 128) {
            throw new IOException("Некорректный размер лобби");
        }
        List<LobbyPlayer> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(new LobbyPlayer(in.readUTF(), in.readBoolean(), in.readBoolean()));
        }
        return new LobbySnapshot(players, cheatsEnabled, goal, randomizationType);
    }

    static void writeStartRun(DataOutputStream out, LobbyRunConfig config) throws IOException {
        out.writeByte(START_RUN);
        writeRunConfig(out, config);
        out.flush();
    }

    static LobbyRunConfig readStartRun(DataInputStream in) throws IOException {
        return readRunConfig(in);
    }

    private static void writeRunConfig(DataOutputStream out, LobbyRunConfig config) throws IOException {
        out.writeLong(config.worldSeed());
        out.writeLong(config.rngSeed());
        out.writeBoolean(config.cheatsEnabled());
        out.writeUTF(config.goal().id());
        out.writeUTF(config.randomizationType().id());
    }

    private static LobbyRunConfig readRunConfig(DataInputStream in) throws IOException {
        long worldSeed = in.readLong();
        long rngSeed = in.readLong();
        boolean cheatsEnabled = in.readBoolean();
        try {
            SpeedrunGoal goal = SpeedrunGoal.fromId(in.readUTF());
            RandomizationType randomizationType = RandomizationType.fromId(in.readUTF());
            return new LobbyRunConfig(worldSeed, rngSeed, cheatsEnabled, goal, randomizationType);
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    static void writeResumeRun(DataOutputStream out, LobbyResumeState state) throws IOException {
        out.writeByte(RESUME_RUN);
        writeRunConfig(out, state.config());
        out.writeBoolean(state.goIssued());
        out.writeLong(state.goAtEpochMillis());
        out.writeInt(state.results().size());
        for (LobbyRaceResult result : state.results()) {
            writeRaceResultBody(out, result);
        }
        out.flush();
    }

    static LobbyResumeState readResumeRun(DataInputStream in) throws IOException {
        LobbyRunConfig config = readRunConfig(in);
        boolean goIssued = in.readBoolean();
        long goAtEpochMillis = in.readLong();
        int count = in.readInt();
        if (count < 0 || count > 128) {
            throw new IOException("Некорректное число результатов при reconnect");
        }
        List<LobbyRaceResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(readRaceResultBody(in));
        }
        return new LobbyResumeState(config, goIssued, goAtEpochMillis, results);
    }

    static void writeReady(DataOutputStream out) throws IOException {
        out.writeByte(READY);
        out.flush();
    }

    static void writeGo(DataOutputStream out, long startAtEpochMillis) throws IOException {
        out.writeByte(GO);
        out.writeLong(startAtEpochMillis);
        out.flush();
    }

    static long readGo(DataInputStream in) throws IOException {
        return in.readLong();
    }

    static void writeFinish(DataOutputStream out, long elapsedMillis) throws IOException {
        out.writeByte(FINISH);
        out.writeLong(elapsedMillis);
        out.flush();
    }

    static long readFinish(DataInputStream in) throws IOException {
        long elapsedMillis = in.readLong();
        if (elapsedMillis < 0L) {
            throw new IOException("Некорректное время финиша");
        }
        return elapsedMillis;
    }

    static void writeFinishUpdate(DataOutputStream out, LobbyRaceResult result) throws IOException {
        out.writeByte(FINISH_UPDATE);
        writeRaceResultBody(out, result);
        out.flush();
    }

    static LobbyRaceResult readFinishUpdate(DataInputStream in) throws IOException {
        return readRaceResultBody(in);
    }

    private static void writeRaceResultBody(DataOutputStream out, LobbyRaceResult result) throws IOException {
        out.writeUTF(result.playerName());
        out.writeInt(result.place());
        out.writeLong(result.elapsedMillis());
        out.writeInt(result.totalPlayers());
    }

    private static LobbyRaceResult readRaceResultBody(DataInputStream in) throws IOException {
        try {
            return new LobbyRaceResult(in.readUTF(), in.readInt(), in.readLong(), in.readInt());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Некорректный результат финиша", exception);
        }
    }

    static void writeAdvancement(DataOutputStream out, String titleKey, String fallbackTitle) throws IOException {
        out.writeByte(ADVANCEMENT);
        out.writeUTF(titleKey == null ? "" : titleKey);
        out.writeUTF(fallbackTitle == null ? "" : fallbackTitle);
        out.flush();
    }

    static AdvancementPayload readAdvancement(DataInputStream in) throws IOException {
        String titleKey = validateText(in.readUTF(), "advancement title key", 256);
        String fallbackTitle = validateText(in.readUTF(), "advancement title", 512);
        if (titleKey.isEmpty() && fallbackTitle.isEmpty()) {
            throw new IOException("Пустое достижение");
        }
        return new AdvancementPayload(titleKey, fallbackTitle);
    }

    static void writeAdvancementBroadcast(DataOutputStream out, LobbyAdvancement advancement) throws IOException {
        out.writeByte(ADVANCEMENT_BROADCAST);
        out.writeUTF(advancement.playerName());
        out.writeUTF(advancement.titleKey());
        out.writeUTF(advancement.fallbackTitle());
        out.flush();
    }

    static LobbyAdvancement readAdvancementBroadcast(DataInputStream in) throws IOException {
        String playerName = validatePlayerName(in.readUTF());
        String titleKey = validateText(in.readUTF(), "advancement title key", 256);
        String fallbackTitle = validateText(in.readUTF(), "advancement title", 512);
        return new LobbyAdvancement(playerName, titleKey, fallbackTitle);
    }

    static void writeOpenSpectatorTunnel(DataOutputStream out, long tunnelId) throws IOException {
        out.writeByte(OPEN_SPECTATOR_TUNNEL);
        out.writeLong(tunnelId);
        out.flush();
    }

    static long readOpenSpectatorTunnel(DataInputStream in) throws IOException {
        long tunnelId = in.readLong();
        if (tunnelId <= 0L) {
            throw new IOException("Некорректный tunnel id");
        }
        return tunnelId;
    }

    static void writePing(DataOutputStream out, long pingId, long clientSendEpochMillis, long clientSendNano) throws IOException {
        out.writeByte(PING);
        out.writeLong(pingId);
        out.writeLong(clientSendEpochMillis);
        out.writeLong(clientSendNano);
        out.flush();
    }

    static PingPayload readPing(DataInputStream in) throws IOException {
        return new PingPayload(in.readLong(), in.readLong(), in.readLong());
    }

    static void writePong(DataOutputStream out, PingPayload ping, long hostReceiveEpochMillis, long hostSendEpochMillis) throws IOException {
        out.writeByte(PONG);
        out.writeLong(ping.pingId());
        out.writeLong(ping.clientSendEpochMillis());
        out.writeLong(ping.clientSendNano());
        out.writeLong(hostReceiveEpochMillis);
        out.writeLong(hostSendEpochMillis);
        out.flush();
    }

    static PongPayload readPong(DataInputStream in) throws IOException {
        return new PongPayload(
            in.readLong(),
            in.readLong(),
            in.readLong(),
            in.readLong(),
            in.readLong()
        );
    }

    static void writeError(DataOutputStream out, String message) throws IOException {
        out.writeByte(ERROR);
        out.writeUTF(message);
        out.flush();
    }

    record ConnectionHello(byte channel, String playerName, String targetName, long tunnelId, String reconnectToken) {
    }

    record SessionPayload(String reconnectToken, boolean reconnected) {
    }

    record AdvancementPayload(String titleKey, String fallbackTitle) {
    }

    record PingPayload(long pingId, long clientSendEpochMillis, long clientSendNano) {
    }

    record PongPayload(
        long pingId,
        long clientSendEpochMillis,
        long clientSendNano,
        long hostReceiveEpochMillis,
        long hostSendEpochMillis
    ) {
    }
}
