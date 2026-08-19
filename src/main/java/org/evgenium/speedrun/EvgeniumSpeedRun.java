package org.evgenium.speedrun;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EvgeniumSpeedRun implements ModInitializer {
    public static final String MOD_ID = "evgenium_speedrun";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Evgenium SpeedRun common foundation initialized");
    }
}
