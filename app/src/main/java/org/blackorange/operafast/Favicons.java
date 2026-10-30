package org.blackorange.operafast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Site icons for the start page: fetched straight from the site once, then cached on disk. */
final class Favicons {
    private Favicons() {}

    interface Callback {
        void onIcon(Bitmap bmp);
    }

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler UI_THREAD = new Handler(Looper.getMainLooper());

    private static File fileFor(Context c, String host) {
        File dir = new File(c.getFilesDir(), "icons");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, Integer.toHexString(host.hashCode()) + ".png");
    }

    static void load(final Context ctx, final String url, final Callback cb) {
        final String host = WebFactory.hostOf(url);
        if (host == null) return;
        final Context app = ctx.getApplicationContext();
        IO.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = get(app, host);
                if (b != null) {
                    UI_THREAD.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onIcon(b);
                        }
                    });
                }
            }
        });
    }

    private static Bitmap get(Context c, String host) {
        File f = fileFor(c, host);
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) return b;
        }
        String[] tries = {"https://" + host + "/apple-touch-icon.png",
                "https://" + host + "/favicon.ico"};
        for (String u : tries) {
            Bitmap b = fetch(u);
            if (b != null && b.getWidth() >= 24) {
                Bitmap s = Bitmap.createScaledBitmap(b, 128, 128, true);
                try {
                    FileOutputStream out = new FileOutputStream(f);
                    try {
                        s.compress(Bitmap.CompressFormat.PNG, 100, out);
                    } finally {
                        out.close();
                    }
                } catch (Exception ignored) {
                }
                return s;
            }
        }
        return null;
    }

    private static Bitmap fetch(String u) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            try {
                return BitmapFactory.decodeStream(in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
