package com.petrobot.hume;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

/**
 * Custom soft glass navigation sounds. Android's own view click sounds are silenced separately,
 * so these are the only navigation sounds. Settings -> Home -> Navigation sounds.
 */
final class Clicks {
    private static AudioManager audio;

    private Clicks() {
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("sounds", Context.MODE_PRIVATE).getBoolean("nav_sounds", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("sounds", Context.MODE_PRIVATE).edit().putBoolean("nav_sounds", on).apply();
    }

    static void init(Context c) {
        if (audio == null) audio = c.getApplicationContext().getSystemService(AudioManager.class);
        GlassSounds.init(c.getApplicationContext());
    }

    /** Focus moved: a light glass tick, only when the navigation-sounds setting is on. */
    static void move(Context c, int keyCode) {
        if (enabled(c)) GlassSounds.playMove();
    }

    /** Something was chosen: a slightly deeper glass tap. */
    static void select(Context c) {
        if (enabled(c)) GlassSounds.playSelect();
    }

    /** Back uses the same quiet glass tap. */
    static void back(Context c) {
        if (enabled(c)) GlassSounds.playSelect();
    }

    // ------------------------------------------------------------ Android's own sounds

    /*
     * Android plays its own click (on every View click) and focus sounds (when it moves focus for a
     * D-pad press) according to the TV's system setting, not Hume's. So Hume silences them on its
     * views and moves focus itself; its own sounds above, governed by the Sounds switch, are then
     * the only ones (and there are no double clicks when it is on).
     */

    private static final android.view.ViewGroup.OnHierarchyChangeListener QUIET =
            new android.view.ViewGroup.OnHierarchyChangeListener() {
                @Override public void onChildViewAdded(android.view.View parent, android.view.View child) {
                    quiet(child);
                }

                @Override public void onChildViewRemoved(android.view.View parent, android.view.View child) {
                }
            };

    /** Silences Android's click sound on a view tree, including views added to it later. */
    static void quiet(android.view.View v) {
        v.setSoundEffectsEnabled(false);
        if (!(v instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        // These widgets rely on their own hierarchy listener; their children are still silenced.
        if (!(g instanceof android.widget.RadioGroup) && !(g instanceof android.widget.TableLayout)) {
            g.setOnHierarchyChangeListener(QUIET);
        }
        for (int i = 0; i < g.getChildCount(); i++) quiet(g.getChildAt(i));
    }

    /**
     * A D-pad press nothing else used: move focus here, so Android doesn't (with its own sound).
     * Returns true when focus moved.
     */
    static boolean moveFocus(android.view.Window w, KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN || w == null) return false;
        int dir;
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = android.view.View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = android.view.View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = android.view.View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = android.view.View.FOCUS_RIGHT; break;
            default: return false;
        }
        android.view.View f = w.getDecorView().findFocus();
        if (f == null) return false;
        android.view.View n = f.focusSearch(dir);
        return n != null && n != f && n.requestFocus(dir);
    }

    /** Hume's dialog builder (system dark alert style), following the Sounds switch like the rest of Hume. */
    static android.app.AlertDialog.Builder dialog(Context c) {
        return new android.app.AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert) {
            @Override public android.app.AlertDialog create() {
                android.app.AlertDialog d = super.create();
                quiet(d);
                return d;
            }
        };
    }

    /** A dialog that follows the Sounds switch too (its own window). */
    static void quiet(android.app.Dialog d) {
        d.setOnShowListener(x -> {
            if (d.getWindow() != null) quiet(d.getWindow().getDecorView());
        });
    }
}
