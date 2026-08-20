package org.evgenium.speedrun.client.mcsr;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.match.RaceSession;

import java.util.OptionalLong;

public final class McsrDebugCommands {
    private McsrDebugCommands() {
    }

    public static void install() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
            dispatcher.register(
                ClientCommands.literal("evsr")
                    .then(
                        ClientCommands.literal("rng")
                            .executes(context -> showState(context.getSource()))
                            .then(ClientCommands.literal("reset").executes(context -> reset(context.getSource())))
                            .then(ClientCommands.literal("sample").executes(context -> sample(context.getSource())))
                    )
            )
        );
    }

    private static int showState(FabricClientCommandSource source) {
        if (!RaceSession.hasRunConfig()) {
            source.sendFeedback(Component.literal("[EVSR] Активного забега нет"));
            return 0;
        }

        source.sendFeedback(Component.literal(
            "[EVSR] World Seed=" + RaceSession.worldSeed()
                + " | RNG Seed=" + RaceSession.rngSeed()
        ));
        source.sendFeedback(Component.literal(
            "[EVSR] Mode=" + RaceSession.randomizationType().displayName()
                + " | Ruleset=" + McsrRules.rulesetLabel()
                + " | active=" + McsrRules.active()
        ));

        StringBuilder counters = new StringBuilder("[EVSR] Counters: ");
        boolean first = true;
        for (RngStream stream : RngStream.values()) {
            if (!first) {
                counters.append(" | ");
            }
            first = false;
            counters.append(stream.name()).append('=').append(CompetitiveRng.counter(stream));
        }
        source.sendFeedback(Component.literal(counters.toString()));
        return 1;
    }

    private static int reset(FabricClientCommandSource source) {
        if (!RaceSession.hasRunConfig()) {
            source.sendFeedback(Component.literal("[EVSR] Активного забега нет"));
            return 0;
        }
        if (!RaceSession.cheatsEnabled()) {
            source.sendFeedback(Component.literal("[EVSR] /evsr rng reset доступен только если в настройках забега включены читы"));
            return 0;
        }

        CompetitiveRng.resetCounters();
        source.sendFeedback(Component.literal("[EVSR] Все MCSR RNG-счётчики сброшены в 0"));
        return 1;
    }

    /** Test-only stream so diagnostics can be verified without consuming future gameplay streams. */
    private static int sample(FabricClientCommandSource source) {
        if (!RaceSession.hasRunConfig()) {
            source.sendFeedback(Component.literal("[EVSR] Активного забега нет"));
            return 0;
        }
        if (!RaceSession.cheatsEnabled()) {
            source.sendFeedback(Component.literal("[EVSR] /evsr rng sample доступен только при включённых читах"));
            return 0;
        }

        OptionalLong value = CompetitiveRng.nextLong(RngStream.DEBUG);
        if (value.isEmpty()) {
            source.sendFeedback(Component.literal("[EVSR] Vanilla: MCSR RNG обойдён, счётчик DEBUG не изменён"));
            return 1;
        }

        source.sendFeedback(Component.literal(
            "[EVSR] DEBUG sample=" + value.getAsLong()
                + " | следующий index=" + CompetitiveRng.counter(RngStream.DEBUG)
        ));
        return 1;
    }
}
