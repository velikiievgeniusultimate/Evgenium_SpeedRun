package org.evgenium.speedrun.client.lobby;

import java.util.List;

/** Authoritative host snapshot used when a control connection is re-established mid-run. */
record LobbyResumeState(
    LobbyRunConfig config,
    boolean goIssued,
    long goAtEpochMillis,
    List<LobbyRaceResult> results
) {
    LobbyResumeState {
        results = List.copyOf(results);
    }
}
