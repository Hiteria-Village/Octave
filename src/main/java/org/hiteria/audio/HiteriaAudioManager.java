/**
 * Jaydenz | 05/23/2026
 */

/**
 * server side audio loader.
 */

package org.hiteria.audio;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HiteriaAudioManager implements ModInitializer {
    public static final String MOD_ID = "hiteria-audio-manager";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Hiteria Audio Manager Loaded");
    }
}
