package org.evgenium.speedrun.client.mcsr;

import org.evgenium.speedrun.client.lobby.RandomizationType;
import org.evgenium.speedrun.client.match.RaceSession;

/** Central gate for every future MCSR-like gameplay modification. */
public final class McsrRules {
    public static final int MCSR_RULESET_VERSION = 1;

    private McsrRules() {
    }

    public static boolean active() {
        return RaceSession.hasRunConfig()
            && RaceSession.randomizationType() == RandomizationType.MCSR_LIKE;
    }

    public static String rulesetLabel() {
        return active() ? "MCSR-Like R" + MCSR_RULESET_VERSION : "Vanilla";
    }
}
