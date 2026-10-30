package org.blackorange.operafast;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.URLUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Thin wrapper over the system DownloadManager (it already handles notifications, resume, retries). */
final class Downloads {
    private Downloads() {}

    static final class Item {
        long id;
        String title;
        int status;
        long total;
        long done;
        String mime;
        long modified;
    }

    private static DownloadManager dm(Context c) {
        return (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /** Returns the file name that was requested. */
    static String enqueue(Context c, String url, String userAgent, String contentDisposition,
                          String mime) {
        Uri uri = Uri.parse(url);
        String name = URLUtil.guessFileName(url, contentDisposition, mime);
        DownloadManager.Request r = new DownloadManager.Request(uri);
        if (mime != null && !mime.isEmpty()) r.setMimeType(mime);
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null) r.addRequestHeader("Cookie", cookie);
        if (userAgent != null) r.addRequestHeader("User-Agent", userAgent);
        r.setTitle(name);
        r.setDescription(UI.hostOf(url));
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
        dm(c).enqueue(r);
        return name;
    }

    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<Item>();
        Cursor cur = null;
        try {
            cur = dm(c).query(new DownloadManager.Query());
            if (cur == null) return out;
            int iId = cur.getColumnIndex(DownloadManager.COLUMN_ID);
            int iTitle = cur.getColumnIndex(DownloadManager.COLUMN_TITLE);
            int iStatus = cur.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int iTotal = cur.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
            int iDone = cur.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
            int iMime = cur.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
            int iMod = cur.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);
            while (cur.moveToNext()) {
                Item it = new Item();
                it.id = cur.getLong(iId);
                it.title = cur.getString(iTitle);
                it.status = cur.getInt(iStatus);
                it.total = cur.getLong(iTotal);
                it.done = cur.getLong(iDone);
                it.mime = cur.getString(iMime);
                it.modified = cur.getLong(iMod);
                out.add(it);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.modified, a.modified);
            }
        });
        return out;
    }

    /** Deletes the entry and (for finished downloads) the file. */
    static void remove(Context c, long id) {
        try {
            dm(c).remove(id);
        } catch (Exception ignored) {
        }
    }

    static boolean open(Context c, Item it) {
        try {
            Uri u = dm(c).getUriForDownloadedFile(it.id);
            if (u == null) return false;
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, it.mime == null || it.mime.isEmpty() ? "*/*" : it.mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String statusText(Item it) {
        switch (it.status) {
            case DownloadManager.STATUS_SUCCESSFUL:
                return "Completed" + (it.total > 0 ? " · " + size(it.total) : "");
            case DownloadManager.STATUS_FAILED:
                return "Failed";
            case DownloadManager.STATUS_PAUSED:
                return "Paused";
            case DownloadManager.STATUS_PENDING:
                return "Waiting…";
            default:
                if (it.total > 0) {
                    int pct = (int) (it.done * 100 / it.total);
                    return "Downloading " + pct + "% · " + size(it.done) + " / " + size(it.total);
                }
                return "Downloading… " + size(it.done);
        }
    }

    private static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(java.util.Locale.US, "%.0f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", b / (1024.0 * 1024));
        }
        return String.format(java.util.Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024));
    }
}
