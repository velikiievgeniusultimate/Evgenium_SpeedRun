package org.evgenium.speedrun.client.spectator;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.evgenium.speedrun.EvgeniumSpeedRun;
import org.evgenium.speedrun.client.match.RaceSession;
import org.evgenium.speedrun.spectator.SpectatorItems;

/**
 * One-slot selector HUD for finished runners in vanilla spectator mode.
 */
public final class SpectatorOneSlotHud {
    private static ItemStack selector;

    private SpectatorOneSlotHud() {
    }

    public static void install() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(EvgeniumSpeedRun.MOD_ID, "spectator_one_slot_inventory"),
            (graphics, deltaTracker) -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (!RaceSession.isLocalFinished()
                    || minecraft.player == null
                    || !minecraft.player.isSpectator()
                    || minecraft.level == null) {
                    return;
                }

                ItemStack selectorStack = selector;
                if (selectorStack == null) {
                    selectorStack = SpectatorItems.createSelector();
                    selector = selectorStack;
                }

                int slotSize = 24;
                int x = (graphics.guiWidth() - slotSize) / 2;
                int y = graphics.guiHeight() - 30;

                graphics.fill(x, y, x + slotSize, y + slotSize, 0xCC202020);
                graphics.outline(x, y, slotSize, slotSize, 0xFFFFFFFF);
                graphics.outline(x + 2, y + 2, slotSize - 4, slotSize - 4, 0xFF8A8A8A);
                graphics.item(selectorStack, x + 4, y + 4);

                String hint = "ПКМ — выбрать игрока";
                int hintX = (graphics.guiWidth() - minecraft.font.width(hint)) / 2;
                graphics.text(minecraft.font, hint, hintX, y - 12, 0xFFFFFFFF, true);
            }
        );
    }
}
