# Direct-connect lobby architecture

The first lobby implementation deliberately does not use a Minecraft world as the waiting room.

A host opens a TCP listening socket on the port selected in the Create Lobby screen. Guests connect directly to `IP:port` from the Join Lobby screen. This means the networking topology is the same as a self-hosted Minecraft server: the host must allow the port through the operating-system firewall and, when connecting over the Internet from behind NAT, forward the selected TCP port on the router.

Keeping the lobby transport independent from the integrated Minecraft server is intentional. When a race starts, every runner will later be able to create and play a separate local world while keeping the lightweight lobby/control connection alive for ready state, countdown, race state and finish synchronization.

## Protocol v1

- TCP transport.
- Client hello: EVSR magic, protocol version, Minecraft username.
- Host broadcasts an immutable player list whenever somebody joins or leaves.
- No authentication or encryption yet; this is an early direct-connect development protocol.
- No automatic UPnP/NAT-PMP port forwarding yet.
