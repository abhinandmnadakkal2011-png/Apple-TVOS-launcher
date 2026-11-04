package com.petrobot.hume;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.content.Context;

/** Tiny synthesized UI sounds: no bundled audio files, network access, or system click tones. */
final class GlassSounds {
    private static final int RATE = 44100;
    private static AudioTrack moveTrack;
    private static AudioTrack selectTrack;

    private GlassSounds() { }

    static synchronized void init(Context context) {
        if (moveTrack != null && selectTrack != null) return;
        try {
            moveTrack = createTrack(glassTick());
            selectTrack = createTrack(glassTap());
        } catch (Throwable ignored) {
            release();
        }
    }

    private static AudioTrack createTrack(short[] pcm) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length * 2)
                .build();
        track.write(pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING);
        track.setVolume(0.34f);
        return track;
    }

    /** Short bright upward glass tick for D-pad movement. */
    private static short[] glassTick() {
        int count = RATE * 48 / 1000;
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++) {
            float t = i / (float) RATE;
            float progress = i / (float) count;
            float frequency = 1420f + 620f * progress;
            float envelope = (float) Math.pow(1f - progress, 2.2);
            float wave = (float) Math.sin(2.0 * Math.PI * frequency * t);
            pcm[i] = (short) (wave * envelope * 5100f);
        }
        return pcm;
    }

    /** Softer two-harmonic tap for selecting an app or going Back. */
    private static short[] glassTap() {
        int count = RATE * 68 / 1000;
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++) {
            float t = i / (float) RATE;
            float progress = i / (float) count;
            float envelope = (float) Math.pow(1f - progress, 2.8);
            float wave = (float) (Math.sin(2.0 * Math.PI * 980f * t)
                    + 0.32 * Math.sin(2.0 * Math.PI * 1680f * t));
            pcm[i] = (short) (wave * envelope * 3000f);
        }
        return pcm;
    }

    static void playMove() { play(moveTrack); }
    static void playSelect() { play(selectTrack); }

    private static synchronized void play(AudioTrack track) {
        if (track == null) return;
        try {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.pause();
            track.setPlaybackHeadPosition(0);
            track.play();
        } catch (IllegalStateException ignored) { }
    }

    private static synchronized void release() {
        if (moveTrack != null) { try { moveTrack.release(); } catch (Exception ignored) { } moveTrack = null; }
        if (selectTrack != null) { try { selectTrack.release(); } catch (Exception ignored) { } selectTrack = null; }
    }
}
