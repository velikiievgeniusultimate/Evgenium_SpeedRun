package org.evgenium.speedrun.client.lobby;

/** One NTP-style clock sample echoed by the lobby host. */
public record LobbyTimeSample(
    long clientSendEpochMillis,
    long clientSendNano,
    long hostReceiveEpochMillis,
    long hostSendEpochMillis
) {
}
