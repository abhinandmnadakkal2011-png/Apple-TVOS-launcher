package com.petrobot.hume;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.content.Context;

/** Original tvOS-inspired UI tones: warm low-end thumps with a clean, short upper transient. */
final class GlassSounds {
    private static final int RATE = 44100;
    private static AudioTrack moveTrack;
    private static AudioTrack selectTrack;

    private GlassSounds() { }

    static synchronized void init(Context context) {
        if (moveTrack != null && selectTrack != null) return;
        try {
            moveTrack = createTrack(navigationThump());
            selectTrack = createTrack(selectionThump());
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
        track.setVolume(0.72f);
        return track;
    }

    /** A rounded, low-pitched 95–145 Hz movement pulse with a subtle crisp edge. */
    private static short[] navigationThump() {
        int count = RATE * 112 / 1000;
        short[] pcm = new short[count];
        double phase = 0.0;
        for (int i = 0; i < count; i++) {
            float t = i / (float) RATE;
            float p = i / (float) count;
            float freq = 104f - 28f * p;
            phase += 2.0 * Math.PI * freq / RATE;
            float env = (float) Math.pow(1f - p, 1.85);
            float low = (float) Math.sin(phase);
            float body = (float) Math.sin(phase * 2.02);
            float tick = (float) Math.sin(2.0 * Math.PI * 420f * t) * (float) Math.exp(-p * 28f);
            float sample = (low * 0.76f + body * 0.18f) * env + tick * 0.06f;
            pcm[i] = (short) (sample * 7800f);
        }
        return pcm;
    }

    /** Deeper, fuller 82–120 Hz selection thump with a gentle 500 Hz attack. */
    private static short[] selectionThump() {
        int count = RATE * 165 / 1000;
        short[] pcm = new short[count];
        double phase = 0.0;
        for (int i = 0; i < count; i++) {
            float t = i / (float) RATE;
            float p = i / (float) count;
            float freq = 88f - 24f * p;
            phase += 2.0 * Math.PI * freq / RATE;
            float env = (float) Math.pow(1f - p, 1.9);
            float sub = (float) Math.sin(phase);
            float harmonic = (float) Math.sin(phase * 2.0);
            float attack = (float) Math.sin(2.0 * Math.PI * 340f * t) * (float) Math.exp(-p * 22f);
            float sample = (sub * 0.80f + harmonic * 0.16f) * env + attack * 0.04f;
            pcm[i] = (short) (sample * 9200f);
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
