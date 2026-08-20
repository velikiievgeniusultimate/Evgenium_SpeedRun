package org.evgenium.speedrun.client.lobby;

import java.io.DataOutputStream;
import java.io.IOException;

public final class LobbyProtocolAccess {
    private LobbyProtocolAccess() {
    }

    public static void writeSpectatorSourceHello(DataOutputStream out, String spectatorName, String targetName) throws IOException {
        LobbyProtocol.writeSpectatorSourceHello(out, spectatorName, targetName);
    }

    public static void writeSpectatorTargetHello(DataOutputStream out, long tunnelId) throws IOException {
        LobbyProtocol.writeSpectatorTargetHello(out, tunnelId);
    }
}
