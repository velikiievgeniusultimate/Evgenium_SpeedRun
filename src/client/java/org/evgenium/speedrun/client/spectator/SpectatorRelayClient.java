package org.evgenium.speedrun.client.spectator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.lobby.LobbyProtocolAccess;
import org.evgenium.speedrun.client.lobby.LobbyService;
import org.evgenium.speedrun.client.ui.SpectatorTargetScreen;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class SpectatorRelayClient {
    private static volatile ServerSocket localProxy;
    private static volatile String currentTarget;
    private static volatile boolean teleportPending;

    private SpectatorRelayClient() {
    }

    public static String currentTarget() {
        return currentTarget;
    }

    /**
     * Publishes the runner's integrated server before READY is sent.
     *
     * The world therefore behaves as a real multiplayer-capable integrated server for the
     * whole race. Evgenium's pause controller is responsible for freezing gameplay on ESC;
     * we deliberately do not depend on vanilla singleplayer pause semantics anymore.
     */
    public static void prepareLocalRunnerWorld(Runnable onReady) {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            onReady.run();
            return;
        }

        server.execute(() -> {
            try {
                int port = ensurePublishedOnServerThread(server);
                EvgeniumSpeedRun.LOGGER.info("Runner integrated server published for spectator relay on port {}", port);
            } catch (Throwable throwable) {
                EvgeniumSpeedRun.LOGGER.warn("Could not pre-publish runner world for spectators", throwable);
                minecraft.execute(() -> RaceSpectatorNotifications.error(
                    "Не удалось открыть мир для наблюдателей: " + throwable.getMessage()
                ));
            } finally {
                minecraft.execute(onReady);
            }
        });
    }

    public static void watch(String targetName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (targetName == null || targetName.isBlank()) {
            return;
        }

        if (targetName.equals(currentTarget) && minecraft.level != null) {
            teleportPending = true;
            teleportToCurrentTarget(minecraft);
            minecraft.gui.setScreen(null);
            return;
        }

        closeLocalProxy();
        try {
            ServerSocket proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            localProxy = proxy;
            int localPort = proxy.getLocalPort();
            Thread proxyThread = new Thread(() -> serveLocalProxy(proxy, targetName), "Evgenium-Spectator-LocalProxy");
            proxyThread.setDaemon(true);
            proxyThread.start();

            currentTarget = targetName;
            teleportPending = true;
            connectMinecraft(minecraft, localPort);
        } catch (IOException exception) {
            RaceSpectatorNotifications.error("Не удалось открыть локальный spectator proxy: " + exception.getMessage());
        }
    }

    public static void onJoinedWorld() {
        if (currentTarget != null) {
            teleportPending = true;
        }
    }

    public static void tick(Minecraft minecraft) {
        if (teleportPending && currentTarget != null && minecraft.level != null && minecraft.player != null && minecraft.player.isSpectator()) {
            teleportToCurrentTarget(minecraft);
        }
    }

    private static void teleportToCurrentTarget(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || currentTarget == null) {
            return;
        }
        for (Player player : minecraft.level.players()) {
            if (player.getName().getString().equals(currentTarget)) {
                minecraft.player.connection.send(new ServerboundTeleportToEntityPacket(player.getUUID()));
                teleportPending = false;
                return;
            }
        }
    }

    private static void connectMinecraft(Minecraft minecraft, int localPort) {
        minecraft.execute(() -> {
            String addressText = "127.0.0.1:" + localPort;
            SpectatorTargetScreen returnScreen = new SpectatorTargetScreen();
            if (minecraft.level != null) {
                minecraft.disconnect(returnScreen, false);
            }
            ServerAddress address = ServerAddress.parseString(addressText);
            ServerData data = new ServerData("Evgenium Spectator", addressText, ServerData.Type.OTHER);

            // null TransferState is intentional. Passing a non-null transfer state marks the
            // login as a server-transfer connection and vanilla rejects it with
            // multiplayer.disconnect.transfers_disabled ("Сервер не принимает сторонних игроков").
            ConnectScreen.startConnecting(returnScreen, minecraft, address, data, false, null);
        });
    }

    private static void serveLocalProxy(ServerSocket proxy, String targetName) {
        try (proxy; Socket minecraftSocket = proxy.accept()) {
            String relayHost = LobbyService.get().relayHost();
            int relayPort = LobbyService.get().relayPort();
            try (Socket relaySocket = new Socket(relayHost, relayPort)) {
                relaySocket.setTcpNoDelay(true);
                DataOutputStream relayOut = new DataOutputStream(relaySocket.getOutputStream());
                LobbyProtocolAccess.writeSpectatorSourceHello(
                    relayOut,
                    LobbyService.get().localPlayerName(),
                    targetName
                );
                relay(minecraftSocket, relaySocket);
            }
        } catch (IOException exception) {
            RaceSpectatorNotifications.error("Spectator relay: " + exception.getMessage());
        } finally {
            localProxy = null;
        }
    }

    public static void openTargetTunnel(String relayHost, int relayPort, long tunnelId) {
        Thread thread = new Thread(() -> {
            try {
                int localMinecraftPort = ensureIntegratedServerPublished();
                try (Socket localMinecraft = new Socket("127.0.0.1", localMinecraftPort);
                     Socket relay = new Socket(relayHost, relayPort)) {
                    localMinecraft.setTcpNoDelay(true);
                    relay.setTcpNoDelay(true);
                    DataOutputStream relayOut = new DataOutputStream(relay.getOutputStream());
                    LobbyProtocolAccess.writeSpectatorTargetHello(relayOut, tunnelId);

                    // The relay host has already authorized this spectator tunnel. Arm exactly
                    // one loopback Minecraft login so ServerLoginPacketListenerImpl can skip the
                    // redundant Mojang session check for this internal connection only.
                    try (SpectatorRelayAuth.Permit ignored = SpectatorRelayAuth.armExpectedLocalLogin()) {
                        relay(localMinecraft, relay);
                    }
                }
            } catch (Exception exception) {
                EvgeniumSpeedRun.LOGGER.warn("Failed to open spectator target tunnel {}", tunnelId, exception);
            }
        }, "Evgenium-Spectator-TargetTunnel");
        thread.setDaemon(true);
        thread.start();
    }

    private static int ensureIntegratedServerPublished() throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("Локальный мир уже закрыт");
        }

        CompletableFuture<Integer> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(ensurePublishedOnServerThread(server));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.get(10, TimeUnit.SECONDS);
    }

    private static int ensurePublishedOnServerThread(IntegratedServer server) throws IOException {
        if (!server.isPublished()) {
            int port = findFreePort();
            if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, GameType.SPECTATOR, false, port)) {
                throw new IllegalStateException("Minecraft не смог открыть мир для наблюдателей");
            }
        }
        return server.getPort();
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void relay(Socket left, Socket right) throws IOException {
        Thread reverse = new Thread(() -> copy(right, left), "Evgenium-Spectator-ClientRelay-Reverse");
        reverse.setDaemon(true);
        reverse.start();
        copy(left, right);
    }

    private static void copy(Socket from, Socket to) {
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
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

    public static void closeLocalProxy() {
        ServerSocket proxy = localProxy;
        if (proxy != null) {
            try {
                proxy.close();
            } catch (IOException ignored) {
            }
            localProxy = null;
        }
        currentTarget = null;
        teleportPending = false;
    }
}
