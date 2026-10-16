package dev.seventwo.launcher;

import android.app.NotificationManager;
import android.content.*;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Context persistence (prefs key "contexts") and the on-device signal readers. */
final class ContextStore {
    static final int MAX_RULES = 24, MAX_WIDGETS = 8;

    static ArrayList<ContextRules.Rule> load(SharedPreferences prefs) {
        ArrayList<ContextRules.Rule> rules = new ArrayList<>();
        String raw = prefs.getString("contexts", null);
        if (raw == null) {
            // First run after upgrade: the old header/media settings become the everyday context.
            rules.add(ContextRules.everyday(prefs.getInt("header", 20) > 0, prefs.getBoolean("media", false)));
            return rules;
        }
        try { rules.addAll(parse(raw)); } catch (Exception ignored) { }
        ContextRules.normalize(rules);
        return rules;
    }

    static void save(SharedPreferences prefs, List<ContextRules.Rule> rules) {
        prefs.edit().putString("contexts", write(rules).toString()).apply();
    }

    static JSONArray write(List<ContextRules.Rule> rules) {
        JSONArray out = new JSONArray();
        try {
            for (ContextRules.Rule r : rules) {
                JSONObject o = new JSONObject().put("id", r.id).put("name", r.name).put("enabled", r.enabled).put("fallback", r.fallback)
                    .put("days", r.days).put("start", r.start).put("end", r.end).put("headphones", r.headphones).put("charging", r.charging)
                    .put("dnd", r.dnd).put("playing", r.playing).put("category", r.category).put("header", r.header).put("widgets", new JSONArray(r.widgets));
                out.put(o);
            }
        } catch (JSONException e) { throw new IllegalStateException(e); }
        return out;
    }

    /** Strict parse; also used to validate backups. */
    static ArrayList<ContextRules.Rule> parse(String raw) throws Exception {
        JSONArray a = new JSONArray(raw);
        if (a.length() > MAX_RULES) throw new IOException("Too many contexts");
        ArrayList<ContextRules.Rule> rules = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String id = o.getString("id"), name = o.getString("name");
            if (!id.matches("[a-z0-9-]{1,40}") || !ids.add(id)) throw new IOException("Invalid context id");
            if (name.trim().isEmpty() || name.length() > 24) throw new IOException("Invalid context name");
            ContextRules.Rule r = new ContextRules.Rule(id, name);
            r.enabled = o.optBoolean("enabled", true); r.fallback = o.optBoolean("fallback", false);
            r.days = range(o, "days", 0, 0, 127); r.start = range(o, "start", 0, 0, 1439); r.end = range(o, "end", 0, 0, 1439);
            r.headphones = range(o, "headphones", 0, 0, 2); r.charging = range(o, "charging", 0, 0, 2);
            r.dnd = range(o, "dnd", 0, 0, 2); r.playing = range(o, "playing", 0, 0, 2);
            r.header = o.optInt("header", -1);
            if (!Arrays.asList(-1, 0, 10, 20, 30, 40).contains(r.header)) throw new IOException("Invalid context header");
            r.category = o.optString("category", "");
            if (r.category.length() > 24) throw new IOException("Invalid context category");
            JSONArray w = o.optJSONArray("widgets");
            if (w != null) {
                if (w.length() > MAX_WIDGETS) throw new IOException("Too many widgets in context");
                for (int j = 0; j < w.length(); j++) {
                    String item = w.getString(j);
                    if (!Arrays.asList(ContextRules.WIDGETS).contains(item) && !item.matches("widget:\\d{1,9}")) throw new IOException("Invalid context widget");
                    r.widgets.add(item);
                }
            }
            rules.add(r);
        }
        return rules;
    }

    private static int range(JSONObject o, String key, int fallback, int min, int max) throws IOException {
        int v = o.optInt(key, fallback);
        if (v < min || v > max) throw new IOException("Invalid context " + key);
        return v;
    }

    static String newId(List<ContextRules.Rule> rules) {
        HashSet<String> used = new HashSet<>();
        for (ContextRules.Rule r : rules) used.add(r.id);
        for (int i = 1; ; i++) if (!used.contains("c" + i)) return "c" + i;
    }

    // Signals. All of these are permission-free.

    static ContextRules.Signals read(Context c, boolean playing) {
        Calendar now = Calendar.getInstance();
        int day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Calendar.MONDAY (2) -> 0
        ContextRules.Signals s = new ContextRules.Signals(day, now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
        s.headphones = headphones(c); s.charging = charging(c); s.dnd = dnd(c); s.playing = playing;
        return s;
    }

    static boolean headphones(Context c) {
        AudioManager audio = c.getSystemService(AudioManager.class);
        if (audio == null) return false;
        for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || t == AudioDeviceInfo.TYPE_USB_HEADSET || Build.VERSION.SDK_INT >= 31 && (t == AudioDeviceInfo.TYPE_BLE_HEADSET)) return true;
        }
        return false;
    }

    static boolean charging(Context c) {
        Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    static boolean dnd(Context c) {
        NotificationManager n = c.getSystemService(NotificationManager.class);
        return n != null && n.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
    }
}
