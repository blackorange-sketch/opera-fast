package org.blackorange.operafast;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Read-only search over bookmarks and history for other apps (the DynamicAI launcher).
 * content://org.blackorange.operafast.search/pages?q=text&limit=6
 * Columns: kind ("bookmark" | "history"), title, url.
 * Guarded by a dangerous permission, so the user must allow the other app explicitly.
 */
public final class SearchProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        MatrixCursor out = new MatrixCursor(new String[]{"kind", "title", "url"});
        String q = uri.getQueryParameter("q");
        if (q == null || q.trim().isEmpty() || getContext() == null) return out;
        q = q.trim();
        int limit = 6;
        try {
            limit = Math.max(1, Math.min(20, Integer.parseInt(uri.getQueryParameter("limit"))));
        } catch (Exception ignored) {
        }
        String low = q.toLowerCase(Locale.ROOT);
        HistoryDb db = HistoryDb.get(getContext());
        HashSet<String> seen = new HashSet<String>();
        int n = 0;
        for (HistoryDb.Entry e : db.bookmarks()) {
            if (n >= limit) break;
            String t = e.title == null ? "" : e.title;
            if ((e.url.toLowerCase(Locale.ROOT).contains(low)
                    || t.toLowerCase(Locale.ROOT).contains(low)) && seen.add(e.url)) {
                out.addRow(new Object[]{"bookmark", t, e.url});
                n++;
            }
        }
        List<HistoryDb.Entry> hist = db.search(q, limit);
        for (HistoryDb.Entry e : hist) {
            if (n >= limit) break;
            if (seen.add(e.url)) {
                out.addRow(new Object[]{"history", e.title == null ? "" : e.title, e.url});
                n++;
            }
        }
        return out;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.org.blackorange.operafast.page";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
