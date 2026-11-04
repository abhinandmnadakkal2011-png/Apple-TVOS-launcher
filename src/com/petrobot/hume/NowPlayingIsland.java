package com.petrobot.hume;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/** Focus-expandable Apple-inspired Now Playing island for Android TV media sessions. */
final class NowPlayingIsland extends FrameLayout {
    private static final String PREFS = "now_playing";
    private static final String ENABLED = "enabled";
    private static final String ART = "artwork";
    private static final String STYLE = "style";
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout pill;
    private final LinearLayout panel;
    private final ImageView pillArt, art;
    private final TextView pillTitle, pillArtist, title, artist, playPause, elapsed, remaining;
    private final SeekBar seek;
    private MediaSessionManager manager;
    private MediaController controller;
    private MediaController.Callback controllerCallback;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    private boolean trackingSeek;
    private boolean expanded;
    private boolean updating;
    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            updateProgress();
            if (controller != null && isPlaying(controller)) handler.postDelayed(this, 1000);
        }
    };

    static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static boolean enabled(Context c) { return prefs(c).getBoolean(ENABLED, true); }
    static void setEnabled(Context c, boolean v) { prefs(c).edit().putBoolean(ENABLED, v).apply(); }
    static boolean showArtwork(Context c) { return prefs(c).getBoolean(ART, true); }
    static void setShowArtwork(Context c, boolean v) { prefs(c).edit().putBoolean(ART, v).apply(); }
    static String style(Context c) { return prefs(c).getString(STYLE, "black"); }
    static void setStyle(Context c, String v) { prefs(c).edit().putString(STYLE, "glass".equals(v) ? "glass" : "black").apply(); }

    NowPlayingIsland(Activity a) {
        super(a);
        activity = a;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setVisibility(GONE);
        setClipChildren(false);
        setClipToPadding(false);

        pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(12), dp(7), dp(16), dp(7));
        pill.setBackground(background(0xF20A0A0C, dp(28)));
        pillArt = new ImageView(a);
        pillArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pill.addView(pillArt, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout miniText = new LinearLayout(a);
        miniText.setOrientation(LinearLayout.VERTICAL);
        miniText.setPadding(dp(10), 0, dp(10), 0);
        pillTitle = label(13, 0xFFFFFFFF, true);
        pillArtist = label(11, 0xFFBDBDC2, false);
        miniText.addView(pillTitle);
        miniText.addView(pillArtist);
        pill.addView(miniText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView wave = label(18, 0xFFFFFFFF, true);
        wave.setText("♫");
        pill.addView(wave);
        addView(pill, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(18));
        panel.setBackground(background(0xF20A0A0C, dp(30)));
        panel.setVisibility(GONE);

        LinearLayout heading = new LinearLayout(a);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(a);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heading.addView(art, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout details = new LinearLayout(a);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(16), 0, 0, 0);
        title = label(18, Color.WHITE, true);
        artist = label(14, 0xFFBDBDC2, false);
        details.addView(title);
        details.addView(artist);
        heading.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(heading);

        LinearLayout timeRow = new LinearLayout(a);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = label(11, 0xFFBDBDC2, false);
        remaining = label(11, 0xFFBDBDC2, false);
        timeRow.addView(elapsed);
        seek = new SeekBar(a);
        seek.setMax(1000);
        seek.setPadding(dp(8), 0, dp(8), 0);
        timeRow.addView(seek, new LinearLayout.LayoutParams(0, dp(28), 1));
        timeRow.addView(remaining);
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        trp.topMargin = dp(14);
        panel.addView(timeRow, trp);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar b) { trackingSeek = true; handler.removeCallbacks(progressTick); }
            @Override public void onStopTrackingTouch(SeekBar b) {
                trackingSeek = false;
                if (controller != null) {
                    long duration = duration();
                    if (duration > 0) controller.getTransportControls().seekTo(duration * b.getProgress() / 1000L);
                }
                updateProgress();
            }
        });

        LinearLayout controls = new LinearLayout(a);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        TextView previous = control("|◀");
        playPause = control("Ⅱ");
        TextView next = control("▶|");
        controls.addView(previous, controlLp());
        controls.addView(playPause, controlLp());
        controls.addView(next, controlLp());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        clp.topMargin = dp(6);
        panel.addView(controls, clp);
        previous.setOnClickListener(v -> transport(0));
        playPause.setOnClickListener(v -> transport(1));
        next.setOnClickListener(v -> transport(2));
        addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        setOnFocusChangeListener((v, has) -> {
            if (has) setExpanded(true);
            else postDelayed(() -> {
                if (!hasFocus()) setExpanded(false);
            }, 90);
        });
        setOnClickListener(v -> setExpanded(!expanded));
        setOnKeyListener((v, key, event) -> {
            if (key == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && expanded) {
                clearFocus();
                setExpanded(false);
                return true;
            }
            return false;
        });
        refreshStyle();
    }

    private int dp(float v) { return Ui.px(activity, v); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor("glass".equals(style(activity)) ? 0xDD34343A : color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), 0x28FFFFFF);
        return d;
    }
    private TextView label(int size, int color, boolean bold) {
        TextView t = new TextView(activity);
        Ui.textSize(t, size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }
    private TextView control(String text) {
        TextView t = label(23, Color.WHITE, true);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(background(0xFF242428, dp(28)));
        return t;
    }
    private LinearLayout.LayoutParams controlLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(58), dp(48));
        p.leftMargin = dp(12);
        p.rightMargin = dp(12);
        return p;
    }
    private boolean isPlaying(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }
    private void transport(int action) {
        if (controller == null) return;
        MediaController.TransportControls tc = controller.getTransportControls();
        if (action == 0) tc.skipToPrevious();
        else if (action == 2) tc.skipToNext();
        else if (isPlaying(controller)) tc.pause();
        else tc.play();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!enabled(activity)) return;
        if (!MediaWatchService.granted(activity)) {
            setVisibility(GONE);
            return;
        }
        connectSessions();
    }

    private void connectSessions() {
        if (!enabled(activity) || !MediaWatchService.granted(activity)) {
            setVisibility(GONE);
            return;
        }
        manager = (MediaSessionManager) activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (manager == null) return;
        sessionsListener = controllers -> chooseController(controllers);
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener,
                    new android.content.ComponentName(activity, MediaWatchService.class));
            chooseController(manager.getActiveSessions(new android.content.ComponentName(activity, MediaWatchService.class)));
        } catch (SecurityException ignored) {
            setVisibility(GONE);
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(progressTick);
        if (manager != null && sessionsListener != null) {
            try { manager.removeOnActiveSessionsChangedListener(sessionsListener); } catch (Exception ignored) { }
        }
        clearController();
    }

    void refreshPermissionAndSettings() {
        if (!enabled(activity) || !MediaWatchService.granted(activity)) {
            setVisibility(GONE);
            return;
        }
        if (manager == null && isAttachedToWindow()) connectSessions();
        if (manager != null && MediaWatchService.granted(activity)) {
            try {
                chooseController(manager.getActiveSessions(new android.content.ComponentName(activity, MediaWatchService.class)));
            } catch (SecurityException ignored) { setVisibility(GONE); }
        }
        refreshStyle();
    }

    private void chooseController(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState p = c.getPlaybackState();
                if (p != null && p.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
                if (best == null && p != null && p.getState() == PlaybackState.STATE_PAUSED) best = c;
            }
        }
        if (controller != null && best != null && controller.getSessionToken().equals(best.getSessionToken())) {
            updateAll();
            return;
        }
        clearController();
        controller = best;
        if (controller == null) { setVisibility(GONE); return; }
        controllerCallback = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata metadata) { updateAll(); }
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                updateAll();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) handler.post(progressTick);
            }
            @Override public void onSessionDestroyed() { clearController(); }
        };
        controller.registerCallback(controllerCallback, handler);
        updateAll();
    }

    private void clearController() {
        handler.removeCallbacks(progressTick);
        if (controller != null && controllerCallback != null) {
            try { controller.unregisterCallback(controllerCallback); } catch (Exception ignored) { }
        }
        controller = null;
        controllerCallback = null;
        setVisibility(GONE);
    }

    private long duration() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        return m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }
    private String meta(String key, String fallback) {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        CharSequence v = m == null ? null : m.getText(key);
        return v == null || v.length() == 0 ? fallback : v.toString();
    }
    private void updateAll() {
        if (controller == null) { setVisibility(GONE); return; }
        PlaybackState state = controller.getPlaybackState();
        if (state == null || (state.getState() != PlaybackState.STATE_PLAYING
                && state.getState() != PlaybackState.STATE_BUFFERING)) {
            setVisibility(GONE);
            handler.removeCallbacks(progressTick);
            return;
        }
        String song = meta(MediaMetadata.METADATA_KEY_TITLE, "Now Playing");
        String by = meta(MediaMetadata.METADATA_KEY_ARTIST, meta(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, "Unknown artist"));
        pillTitle.setText(song);
        pillArtist.setText(by);
        title.setText(song);
        artist.setText(by);
        Bitmap bitmap = null;
        MediaMetadata m = controller.getMetadata();
        if (m != null) {
            bitmap = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (bitmap == null) bitmap = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        }
        boolean showArt = showArtwork(activity);
        pillArt.setVisibility(showArt ? View.VISIBLE : View.GONE);
        art.setVisibility(showArt ? View.VISIBLE : View.GONE);
        if (bitmap != null) {
            pillArt.setImageBitmap(bitmap);
            art.setImageBitmap(bitmap);
        } else {
            pillArt.setImageResource(android.R.drawable.ic_media_play);
            art.setImageResource(android.R.drawable.ic_media_play);
        }
        setVisibility(VISIBLE);
        refreshStyle();
        updateProgress();
        handler.removeCallbacks(progressTick);
        handler.postDelayed(progressTick, 1000);
    }
    private void updateProgress() {
        if (controller == null || trackingSeek) return;
        long d = duration();
        PlaybackState s = controller.getPlaybackState();
        long p = s == null ? 0 : Math.max(0, s.getPosition());
        if (d > 0) {
            seek.setProgress((int) Math.min(1000, p * 1000 / d));
            elapsed.setText(time(p));
            remaining.setText("-" + time(Math.max(0, d - p)));
        } else {
            seek.setProgress(0);
            elapsed.setText("LIVE");
            remaining.setText("");
        }
        playPause.setText(isPlaying(controller) ? "Ⅱ" : "▶");
    }
    private String time(long ms) {
        long sec = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60);
    }
    private void setExpanded(boolean value) {
        if (controller == null || getVisibility() != VISIBLE) return;
        expanded = value;
        if (value) {
            panel.setVisibility(VISIBLE);
            pill.animate().cancel();
            panel.setAlpha(0f);
            panel.setScaleX(0.94f);
            panel.setScaleY(0.94f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.d(260)).setInterpolator(MainActivity.EASE).start();
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) getLayoutParams();
            if (p != null) { p.height = dp(250); setLayoutParams(p); }
        } else {
            panel.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(Motion.d(180))
                    .withEndAction(() -> panel.setVisibility(GONE)).start();
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) getLayoutParams();
            if (p != null) { p.height = dp(56); setLayoutParams(p); }
        }
    }
    void refreshStyle() {
        int bg = "glass".equals(style(activity)) ? 0xE62A2A30 : 0xF20A0A0C;
        pill.setBackground(background(bg, dp(28)));
        panel.setBackground(background(bg, dp(30)));
    }
}
