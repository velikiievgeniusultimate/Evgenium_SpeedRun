# Evgenium SpeedRun architecture

## Goal

Evgenium SpeedRun is not intended to become a conventional content mod. It is a competitive speedrun client built on top of Minecraft/Fabric.

The client should eventually replace much of the normal multiplayer flow with a match-oriented flow:

`menu -> lobby -> ready -> world preparation -> synchronized countdown -> run -> finish`

## Client areas

### `ui`
Owns the custom title screen, lobby screens, ready/countdown overlays, match HUD, settings and statistics UI.

### `lobby`
Will own lobby membership, host permissions, ready state, rule selection and backend synchronization.

### `match`
Will own the active match identity, participants, rules, start timestamp, finish state and race result.

### `world`
Will own local world preparation, seed application, preloading/freezing and the transition from prepared world to live run.

### `rng`
Will later own explicit competitive RNG streams. RNG manipulation must be opt-in and isolated by subsystem rather than replacing Minecraft's global random implementation.

### `runtime`
Contains the high-level client lifecycle. UI and networking should react to this state instead of inventing separate incompatible state machines.

## Backend boundary

The future backend should coordinate lobbies and matches but should not simulate Minecraft worlds. Each runner keeps an isolated local Minecraft world; the backend distributes match configuration and synchronization messages.

Expected backend responsibilities:

- account/session identity;
- lobby membership and ready state;
- match ID and rules;
- world seed / seed profile;
- RNG profile/seed when enabled;
- synchronized start authorization/time;
- finish/result reporting;
- later matchmaking, ratings and statistics.

## First milestones

1. Reproducible Fabric 26.2 build.
2. Replace the vanilla title screen with `EvgeniumMainScreen`.
3. Build a functional local lobby UI/state model without networking.
4. Define the backend protocol and connect a development lobby server.
5. Prepare identical local worlds and perform a synchronized start.
6. Add timer and automatic finish detection.
7. Only then begin seed filtering and RNG standardization.
