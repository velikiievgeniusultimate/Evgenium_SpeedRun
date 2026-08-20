package org.evgenium.speedrun.client.lobby;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class LobbyProtocol {
    static final int MAGIC = 0x45565352; // EVSR
    static final int VERSION = 4;
    static final byte STATE = 10;
    static final byte ERROR = 11;
    static final byte START_RUN = 12;
    static final byte GO = 13;
    static final byte RACE_FINISHED = 14;
    static final byte LEAVE = 20;
    static final byte READY = 21;
    static final byte FINISH = 22;

    private LobbyProtocol() {
    }

    static void writeHello(DataOutputStream out, String playerName) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeUTF(playerName);
        out.flush();
    }

    static String readHello(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("Это не Evgenium SpeedRun lobby");
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("Несовместимая версия протокола: " + version);
        }
        String name = in.readUTF().trim();
        if (name.isEmpty() || name.length() > 32) {
            throw new IOException("Некорректное имя игрока");
        }
        return name;
    }

    static void writeState(DataOutputStream out, LobbySnapshot snapshot) throws IOException {
        out.writeByte(STATE);
        out.writeBoolean(snapshot.cheatsEnabled());
        out.writeUTF(snapshot.goal().id());
        out.writeInt(snapshot.players().size());
        for (LobbyPlayer player : snapshot.players()) {
            out.writeUTF(player.name());
            out.writeBoolean(player.host());
        }
        out.flush();
    }

    static LobbySnapshot readState(DataInputStream in) throws IOException {
        boolean cheatsEnabled = in.readBoolean();
        SpeedrunGoal goal;
        try {
            goal = SpeedrunGoal.fromId(in.readUTF());
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        int count = in.readInt();
        if (count < 0 || count > 128) {
            throw new IOException("Некорректный размер лобби");
        }
        List<LobbyPlayer> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(new LobbyPlayer(in.readUTF(), in.readBoolean()));
        }
        return new LobbySnapshot(players, cheatsEnabled, goal);
    }

    static void writeStartRun(DataOutputStream out, LobbyRunConfig config) throws IOException {
        out.writeByte(START_RUN);
        out.writeLong(config.seed());
        out.writeBoolean(config.cheatsEnabled());
        out.writeUTF(config.goal().id());
        out.flush();
    }

    static LobbyRunConfig readStartRun(DataInputStream in) throws IOException {
        long seed = in.readLong();
        boolean cheatsEnabled = in.readBoolean();
        try {
            return new LobbyRunConfig(seed, cheatsEnabled, SpeedrunGoal.fromId(in.readUTF()));
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
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

    static void writeRaceFinished(DataOutputStream out, LobbyRaceResult result) throws IOException {
        out.writeByte(RACE_FINISHED);
        out.writeUTF(result.winnerName());
        out.writeLong(result.elapsedMillis());
        out.flush();
    }

    static LobbyRaceResult readRaceFinished(DataInputStream in) throws IOException {
        String winnerName = in.readUTF();
        long elapsedMillis = in.readLong();
        try {
            return new LobbyRaceResult(winnerName, elapsedMillis);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Некорректный результат гонки", exception);
        }
    }

    static void writeError(DataOutputStream out, String message) throws IOException {
        out.writeByte(ERROR);
        out.writeUTF(message);
        out.flush();
    }
}
