package com.petrobot.hume;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** SAF-backed custom home wallpapers. No broad storage permission is required. */
final class CustomWallpapers {
    static final int DEFAULT_INTERVAL_SECONDS = 30;
    private static final String PREFS = "custom_wallpapers";
    private static final String TREE = "tree_uri";
    private static final String FILES = "image_uris";
    private static final String ENABLED = "enabled";
    private static final String MODE = "rotation_mode";
    private static final String INTERVAL = "rotation_interval";
    private static final String INDEX = "rotation_index";

    private CustomWallpapers() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(ENABLED, true);
    }

    static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(ENABLED, enabled).apply();
    }

    /** "timed" or "boot". Timed is the first-use default. */
    static String mode(Context c) {
        return prefs(c).getString(MODE, "timed");
    }

    static void setMode(Context c, String mode) {
        prefs(c).edit().putString(MODE, "boot".equals(mode) ? "boot" : "timed").apply();
    }

    static int intervalSeconds(Context c) {
        int value = prefs(c).getInt(INTERVAL, DEFAULT_INTERVAL_SECONDS);
        return value == 15 || value == 30 || value == 60 || value == 300
                ? value : DEFAULT_INTERVAL_SECONDS;
    }

    static void setIntervalSeconds(Context c, int seconds) {
        if (seconds == 15 || seconds == 30 || seconds == 60 || seconds == 300) {
            prefs(c).edit().putInt(INTERVAL, seconds).apply();
        }
    }

    static int index(Context c) {
        return Math.max(0, prefs(c).getInt(INDEX, 0));
    }

    static void setIndex(Context c, int index) {
        prefs(c).edit().putInt(INDEX, Math.max(0, index)).apply();
    }

    static String folderLabel(Context c) {
        String uri = prefs(c).getString(TREE, "");
        int count = images(c).size();
        return count == 0 ? (uri.isEmpty() ? "No images selected" : "Folder selected (empty)")
                : count + (count == 1 ? " image selected" : " images selected");
    }

    static boolean hasFolder(Context c) {
        return !prefs(c).getString(TREE, "").isEmpty()
                || !prefs(c).getStringSet(FILES, java.util.Collections.<String>emptySet()).isEmpty();
    }

    /** Store a folder selected through ACTION_OPEN_DOCUMENT_TREE and keep access across reboots. */
    static boolean saveFolder(Context c, Uri uri, int resultFlags) {
        if (uri == null) return false;
        int takeFlags = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            c.getContentResolver().takePersistableUriPermission(uri, takeFlags
                    & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // Some TV document providers do not expose persistable grants; the URI is still saved.
        }
        prefs(c).edit().putString(TREE, uri.toString()).putInt(INDEX, 0).apply();
        return true;
    }

    /** Save individually selected image URIs from TVs that do not provide a folder picker. */
    static boolean saveImages(Context c, List<Uri> uris, int resultFlags) {
        if (uris == null || uris.isEmpty()) return false;
        java.util.Set<String> saved = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        int takeFlags = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        for (Uri uri : uris) {
            if (uri == null) continue;
            try {
                c.getContentResolver().takePersistableUriPermission(uri,
                        takeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            saved.add(uri.toString());
        }
        if (saved.isEmpty()) return false;
        prefs(c).edit().remove(TREE).putStringSet(FILES, saved).putInt(INDEX, 0).apply();
        return true;
    }

    static void clearFolder(Context c) {
        String saved = prefs(c).getString(TREE, "");
        if (!saved.isEmpty()) {
            try {
                c.getContentResolver().releasePersistableUriPermission(
                        Uri.parse(saved), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
        }
        java.util.Set<String> files = prefs(c).getStringSet(FILES, java.util.Collections.<String>emptySet());
        for (String file : files) {
            try { c.getContentResolver().releasePersistableUriPermission(
                    Uri.parse(file), Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) { }
        }
        prefs(c).edit().remove(TREE).remove(FILES).putInt(INDEX, 0).apply();
    }

    /** Lists direct image files in the selected folder in a stable alphabetical order. */
    static List<String> images(Context c) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> selected = prefs(c).getStringSet(FILES, java.util.Collections.<String>emptySet());
        out.addAll(selected);
        String saved = prefs(c).getString(TREE, "");
        if (saved.isEmpty()) return out;
        Cursor cursor = null;
        try {
            Uri tree = Uri.parse(saved);
            String documentId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
            String[] columns = {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            };
            cursor = c.getContentResolver().query(children, columns, null, null, null);
            if (cursor == null) return out;
            int idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String id = idCol >= 0 ? cursor.getString(idCol) : null;
                String name = nameCol >= 0 ? cursor.getString(nameCol) : "";
                String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : "";
                if (id == null || !isImage(name, mime)) continue;
                Uri file = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                out.add(file.toString());
            }
        } catch (Exception ignored) {
            // A removed USB drive or revoked folder permission should fall back to the normal carousel.
        } finally {
            if (cursor != null) cursor.close();
        }
        Collections.sort(out, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                return a.compareToIgnoreCase(b);
            }
        });
        return out;
    }

    private static boolean isImage(String name, String mime) {
        if (mime != null && mime.toLowerCase().startsWith("image/")) return true;
        String n = name == null ? "" : name.toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".webp") || n.endsWith(".bmp") || n.endsWith(".gif");
    }

    /** Decode near screen resolution rather than loading a full camera image into TV memory. */
    static Bitmap decode(Context c, String uriString, int targetWidth, int targetHeight) {
        if (uriString == null || uriString.isEmpty()) return null;
        android.content.ContentResolver resolver = c.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        java.io.InputStream first = null;
        try {
            first = resolver.openInputStream(Uri.parse(uriString));
            if (first == null) return null;
            BitmapFactory.decodeStream(first, null, bounds);
        } catch (Exception e) {
            return null;
        } finally {
            if (first != null) try { first.close(); } catch (Exception ignored) { }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        int tw = Math.max(1, targetWidth);
        int th = Math.max(1, targetHeight);
        while (bounds.outWidth / (sample * 2) >= tw
                && bounds.outHeight / (sample * 2) >= th) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        java.io.InputStream second = null;
        try {
            second = resolver.openInputStream(Uri.parse(uriString));
            return second == null ? null : BitmapFactory.decodeStream(second, null, opts);
        } catch (Exception e) {
            return null;
        } finally {
            if (second != null) try { second.close(); } catch (Exception ignored) { }
        }
    }

    /** Advance one image for the boot-rotation mode. A single image remains unchanged. */
    static void advanceForBoot(Context c) {
        if (!enabled(c) || !"boot".equals(mode(c))) return;
        List<String> items = images(c);
        if (items.size() < 2) return;
        setIndex(c, (index(c) + 1) % items.size());
    }
}
