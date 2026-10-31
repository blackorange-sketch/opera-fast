package org.blackorange.operafast;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Subset of the Adblock Plus / uBlock filter syntax (EasyList, EasyPrivacy, uBlock filters):
 * network rules (||domain^, substrings, $third-party, $script ..., $popup, @@ exceptions) and
 * cosmetic rules (##selector, domain##selector) that hide elements with injected CSS.
 * Lists are downloaded at runtime and parsed on a background thread.
 */
final class Filters {
    private Filters() {}

    static final int SCRIPT = 1, IMAGE = 2, CSS = 4, XHR = 8, SUBDOC = 16, OTHER = 32, MEDIA = 64,
            FONT = 128, PING = 256;

    private static final String[][] LISTS = {
            {"easylist.txt", "https://easylist.to/easylist/easylist.txt"},
            {"easyprivacy.txt", "https://easylist.to/easylist/easyprivacy.txt"},
            {"ublock.txt", "https://ublockorigin.github.io/uAssets/filters/filters.txt"},
            {"annoyances.txt", "https://easylist.to/easylist/fanboy-annoyance.txt"},
    };

    private static final class Rule {
        String[] parts;
        boolean startA, endA;
        int types;
        int third;          // 0 any, 1 third-party only, 2 first-party only
        String[] inc;       // page domains the rule is limited to

        Rule(String pat) {
            if (pat.startsWith("|")) {
                startA = true;
                pat = pat.substring(1);
            }
            if (pat.endsWith("|")) {
                endA = true;
                pat = pat.substring(0, pat.length() - 1);
            }
            parts = pat.split("\\*", -1);
        }
    }

    private static final class RuleSet {
        final HashSet<String> domains = new HashSet<String>();
        final HashMap<String, ArrayList<Rule>> byDomain = new HashMap<String, ArrayList<Rule>>();
        final HashMap<String, ArrayList<Rule>> byToken = new HashMap<String, ArrayList<Rule>>();
        ArrayList<Rule> anyPopup;
    }

    private static final class Data {
        final RuleSet block = new RuleSet(), allow = new RuleSet(), popup = new RuleSet();
        final LinkedHashSet<String> generic = new LinkedHashSet<String>();
        final HashMap<String, ArrayList<String>> perDomain = new HashMap<String, ArrayList<String>>();
        String genericCss = "";
        int rules;
    }

    private static volatile Data data;

    static int ruleCount() {
        Data d = data;
        return d == null ? 0 : d.rules;
    }

    // ---------------------------------------------------------------- loading

    static void load(Context app) {
        Data d = new Data();
        File dir = new File(app.getFilesDir(), "filters");
        for (String[] l : LISTS) {
            File f = new File(dir, l[0]);
            if (!f.exists()) continue;
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(
                        new FileInputStream(f), "UTF-8"), 1 << 16);
                try {
                    String line;
                    while ((line = r.readLine()) != null) {
                        try {
                            parseLine(d, line.trim());
                        } catch (Exception ignored) {
                            // one bad rule must not stop the list
                        }
                    }
                } finally {
                    r.close();
                }
            } catch (Exception ignored) {
                // unreadable list: skip it
            }
        }
        StringBuilder css = new StringBuilder();
        int n = 0;
        StringBuilder grp = new StringBuilder();
        for (String s : d.generic) {
            if (grp.length() > 0) grp.append(',');
            grp.append(s);
            if (++n % 25 == 0) {
                css.append(grp).append("{display:none!important}");
                grp.setLength(0);
            }
        }
        if (grp.length() > 0) css.append(grp).append("{display:none!important}");
        d.genericCss = css.toString();
        data = d;
    }

    static boolean stale(Context app) {
        File f = new File(new File(app.getFilesDir(), "filters"), LISTS[0][0]);
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > 7L * 24 * 3600 * 1000;
    }

    /** Downloads the lists (call from a worker thread). True when at least one list was saved. */
    static boolean download(Context app) {
        File dir = new File(app.getFilesDir(), "filters");
        dir.mkdirs();
        boolean any = false;
        for (String[] l : LISTS) {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL(l[1]).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(20000);
                if (c.getResponseCode() == 200) {
                    File tmp = new File(dir, l[0] + ".tmp");
                    java.io.InputStream in = c.getInputStream();
                    FileOutputStream out = new FileOutputStream(tmp);
                    byte[] buf = new byte[1 << 15];
                    long total = 0;
                    int k;
                    while ((k = in.read(buf)) > 0) {
                        out.write(buf, 0, k);
                        total += k;
                    }
                    out.close();
                    in.close();
                    File dst = new File(dir, l[0]);
                    if (total > 5000) {
                        dst.delete();
                        any |= tmp.renameTo(dst);
                    } else {
                        tmp.delete();
                    }
                }
                c.disconnect();
            } catch (Exception ignored) {
                // try the next list
            }
        }
        if (any) load(app);
        return any;
    }

    // ---------------------------------------------------------------- parsing

    private static void parseLine(Data d, String line) {
        if (line.isEmpty()) return;
        char c0 = line.charAt(0);
        if (c0 == '!' || c0 == '[') return;
        int ci = line.indexOf("##");
        if (ci >= 0) {
            cosmetic(d, line.substring(0, ci), line.substring(ci + 2));
            return;
        }
        if (line.contains("#@#") || line.contains("#?#") || line.contains("#$#")
                || line.contains("#%#")) {
            return;
        }
        network(d, line);
    }

    private static void cosmetic(Data d, String doms, String sel) {
        if (sel.isEmpty() || sel.length() > 120 || sel.startsWith("+js") || sel.startsWith("^")
                || sel.contains(":-abp-") || sel.contains(":has-text(") || sel.contains(":contains(")
                || sel.contains(":xpath(") || sel.contains(":matches-") || sel.contains(":upward(")
                || sel.contains(":style(") || sel.contains(":remove(") || sel.contains(":watch-attr")
                || sel.contains(":min-text-length") || sel.contains(":others(")
                || sel.contains("{") || sel.contains("}")) {
            return;
        }
        if (doms.isEmpty()) {
            if (d.generic.size() < 4000) d.generic.add(sel);
            return;
        }
        String[] ds = doms.split(",");
        for (String dm : ds) {
            if (dm.startsWith("~")) return;
        }
        for (String dm : ds) {
            dm = dm.toLowerCase(Locale.ROOT);
            ArrayList<String> l = d.perDomain.get(dm);
            if (l == null) {
                l = new ArrayList<String>();
                d.perDomain.put(dm, l);
            }
            l.add(sel);
            d.rules++;
        }
    }

    private static void network(Data d, String line) {
        boolean exc = line.startsWith("@@");
        if (exc) line = line.substring(2);
        String pat = line;
        int types = 0, third = 0;
        String[] inc = null;
        boolean popup = false;
        int di = line.lastIndexOf('$');
        if (di >= 0) {
            pat = line.substring(0, di);
            for (String o : line.substring(di + 1).split(",")) {
                if (o.equals("third-party") || o.equals("3p")) third = 1;
                else if (o.equals("~third-party") || o.equals("~3p") || o.equals("first-party")
                        || o.equals("1p")) third = 2;
                else if (o.equals("script")) types |= SCRIPT;
                else if (o.equals("image")) types |= IMAGE;
                else if (o.equals("stylesheet") || o.equals("css")) types |= CSS;
                else if (o.equals("xmlhttprequest") || o.equals("xhr")) types |= XHR;
                else if (o.equals("subdocument") || o.equals("frame")) types |= SUBDOC;
                else if (o.equals("media")) types |= MEDIA;
                else if (o.equals("font")) types |= FONT;
                else if (o.equals("ping")) types |= PING;
                else if (o.equals("other") || o.equals("object") || o.equals("websocket")) types |= OTHER;
                else if (o.equals("popup")) popup = true;
                else if (o.equals("important") || o.equals("match-case") || o.equals("all")) continue;
                else if (o.startsWith("domain=")) {
                    String[] a = o.substring(7).split("\\|");
                    for (String x : a) if (x.startsWith("~")) return;
                    inc = a;
                } else return; // unsupported option: ignore the whole rule
            }
        }
        if (pat.length() > 2 && pat.startsWith("/") && pat.endsWith("/")) return; // regex
        pat = pat.toLowerCase(Locale.ROOT);
        if (pat.isEmpty() && !popup) return;
        if (popup && exc) return;
        RuleSet rs = popup ? d.popup : exc ? d.allow : d.block;
        boolean plain = types == 0 && third == 0 && inc == null;
        if (pat.isEmpty()) {
            Rule r = new Rule("*");
            r.inc = inc;
            if (rs.anyPopup == null) rs.anyPopup = new ArrayList<Rule>();
            rs.anyPopup.add(r);
            d.rules++;
            return;
        }
        if (pat.startsWith("||")) {
            String p = pat.substring(2);
            int i = 0;
            while (i < p.length()) {
                char c = p.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '-' || c == '.') i++; else break;
            }
            String domain = p.substring(0, i);
            String rest = p.substring(i);
            if (domain.isEmpty() || domain.indexOf('.') < 0) return;
            if (plain && (rest.isEmpty() || rest.equals("^"))) {
                rs.domains.add(domain);
            } else {
                Rule r = new Rule(rest.isEmpty() ? "^" : rest);
                r.types = types;
                r.third = third;
                r.inc = inc;
                ArrayList<Rule> l = rs.byDomain.get(domain);
                if (l == null) {
                    l = new ArrayList<Rule>();
                    rs.byDomain.put(domain, l);
                }
                l.add(r);
            }
            d.rules++;
            return;
        }
        String tok = token(pat);
        if (tok == null) return;
        Rule r = new Rule(pat);
        r.types = types;
        r.third = third;
        r.inc = inc;
        ArrayList<Rule> l = rs.byToken.get(tok);
        if (l == null) {
            l = new ArrayList<Rule>();
            rs.byToken.put(tok, l);
        }
        l.add(r);
        d.rules++;
    }

    /** Longest whole word of the pattern (not touching a wildcard) used as the lookup key. */
    private static String token(String pat) {
        String best = null;
        int n = pat.length();
        int i = 0;
        while (i < n) {
            if (!Character.isLetterOrDigit(pat.charAt(i))) {
                i++;
                continue;
            }
            int s = i;
            while (i < n && Character.isLetterOrDigit(pat.charAt(i))) i++;
            boolean left = s > 0 && pat.charAt(s - 1) != '*';
            boolean right = i < n && pat.charAt(i) != '*';
            if (s == 0 && pat.startsWith("|")) left = true;
            if (left && right && i - s >= 3 && (best == null || i - s > best.length())) {
                best = pat.substring(s, i);
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- matching

    static boolean blocks(String url, String pageHost, int type) {
        Data d = data;
        if (d == null || url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        String host = WebFactory.hostOf(u);
        if (host == null) return false;
        boolean third = pageHost != null && !WebFactory.sameSite(host, pageHost);
        if (!hit(d.block, u, host, pageHost, third, type)) return false;
        return !hit(d.allow, u, host, pageHost, third, type);
    }

    /** Is opening this URL as a pop-up from pageHost forbidden by a $popup rule? */
    static boolean popupBlocked(String url, String pageHost) {
        Data d = data;
        if (d == null || url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        String host = WebFactory.hostOf(u);
        if (host == null) return false;
        boolean third = pageHost != null && !WebFactory.sameSite(host, pageHost);
        if (d.popup.anyPopup != null) {
            for (Rule r : d.popup.anyPopup) if (okRule(r, pageHost, third, 0)) return true;
        }
        return hit(d.popup, u, host, pageHost, third, 0);
    }

    private static boolean okRule(Rule r, String pageHost, boolean third, int type) {
        if (r.types != 0 && type != 0 && (r.types & type) == 0) return false;
        if (r.third == 1 && !third) return false;
        if (r.third == 2 && third) return false;
        if (r.inc != null) {
            if (pageHost == null) return false;
            boolean found = false;
            for (String dom : r.inc) {
                if (pageHost.equals(dom) || pageHost.endsWith("." + dom)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean hit(RuleSet rs, String u, String host, String pageHost,
                               boolean third, int type) {
        String h = host;
        int hostEnd = u.indexOf(host);
        hostEnd = hostEnd < 0 ? 0 : hostEnd + host.length();
        while (true) {
            if (rs.domains.contains(h)) return true;
            ArrayList<Rule> l = rs.byDomain.get(h);
            if (l != null) {
                for (int i = 0; i < l.size(); i++) {
                    Rule r = l.get(i);
                    if (okRule(r, pageHost, third, type) && match(r, u, hostEnd, true)) return true;
                }
            }
            int dot = h.indexOf('.');
            if (dot < 0) break;
            h = h.substring(dot + 1);
        }
        if (rs.byToken.isEmpty()) return false;
        int n = u.length();
        int i = 0;
        while (i < n) {
            if (!Character.isLetterOrDigit(u.charAt(i))) {
                i++;
                continue;
            }
            int s = i;
            while (i < n && Character.isLetterOrDigit(u.charAt(i))) i++;
            if (i - s < 3) continue;
            ArrayList<Rule> l = rs.byToken.get(u.substring(s, i));
            if (l == null) continue;
            for (int k = 0; k < l.size(); k++) {
                Rule r = l.get(k);
                if (okRule(r, pageHost, third, type) && match(r, u, 0, r.startA)) return true;
            }
        }
        return false;
    }

    private static boolean sep(char c) {
        return !(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '%');
    }

    /** Matches one pattern part at exactly position s; returns the end index or -1. */
    private static int at(String part, String u, int s) {
        int idx = s;
        int len = u.length();
        for (int k = 0; k < part.length(); k++) {
            char c = part.charAt(k);
            if (c == '^') {
                if (idx == len) continue;
                if (!sep(u.charAt(idx))) return -1;
                idx++;
            } else {
                if (idx >= len || u.charAt(idx) != c) return -1;
                idx++;
            }
        }
        return idx;
    }

    private static boolean match(Rule r, String u, int pos, boolean anchored) {
        int len = u.length();
        int cur = pos;
        String[] parts = r.parts;
        int lastNonEmpty = -1;
        for (int i = 0; i < parts.length; i++) if (!parts[i].isEmpty()) lastNonEmpty = i;
        if (lastNonEmpty < 0) return true;
        boolean first = true;
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) {
                if (i == 0) anchored = false;
                continue;
            }
            boolean needEnd = r.endA && i == lastNonEmpty;
            int e = -1;
            if (first && anchored) {
                e = at(p, u, cur);
                if (needEnd && e != len) e = -1;
            } else {
                for (int s = cur; s <= len; s++) {
                    e = at(p, u, s);
                    if (e >= 0 && (!needEnd || e == len)) break;
                    e = -1;
                }
            }
            if (e < 0) return false;
            cur = e;
            first = false;
        }
        return true;
    }

    // ---------------------------------------------------------------- cosmetic

    /** CSS hiding the known ad elements for this page host ("" when there is nothing). */
    static String cssFor(String host) {
        Data d = data;
        if (d == null || host == null) return "";
        StringBuilder sb = new StringBuilder(d.genericCss);
        String h = host.toLowerCase(Locale.ROOT);
        while (true) {
            ArrayList<String> l = d.perDomain.get(h);
            if (l != null) {
                StringBuilder grp = new StringBuilder();
                int n = 0;
                for (String s : l) {
                    if (grp.length() > 0) grp.append(',');
                    grp.append(s);
                    if (++n % 20 == 0) {
                        sb.append(grp).append("{display:none!important}");
                        grp.setLength(0);
                    }
                }
                if (grp.length() > 0) sb.append(grp).append("{display:none!important}");
            }
            int dot = h.indexOf('.');
            if (dot < 0) break;
            h = h.substring(dot + 1);
        }
        return sb.toString();
    }
}
