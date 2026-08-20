package org.evgenium.speedrun.client.match;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.evgenium.speedrun.client.lobby.LobbyAdvancement;

public final class AdvancementChat {
    private AdvancementChat() {
    }

    public static void show(LobbyAdvancement advancement) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null || minecraft.gui.hud == null) {
            return;
        }

        MutableComponent title = advancement.titleKey().isBlank()
            ? Component.literal(advancement.fallbackTitle())
            : Component.translatable(advancement.titleKey());

        MutableComponent message = Component.literal(advancement.playerName())
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" получил достижение ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("[").withStyle(ChatFormatting.GREEN))
            .append(title.withStyle(ChatFormatting.GREEN))
            .append(Component.literal("]").withStyle(ChatFormatting.GREEN));

        minecraft.gui.hud.getChat().addClientSystemMessage(message);
    }
}
