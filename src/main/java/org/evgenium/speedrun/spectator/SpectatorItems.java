package org.evgenium.speedrun.spectator;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SpectatorItems {
    public static final String SELECTOR_NAME = "Наблюдать за игроками";

    private SpectatorItems() {
    }

    public static ItemStack createSelector() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(SELECTOR_NAME));
        return stack;
    }

    public static boolean isSelector(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.COMPASS || stack.getCustomName() == null) {
            return false;
        }
        return SELECTOR_NAME.equals(stack.getCustomName().getString());
    }
}
