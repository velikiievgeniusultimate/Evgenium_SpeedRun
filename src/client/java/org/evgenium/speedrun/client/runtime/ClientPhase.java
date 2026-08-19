package org.evgenium.speedrun.client.runtime;

/**
 * High-level lifecycle of the competitive client.
 *
 * This intentionally lives above Minecraft's individual screens/worlds so future
 * lobby networking and match synchronization have one authoritative client state.
 */
public enum ClientPhase {
    BOOT,
    MENU,
    LOBBY,
    PREPARING_WORLD,
    COUNTDOWN,
    RUNNING,
    FINISHED
}
