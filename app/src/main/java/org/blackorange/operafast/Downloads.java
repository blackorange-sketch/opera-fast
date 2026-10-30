package org.blackorange.operafast;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.URLUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Own download manager: resumable (HTTP Range), pausable, retry, progress notification
 * through a foreground service. Partial files live in the app's private storage and are
 * copied to the public Downloads folder when finished.
 */
final class Downloads {
    private Downloads() {}

    static final int QUEUED = 0, RUNNING = 1, PAUSED = 2, DONE = 3, FAILED = 4;
    static final String CHANNEL = "downloads";

    static final class Item {
        long id;
        String url, name, mime, ua, folder;
        int status;
        long total, done, time;
        String saved;           // content:// uri (or path) of the finished file
        volatile boolean stop;  // set to pause / cancel the running worker
        boolean cancelled;
    }

    private static final List<Item> items = new ArrayList<Item>();
    private static final Map<Long, Item> running = new HashMap<Long, Item>();
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    private static Context app;
    private static File store;
    private static long lastNotify;

    static synchronized void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        store = new File(app.getFilesDir(), "downloads.json");
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(store);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            JSONArray a = new JSONArray(bo.toString("UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.id = o.getLong("id");
                it.url = o.getString("url");
                it.name = o.optString("name", "download");
                it.mime = o.optString("mime", "");
                it.folder = o.optString("folder", "");
                it.status = o.optInt("status", PAUSED);
                it.total = o.optLong("total");
                it.done = o.optLong("done");
                it.time = o.optLong("time");
                it.saved = o.has("saved") ? o.getString("saved") : null;
                // anything that was running when the app died can be resumed
                if (it.status == RUNNING || it.status == QUEUED) it.status = PAUSED;
                items.add(it);
            }
        } catch (Exception ignored) {
        }
    }

    private static synchronized void persist() {
        try {
            JSONArray a = new JSONArray();
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("url", it.url);
                o.put("name", it.name);
                o.put("mime", it.mime == null ? "" : it.mime);
                o.put("folder", it.folder == null ? "" : it.folder);
                o.put("status", it.status);
                o.put("total", it.total);
                o.put("done", it.done);
                o.put("time", it.time);
                if (it.saved != null) o.put("saved", it.saved);
                a.put(o);
            }
            FileOutputStream out = new FileOutputStream(store);
            out.write(a.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ public API

    /** Starts a download; returns the file name that was chosen. */
    static String enqueue(Context c, String url, String userAgent, String contentDisposition,
                          String mime) {
        init(c);
        String name = URLUtil.guessFileName(url, contentDisposition, mime).replace('/', '_');
        Item it = new Item();
        it.id = System.currentTimeMillis();
        it.url = url;
        it.name = name;
        it.mime = mime == null ? "" : mime;
        it.ua = userAgent;
        it.folder = Settings.dlFolder();
        it.time = it.id;
        it.status = QUEUED;
        synchronized (Downloads.class) {
            items.add(it);
        }
        persist();
        launch(it);
        return name;
    }

    static synchronized List<Item> list() {
        List<Item> out = new ArrayList<Item>(items);
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.time, a.time);
            }
        });
        return out;
    }

    static void pause(Item it) {
        it.stop = true;   // the worker notices, flushes and marks the item as paused
        synchronized (Downloads.class) {
            if (it.status == QUEUED) it.status = PAUSED;
        }
        persist();
    }

    /** Resume a paused download or retry a failed one. */
    static void resume(Item it) {
        synchronized (Downloads.class) {
            if (running.containsKey(it.id)) return;
            it.status = QUEUED;
        }
        launch(it);
    }

    /** Removes the entry; a running download is cancelled and its partial file deleted. */
    static void remove(Item it, boolean deleteFile) {
        it.stop = true;
        it.cancelled = true;
        synchronized (Downloads.class) {
            items.remove(it);
        }
        if (it.status != DONE) {
            partFile(it).delete();
        } else if (deleteFile && it.saved != null && app != null) {
            try {
                if (it.saved.startsWith("content:")) {
                    app.getContentResolver().delete(Uri.parse(it.saved), null, null);
                } else {
                    new File(it.saved).delete();
                }
            } catch (Exception ignored) {
            }
        }
        persist();
    }

    static synchronized int activeCount() {
        return running.size();
    }

    static boolean open(Context c, Item it) {
        try {
            if (it.saved == null) return false;
            Uri u = it.saved.startsWith("content:") ? Uri.parse(it.saved) : Uri.fromFile(new File(it.saved));
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
            case DONE:
                return S.t("Completed") + (it.total > 0 ? " · " + size(it.total) : "");
            case FAILED:
                return S.t("Failed") + " · " + S.t("tap to retry");
            case PAUSED:
                return S.t("Paused") + " · " + size(it.done)
                        + (it.total > 0 ? " / " + size(it.total) : "");
            case QUEUED:
                return S.t("Waiting…");
            default:
                if (it.total > 0) {
                    int pct = (int) (it.done * 100 / it.total);
                    return S.t("Downloading ") + pct + "% · " + size(it.done) + " / " + size(it.total);
                }
                return S.t("Downloading… ") + size(it.done);
        }
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(java.util.Locale.US, "%.0f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", b / (1024.0 * 1024));
        }
        return String.format(java.util.Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024));
    }

    // ------------------------------------------------------------------ engine

    private static File partFile(Item it) {
        File dir = app.getExternalFilesDir("downloads");
        if (dir == null) dir = new File(app.getFilesDir(), "downloads");
        dir.mkdirs();
        return new File(dir, it.id + ".part");
    }

    private static void launch(final Item it) {
        it.stop = false;
        synchronized (Downloads.class) {
            running.put(it.id, it);
        }
        startService();
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    work(it);
                } catch (Throwable t) {
                    if (it.status == RUNNING || it.status == QUEUED) it.status = FAILED;
                } finally {
                    synchronized (Downloads.class) {
                        running.remove(it.id);
                    }
                    persist();
                    notifyProgress(true);
                    if (activeCount() == 0) stopService();
                }
            }
        });
    }

    private static void work(Item it) throws Exception {
        it.status = RUNNING;
        File part = partFile(it);
        long have = part.exists() ? part.length() : 0;
        HttpURLConnection con = (HttpURLConnection) new URL(it.url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setInstanceFollowRedirects(true);
        if (it.ua != null) con.setRequestProperty("User-Agent", it.ua);
        String cookie = CookieManager.getInstance().getCookie(it.url);
        if (cookie != null) con.setRequestProperty("Cookie", cookie);
        if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-");
        int code = con.getResponseCode();
        boolean append = false;
        if (code == 206) {
            append = true;
            String cr = con.getHeaderField("Content-Range");   // bytes 100-999/1000
            long tot = -1;
            if (cr != null && cr.indexOf('/') > 0) {
                try {
                    tot = Long.parseLong(cr.substring(cr.indexOf('/') + 1).trim());
                } catch (Exception ignored) {
                }
            }
            it.total = tot > 0 ? tot : have + con.getContentLengthLong();
        } else if (code == 200) {
            have = 0;
            it.total = con.getContentLengthLong();
        } else if (code == 416 && have > 0) {
            it.total = have;   // already complete
            con.disconnect();
            finish(it, part);
            return;
        } else {
            con.disconnect();
            it.status = FAILED;
            return;
        }
        it.done = have;
        InputStream in = con.getInputStream();
        OutputStream out = new FileOutputStream(part, append);
        byte[] buf = new byte[64 * 1024];
        int n;
        long lastUi = 0;
        try {
            while (!it.stop && (n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                it.done += n;
                long now = System.currentTimeMillis();
                if (now - lastUi > 700) {
                    lastUi = now;
                    notifyProgress(false);
                }
            }
        } finally {
            try {
                out.close();
            } catch (Exception ignored) {
            }
            try {
                in.close();
            } catch (Exception ignored) {
            }
            con.disconnect();
        }
        if (it.cancelled) {
            part.delete();
            return;
        }
        if (it.stop) {
            it.status = PAUSED;
            return;
        }
        if (it.total > 0 && it.done < it.total) {
            it.status = PAUSED;   // connection dropped: can be resumed
            return;
        }
        finish(it, part);
    }

    private static void finish(final Item it, File part) throws Exception {
        String name = it.name;
        boolean tree = it.folder != null && it.folder.startsWith("content:");
        if (tree) {
            try {
                Uri t = Uri.parse(it.folder);
                Uri dir = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        t, android.provider.DocumentsContract.getTreeDocumentId(t));
                String mime = it.mime == null || it.mime.isEmpty() ? "application/octet-stream" : it.mime;
                Uri doc = android.provider.DocumentsContract.createDocument(
                        app.getContentResolver(), dir, mime, name);
                if (doc != null) {
                    java.io.FileInputStream in = new java.io.FileInputStream(part);
                    OutputStream out = app.getContentResolver().openOutputStream(doc);
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.close();
                    in.close();
                    it.saved = doc.toString();
                    part.delete();
                    it.done = it.total > 0 ? it.total : it.done;
                    it.status = DONE;
                    return;
                }
            } catch (Exception ignored) {
                // folder gone or permission revoked: fall back to the Downloads folder
            }
        }
        String sub = "";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = app.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            if (it.mime != null && !it.mime.isEmpty()) v.put(MediaStore.MediaColumns.MIME_TYPE, it.mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + sub);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                it.status = FAILED;
                return;
            }
            java.io.FileInputStream in = new java.io.FileInputStream(part);
            OutputStream out = cr.openOutputStream(uri);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            it.saved = uri.toString();
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), sub);
            dir.mkdirs();
            File dst = new File(dir, name);
            for (int i = 1; dst.exists(); i++) {
                int dot = name.lastIndexOf('.');
                dst = new File(dir, dot > 0 ? name.substring(0, dot) + " (" + i + ")" + name.substring(dot)
                        : name + " (" + i + ")");
            }
            java.io.FileInputStream in = new java.io.FileInputStream(part);
            OutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            it.saved = dst.getAbsolutePath();
            MediaScannerConnection.scanFile(app, new String[]{dst.getAbsolutePath()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            if (uri != null) {
                                it.saved = uri.toString();
                                persist();
                            }
                        }
                    });
        }
        part.delete();
        it.done = it.total > 0 ? it.total : it.done;
        it.status = DONE;
    }

    // ------------------------------------------------------------------ notification / service

    private static void startService() {
        try {
            Intent i = new Intent(app, DownloadService.class);
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
            else app.startService(i);
        } catch (Exception ignored) {
        }
    }

    private static void stopService() {
        try {
            app.stopService(new Intent(app, DownloadService.class));
        } catch (Exception ignored) {
        }
    }

    static Notification buildNotification(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, S.t("Downloads"),
                        NotificationManager.IMPORTANCE_LOW));
            }
        }
        long done = 0, total = 0;
        int count = 0;
        boolean known = true;
        synchronized (Downloads.class) {
            for (Item it : running.values()) {
                count++;
                done += it.done;
                if (it.total > 0) total += it.total;
                else known = false;
            }
        }
        Intent open = new Intent(c, MainActivity.class).setAction("org.blackorange.operafast.DOWNLOADS")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(S.t("Downloading ") + count)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi);
        if (total > 0 && known) {
            int pct = (int) (done * 100 / total);
            b.setProgress(100, pct, false).setContentText(pct + "%  ·  " + size(done) + " / " + size(total));
        } else {
            b.setProgress(0, 0, true).setContentText(size(done));
        }
        return b.build();
    }

    private static void notifyProgress(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotify < 800) return;
        lastNotify = now;
        try {
            if (activeCount() == 0) return;
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(DownloadService.NOTIF_ID, buildNotification(app));
        } catch (Exception ignored) {
        }
    }
}
