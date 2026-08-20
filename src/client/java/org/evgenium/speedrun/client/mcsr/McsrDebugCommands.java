package org.evgenium.speedrun.client.mcsr;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.match.RaceSession;
import org.evgenium.speedrun.mcsr.RngStream;

import java.util.Locale;
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
                            .then(
                                ClientCommands.literal("sample")
                                    .executes(context -> sample(context.getSource(), RngStream.DEBUG, 1))
                                    .then(
                                        ClientCommands.argument("stream", StringArgumentType.word())
                                            .executes(context -> sample(
                                                context.getSource(),
                                                parseStream(context.getSource(), StringArgumentType.getString(context, "stream")),
                                                1
                                            ))
                                            .then(
                                                ClientCommands.argument("count", IntegerArgumentType.integer(1, 10_000))
                                                    .executes(context -> sample(
                                                        context.getSource(),
                                                        parseStream(context.getSource(), StringArgumentType.getString(context, "stream")),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                    ))
                                            )
                                    )
                            )
                            .then(ClientCommands.literal("selftest").executes(context -> selfTest(context.getSource())))
                            .then(ClientCommands.literal("history").executes(context -> history(context.getSource())))
                    )
            )
        );
    }

    private static int showState(FabricClientCommandSource source) {
        if (!ensureRace(source)) {
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
        source.sendFeedback(Component.literal(
            "[EVSR] History=" + CompetitiveRng.historySnapshot().size() + "/" + CompetitiveRng.HISTORY_LIMIT
                + " | /evsr rng history — открыть журнал"
        ));
        return 1;
    }

    private static int reset(FabricClientCommandSource source) {
        if (!ensureRace(source)) {
            return 0;
        }
        if (!RaceSession.cheatsEnabled()) {
            source.sendFeedback(Component.literal("[EVSR] /evsr rng reset доступен только если в настройках забега включены читы"));
            return 0;
        }

        CompetitiveRng.resetCounters();
        source.sendFeedback(Component.literal("[EVSR] Все MCSR RNG-счётчики и история сброшены"));
        return 1;
    }

    private static int sample(FabricClientCommandSource source, RngStream stream, int count) {
        if (stream == null) {
            return 0;
        }
        if (!ensureRace(source)) {
            return 0;
        }
        if (!RaceSession.cheatsEnabled()) {
            source.sendFeedback(Component.literal("[EVSR] /evsr rng sample доступен только при включённых читах"));
            return 0;
        }

        long first = 0L;
        long last = 0L;
        for (int i = 0; i < count; i++) {
            OptionalLong value = CompetitiveRng.nextLong(stream);
            if (value.isEmpty()) {
                source.sendFeedback(Component.literal(
                    "[EVSR] Vanilla: MCSR RNG обойдён, счётчик " + stream.name() + " не изменён"
                ));
                return 1;
            }
            if (i == 0) {
                first = value.getAsLong();
            }
            last = value.getAsLong();
        }

        source.sendFeedback(Component.literal(
            "[EVSR] " + stream.name() + " consumed=" + count
                + " | first=" + first
                + " | last=" + last
                + " | nextIndex=" + CompetitiveRng.counter(stream)
        ));
        return 1;
    }

    private static int selfTest(FabricClientCommandSource source) {
        if (!ensureRace(source)) {
            return 0;
        }

        CompetitiveRngSelfTest.Report report = CompetitiveRngSelfTest.run(RaceSession.rngSeed());
        if (report.success()) {
            source.sendFeedback(Component.literal(
                "[EVSR] RNG SELFTEST: PASS (" + report.passed().size() + "/" + report.passed().size() + ")"
            ));
            for (String check : report.passed()) {
                source.sendFeedback(Component.literal("[EVSR] ✓ " + check));
            }
            return 1;
        }

        source.sendFeedback(Component.literal(
            "[EVSR] RNG SELFTEST: FAIL passed=" + report.passed().size() + " failed=" + report.failed().size()
        ));
        for (String failure : report.failed()) {
            source.sendFeedback(Component.literal("[EVSR] ✗ " + failure));
        }
        return 0;
    }

    private static int history(FabricClientCommandSource source) {
        if (!ensureRace(source)) {
            return 0;
        }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new McsrRngHistoryScreen()));
        return 1;
    }

    private static RngStream parseStream(FabricClientCommandSource source, String raw) {
        try {
            return RngStream.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFeedback(Component.literal(
                "[EVSR] Неизвестный stream '" + raw + "'. Пример: FLINT, BLAZE_DROP, ENDERMAN_DROP, BARTER, EYE_BREAK"
            ));
            return null;
        }
    }

    private static boolean ensureRace(FabricClientCommandSource source) {
        if (!RaceSession.hasRunConfig()) {
            source.sendFeedback(Component.literal("[EVSR] Активного забега нет"));
            return false;
        }
        return true;
    }
}
