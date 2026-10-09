package com.hollowdread;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HollowDread implements ModInitializer {
    public static final String MOD_ID = "hollowdread";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModEntities.init();
        ModItems.init();
        FearManager.init();
        DreadCommand.init();
        LOGGER.info("Hollow Dread carregado. Não olhe para trás.");
    }
}
