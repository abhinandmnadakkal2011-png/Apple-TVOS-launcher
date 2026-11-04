package com.petrobot.hume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Advances the custom wallpaper once after each real TV boot when boot rotation is selected. */
public final class WallpaperBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            CustomWallpapers.advanceForBoot(context);
        }
    }
}
