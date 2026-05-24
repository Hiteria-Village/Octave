/**
 * Jaydenz | 05/23/2026
 */

/**
 * client side audio loader.
 */

package org.hiteria.audio.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.hiteria.audio.HiteriaAudioManager;

public class HiteriaAudioClient implements ClientModInitializer {
    private static boolean audioDisabled = false;
    private static boolean ffmpegInitialized = false;

    @Override
    public void onInitializeClient() {
    }

    public static synchronized boolean ensureFfmpegInitialized() {
        if (ffmpegInitialized || audioDisabled) {
            return !audioDisabled;
        }

        try {
            org.bytedeco.ffmpeg.global.avutil.av_log_set_level(16);
            ffmpegInitialized = true;
            return true;
        } catch (Throwable t) {
            audioDisabled = true;
            return false;
        }
    }
}