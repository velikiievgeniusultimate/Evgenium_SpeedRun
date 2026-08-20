package org.evgenium.speedrun.client.spectator;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Grants one-shot authentication bypass permits for Minecraft TCP connections that are
 * created locally by an already-authorized Evgenium spectator tunnel.
 *
 * A permit is armed only after the host has approved a spectator tunnel. The integrated
 * server then consumes exactly one permit when the matching loopback Minecraft login reaches
 * ServerLoginPacketListenerImpl. Normal LAN / internet logins never receive a permit.
 */
public final class SpectatorRelayAuth {
    private static final ConcurrentLinkedQueue<Permit> EXPECTED_LOCAL_LOGINS = new ConcurrentLinkedQueue<>();

    private SpectatorRelayAuth() {
    }

    public static Permit armExpectedLocalLogin() {
        Permit permit = new Permit();
        EXPECTED_LOCAL_LOGINS.add(permit);
        return permit;
    }

    public static boolean consumeExpectedLocalLogin() {
        Permit permit;
        while ((permit = EXPECTED_LOCAL_LOGINS.poll()) != null) {
            if (permit.consume()) {
                return true;
            }
        }
        return false;
    }

    public static final class Permit implements AutoCloseable {
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Permit() {
        }

        private boolean consume() {
            return active.compareAndSet(true, false);
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) {
                EXPECTED_LOCAL_LOGINS.remove(this);
            }
        }
    }
}
