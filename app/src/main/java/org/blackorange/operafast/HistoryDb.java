package org.blackorange.operafast;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Browsing history: one row per URL, newest visit first. Writes go through one IO thread. */
final class HistoryDb extends SQLiteOpenHelper {

    static final class Entry {
        long id;
        String url;
        String title;
        long time;
    }

    private static HistoryDb instance;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    static synchronized HistoryDb get(Context c) {
        if (instance == null) instance = new HistoryDb(c.getApplicationContext());
        return instance;
    }

    private HistoryDb(Context c) {
        super(c, "history.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "url TEXT NOT NULL UNIQUE,"
                + "title TEXT,"
                + "last_visit INTEGER NOT NULL,"
                + "visits INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE INDEX idx_history_last ON history(last_visit DESC)");
        createBookmarks(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createBookmarks(db);
    }

    private static void createBookmarks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "url TEXT NOT NULL UNIQUE,"
                + "title TEXT,"
                + "added INTEGER NOT NULL)");
    }

    // ---- bookmarks (tiny, so done synchronously: the UI reads the result right away)

    boolean isBookmarked(String url) {
        if (url == null) return false;
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT 1 FROM bookmarks WHERE url=?", new String[]{url});
            return c.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    void addBookmark(String url, String title) {
        try {
            getWritableDatabase().execSQL(
                    "INSERT OR REPLACE INTO bookmarks(url,title,added) VALUES(?,?,?)",
                    new Object[]{url, title == null ? "" : title, System.currentTimeMillis()});
        } catch (Exception ignored) {
        }
    }

    void removeBookmark(String url) {
        try {
            getWritableDatabase().execSQL("DELETE FROM bookmarks WHERE url=?", new Object[]{url});
        } catch (Exception ignored) {
        }
    }

    List<Entry> bookmarks() {
        List<Entry> out = new ArrayList<Entry>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT _id,url,title,added FROM bookmarks ORDER BY added DESC", null);
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getLong(0);
                e.url = c.getString(1);
                e.title = c.getString(2);
                e.time = c.getLong(3);
                out.add(e);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    static boolean isRecordable(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    void visit(final String url, final String title) {
        if (!isRecordable(url)) return;
        IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLiteDatabase db = getWritableDatabase();
                    long now = System.currentTimeMillis();
                    String t = title == null ? "" : title;
                    db.execSQL("INSERT OR IGNORE INTO history(url,title,last_visit,visits) "
                            + "VALUES(?,?,?,1)", new Object[]{url, t, now});
                    db.execSQL("UPDATE history SET last_visit=?, visits=visits+1, "
                            + "title=CASE WHEN ?='' THEN title ELSE ? END "
                            + "WHERE url=? AND last_visit<>?", new Object[]{now, t, t, url, now});
                } catch (Exception ignored) {
                }
            }
        });
    }

    void setTitle(final String url, final String title) {
        if (!isRecordable(url) || title == null || title.isEmpty()) return;
        IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    getWritableDatabase().execSQL(
                            "UPDATE history SET title=? WHERE url=?", new Object[]{title, url});
                } catch (Exception ignored) {
                }
            }
        });
    }

    List<Entry> recent(int limit) {
        List<Entry> out = new ArrayList<Entry>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT _id,url,title,last_visit FROM history "
                            + "ORDER BY last_visit DESC LIMIT " + limit, null);
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getLong(0);
                e.url = c.getString(1);
                e.title = c.getString(2);
                e.time = c.getLong(3);
                out.add(e);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    void delete(final long id) {
        IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    getWritableDatabase().execSQL(
                            "DELETE FROM history WHERE _id=?", new Object[]{id});
                } catch (Exception ignored) {
                }
            }
        });
    }

    void clear() {
        IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    getWritableDatabase().execSQL("DELETE FROM history");
                } catch (Exception ignored) {
                }
            }
        });
    }
}
