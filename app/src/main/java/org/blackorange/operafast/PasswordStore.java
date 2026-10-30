package org.blackorange.operafast;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Saved logins. Passwords are AES-GCM encrypted with a key that never leaves the Android Keystore. */
final class PasswordStore {
    private PasswordStore() {}

    static final class Item {
        String host;
        String user;
        String pass;   // decrypted (empty if it could not be decrypted)
        long time;
    }

    private static final String ALIAS = "operafast_pw";
    private static SharedPreferences prefs;

    static void init(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("passwords", Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    private static String enc(String plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }

    private static String dec(String b64) {
        try {
            byte[] all = Base64.decode(b64, Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static JSONArray load() {
        try {
            return new JSONArray(prefs.getString("items", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void store(JSONArray a) {
        prefs.edit().putString("items", a.toString()).apply();
    }

    static synchronized List<Item> list() {
        List<Item> out = new ArrayList<Item>();
        if (prefs == null) return out;
        JSONArray a = load();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Item it = new Item();
            it.host = o.optString("h");
            it.user = o.optString("u");
            it.pass = "";
            it.time = o.optLong("t");
            out.add(it);
        }
        return out;
    }

    /** Newest login for this host with its password decrypted, or null. */
    static synchronized Item find(String host, String user) {
        if (prefs == null || host == null) return null;
        JSONArray a = load();
        JSONObject best = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !host.equals(o.optString("h"))) continue;
            if (user != null && !user.equals(o.optString("u"))) continue;
            if (best == null || o.optLong("t") > best.optLong("t")) best = o;
        }
        if (best == null) return null;
        Item it = new Item();
        it.host = best.optString("h");
        it.user = best.optString("u");
        it.pass = dec(best.optString("p"));
        it.time = best.optLong("t");
        return it;
    }

    static synchronized boolean save(String host, String user, String pass) {
        if (prefs == null || host == null || pass == null) return false;
        try {
            JSONArray a = load();
            JSONArray n = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (host.equals(o.optString("h")) && user.equals(o.optString("u"))) continue;
                n.put(o);
            }
            JSONObject o = new JSONObject();
            o.put("h", host);
            o.put("u", user);
            o.put("p", enc(pass));
            o.put("t", System.currentTimeMillis());
            n.put(o);
            store(n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static synchronized void delete(String host, String user) {
        if (prefs == null) return;
        JSONArray a = load();
        JSONArray n = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (host.equals(o.optString("h")) && user.equals(o.optString("u"))) continue;
            n.put(o);
        }
        store(n);
    }

    static synchronized void clear() {
        if (prefs != null) prefs.edit().remove("items").apply();
    }
}
