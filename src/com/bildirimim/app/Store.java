package com.bildirimim.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bildirimleri SharedPreferences icinde JSON olarak saklar. */
final class Store {
    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("bildirimim", Context.MODE_PRIVATE);
    }

    static synchronized JSONArray all(Context c) {
        try { return new JSONArray(sp(c).getString("items", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private static void write(Context c, JSONArray a) {
        sp(c).edit().putString("items", a.toString()).commit();
    }

    static synchronized JSONObject add(Context c, String text, long start, long end, boolean sound) throws Exception {
        SharedPreferences p = sp(c);
        int id = p.getInt("next", 1);
        p.edit().putInt("next", id + 1).commit();
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("text", text); o.put("start", start); o.put("end", end); o.put("sound", sound);
        JSONArray a = all(c);
        a.put(o);
        write(c, a);
        return o;
    }

    static synchronized JSONObject get(Context c, int id) {
        JSONArray a = all(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("id") == id) return o;
        }
        return null;
    }

    static synchronized void remove(Context c, int id) {
        JSONArray a = all(c), out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("id") != id) out.put(o);
        }
        write(c, out);
    }
}
