package org.evgenium.speedrun.client.mcsr;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.match.RaceSession;

import java.util.ArrayList;
import java.util.List;

/** Temporary always-on diagnostic HUD for the MCSR foundation phase. */
public final class McsrDebugHud {
    private McsrDebugHud() {
    }

    public static void install() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(EvgeniumSpeedRun.MOD_ID, "mcsr_debug"),
            (graphics, deltaTracker) -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null || !RaceSession.hasRunConfig()) {
                    return;
                }

                List<String> lines = new ArrayList<>();
                lines.add("MCSR DEBUG");
                lines.add("World Seed: " + RaceSession.worldSeed());
                lines.add("RNG Seed: " + RaceSession.rngSeed());
                lines.add("Mode: " + RaceSession.randomizationType().displayName());
                lines.add("Ruleset: " + McsrRules.rulesetLabel());
                lines.add("RNG Events: " + CompetitiveRng.totalEvents());

                int width = 0;
                for (String line : lines) {
                    width = Math.max(width, minecraft.font.width(line));
                }

                int padding = 5;
                int lineHeight = 10;
                int boxWidth = width + padding * 2;
                int boxHeight = lines.size() * lineHeight + padding * 2;
                int x = graphics.guiWidth() - boxWidth - 4;
                int y = 4;

                graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xA0000000);
                for (int i = 0; i < lines.size(); i++) {
                    int color = i == 0 ? 0xFFFFDD77 : 0xFFFFFFFF;
                    graphics.text(minecraft.font, lines.get(i), x + padding, y + padding + i * lineHeight, color, i == 0);
                }
            }
        );
    }
}
