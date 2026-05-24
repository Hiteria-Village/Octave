/**
 * Jaydenz | 05/23/2026
 */

/**
 * class file for audio and format detection.
 */

package org.hiteria.audio.client;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.hiteria.audio.HiteriaAudioManager;

import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class FFmpegAudioUtils {
    

    public static AudioMetadata getAudioMetadata(Path audioFile) {
        if (!HiteriaAudioClient.ensureFfmpegInitialized()) return null;
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(audioFile.toString())) {
            grabber.start();
            
            return new AudioMetadata(
                grabber.getSampleRate(),
                grabber.getAudioChannels(),
                grabber.getLengthInTime() / 1_000_000.0, // Convert microseconds to seconds
                grabber.getAudioCodecName(),
                grabber.getAudioBitrate()
            );
        } catch (Exception e) {
            HiteriaAudioManager.LOGGER.warn("Failed to get metadata for: " + audioFile.getFileName() + " - " + e.getMessage());
            return null;
        }
    }
    
    public static boolean isSupportedAudioFormat(Path audioFile) {
        String fileName = audioFile.getFileName().toString().toLowerCase();
        return fileName.endsWith(".ogg") || 
               fileName.endsWith(".mp3") || 
               fileName.endsWith(".wav") || 
               fileName.endsWith(".flac") ||
               fileName.endsWith(".m4a") ||
               fileName.endsWith(".aac") ||
               fileName.endsWith(".wma");
    }
    
    /**
     * decoding
     */
    public static CompletableFuture<short[]> decodeAudioAsync(Path audioFile) {
        if (!HiteriaAudioClient.ensureFfmpegInitialized()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(audioFile.toString())) {
                grabber.start();
                int sampleRate = grabber.getSampleRate();
                int channels = grabber.getAudioChannels();
                long lengthInSamples = grabber.getLengthInAudioFrames();
                if (lengthInSamples <= 0) {
                    lengthInSamples = (long) (grabber.getLengthInTime() / 1_000_000.0 * sampleRate);
                }
                short[] audioData = new short[(int) (lengthInSamples * channels)];
                int offset = 0;
                Frame frame;
                while ((frame = grabber.grab()) != null && offset < audioData.length) {
                    if (frame.samples != null) {
                        ShortBuffer buffer = (ShortBuffer) frame.samples[0];
                        int remaining = Math.min(buffer.remaining(), audioData.length - offset);
                        buffer.get(audioData, offset, remaining);
                        offset += remaining;
                    }
                }
                
                if (offset < audioData.length) {
                    short[] trimmed = new short[offset];
                    System.arraycopy(audioData, 0, trimmed, 0, offset);
                    return trimmed;
                }
                
                return audioData;
                
            } catch (Exception e) {
                HiteriaAudioManager.LOGGER.warn("Failed to decode audio: " + audioFile.getFileName() + " - " + e.getMessage());
                return null;
            }
        });
    }
    
    public static short[] decodeAudio(Path audioFile) {
        if (!HiteriaAudioClient.ensureFfmpegInitialized()) return null;
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(audioFile.toString())) {
            grabber.start();
            
            int sampleRate = grabber.getSampleRate();
            int channels = grabber.getAudioChannels();
            
            HiteriaAudioManager.LOGGER.info("Loading audio: " + audioFile.getFileName() + " (" + sampleRate + "Hz, " + channels + " channels)");
            
            java.util.List<short[]> audioChunks = new java.util.ArrayList<>();
            int totalSamples = 0;
            
            Frame frame;
            while ((frame = grabber.grab()) != null) {
                if (frame.samples != null) {
                    ShortBuffer buffer = (ShortBuffer) frame.samples[0];
                    short[] chunk = new short[buffer.remaining()];
                    buffer.get(chunk);
                    
                    if (channels == 1) {
                        short[] stereoChunk = new short[chunk.length * 2];
                        for (int i = 0; i < chunk.length; i++) {
                            stereoChunk[i * 2] = chunk[i];
                            stereoChunk[i * 2 + 1] = chunk[i];
                        }
                        chunk = stereoChunk;
                    }
                    
                    audioChunks.add(chunk);
                    totalSamples += chunk.length;
                }
            }
            
            short[] result = new short[totalSamples];
            int offset = 0;
            for (short[] chunk : audioChunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            
            return result;
            
        } catch (Exception e) {
            HiteriaAudioManager.LOGGER.warn("Failed to decode audio with FFmpeg: " + audioFile.getFileName() + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * apply audio settings and effects
     */
    public static short[] applyAudioEffects(short[] audioData, AudioEffects effects) {
        if (effects == null || audioData == null) return audioData;
        
        short[] processed = audioData.clone();
        
        if (effects.normalize) {
            HiteriaAudioManager.LOGGER.info("Applying normalization (this may alter audio dynamics)");
            processed = normalizeAudio(processed);
        }
        
        if (effects.volumeMultiplier != 1.0f) {
            for (int i = 0; i < processed.length; i++) {
                int sample = (int) (processed[i] * effects.volumeMultiplier);
                processed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
            }
        }
        
        if (effects.fadeInSamples > 0) {
            applyFadeIn(processed, effects.fadeInSamples);
        }
        if (effects.fadeOutSamples > 0) {
            applyFadeOut(processed, effects.fadeOutSamples);
        }
        
        return processed;
    }
    
    private static short[] normalizeAudio(short[] audioData) {
        int maxPeak = 0;
        for (short sample : audioData) {
            maxPeak = Math.max(maxPeak, Math.abs(sample));
        }
        
        if (maxPeak == 0) return audioData;
        
        float normalizationFactor = (Short.MAX_VALUE * 0.85f) / maxPeak;
        
        if (normalizationFactor >= 1.0f) return audioData; 
        
        short[] normalized = new short[audioData.length];
        for (int i = 0; i < audioData.length; i++) {
            int sample = (int) (audioData[i] * normalizationFactor);
            normalized[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
        }
        
        return normalized;
    }
    
    private static void applyFadeIn(short[] audioData, int fadeInSamples) {
        int actualFadeSamples = Math.min(fadeInSamples, audioData.length);
        for (int i = 0; i < actualFadeSamples; i++) {
            float factor = (float) i / actualFadeSamples;
            audioData[i] = (short) (audioData[i] * factor);
        }
    }
    
    private static void applyFadeOut(short[] audioData, int fadeOutSamples) {
        int actualFadeSamples = Math.min(fadeOutSamples, audioData.length);
        int startIndex = audioData.length - actualFadeSamples;
        for (int i = 0; i < actualFadeSamples; i++) {
            float factor = 1.0f - ((float) i / actualFadeSamples);
            audioData[startIndex + i] = (short) (audioData[startIndex + i] * factor);
        }
    }
    
    public static class AudioMetadata {
        public final int sampleRate;
        public final int channels;
        public final double durationSeconds;
        public final String codecName;
        public final int bitrate;
        
        public AudioMetadata(int sampleRate, int channels, double durationSeconds, String codecName, int bitrate) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.durationSeconds = durationSeconds;
            this.codecName = codecName;
            this.bitrate = bitrate;
        }
        
        @Override
        public String toString() {
            return String.format("AudioMetadata{rate=%dHz, channels=%d, duration=%.2fs, codec=%s, bitrate=%d}", 
                sampleRate, channels, durationSeconds, codecName, bitrate);
        }
    }
    
    public static class AudioEffects {
        public boolean normalize = false;
        public float volumeMultiplier = 1.0f;
        public int fadeInSamples = 0;
        public int fadeOutSamples = 0;
        
        public static AudioEffects normalize() {
            AudioEffects effects = new AudioEffects();
            effects.normalize = true;
            return effects;
        }
        
        public static AudioEffects volume(float multiplier) {
            AudioEffects effects = new AudioEffects();
            effects.volumeMultiplier = multiplier;
            return effects;
        }
        
        public AudioEffects withFadeIn(int samples) {
            this.fadeInSamples = samples;
            return this;
        }
        
        public AudioEffects withFadeOut(int samples) {
            this.fadeOutSamples = samples;
            return this;
        }
    }
}
