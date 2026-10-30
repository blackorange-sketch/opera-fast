package org.blackorange.operafast;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Search-engine query suggestions (all four engines answer in the ["q",["a","b"]] format). */
final class Suggest {
    private Suggest() {}

    private static final String[] URLS = {
            "https://suggestqueries.google.com/complete/search?client=firefox&q=",
            "https://duckduckgo.com/ac/?type=list&q=",
            "https://api.bing.com/osjson.aspx?query=",
            "https://search.brave.com/api/suggest?q="};

    /** Blocking; call off the main thread. Returns an empty list on any failure. */
    static List<String> fetch(String query) {
        List<String> out = new ArrayList<String>();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(
                    URLS[Settings.engine()] + URLEncoder.encode(query, "UTF-8")).openConnection();
            c.setConnectTimeout(2500);
            c.setReadTimeout(2500);
            c.setRequestProperty("Accept-Language", Settings.uk() ? "uk,en;q=0.7" : "en");
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[2048];
            int n;
            while ((n = r.read(buf)) > 0 && sb.length() < 64 * 1024) sb.append(buf, 0, n);
            JSONArray a = new JSONArray(sb.toString());
            JSONArray list = a.getJSONArray(1);
            for (int i = 0; i < list.length() && out.size() < 6; i++) {
                String s = list.optString(i, "");
                if (!s.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {
            out.clear();
        } finally {
            if (c != null) c.disconnect();
        }
        return out;
    }
}
