package com.studio72f.eigen;

import java.util.*;

/** Context rules: ordered, first match wins, AND-only conditions. Pure Java so it is testable off-device. */
public final class ContextRules {
    static final int ANY = 0, YES = 1, NO = 2;
    static final String[] DAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
    static final String[] WIDGETS = {"clock", "alarm", "agenda", "media", "smartspace"};
    static final int WEEKDAYS = 0b0011111, WEEKEND = 0b1100000;
    // Display words. English defaults keep this class testable off-device; the app replaces them from resources.
    static String everyDay = "every day", weekdays = "weekdays", weekends = "weekends", fallbackText = "when nothing else matches",
        always = "always", headphonesWord = "headphones", chargingWord = "charging", dndWord = "do not disturb", playingWord = "media playing", notFormat = "no %s";
    static String[] dayNames = DAYS.clone();
    static void localize(String[] words, String[] days) {
        everyDay = words[0]; weekdays = words[1]; weekends = words[2]; fallbackText = words[3]; always = words[4];
        headphonesWord = words[5]; chargingWord = words[6]; dndWord = words[7]; playingWord = words[8]; notFormat = words[9];
        if (days.length == 7) dayNames = days.clone();
    }

    public static final class Rule {
        String id, name, category = "";
        boolean enabled = true, fallback;
        int days, start, end, headphones, charging, dnd, playing, header = -1;
        final ArrayList<String> widgets = new ArrayList<>();
        Rule(String id, String name) { this.id = id; this.name = name; }
        boolean allDay() { return start == end; }
    }

    static final class Signals {
        int day, minute; // day: 0 = Monday
        boolean headphones, charging, dnd, playing;
        Signals(int day, int minute) { this.day = day; this.minute = minute; }
    }

    static boolean matches(Rule r, Signals s) {
        if (r.fallback) return true;
        if (!r.enabled) return false;
        if (!inWindow(r, s)) return false;
        return flag(r.headphones, s.headphones) && flag(r.charging, s.charging) && flag(r.dnd, s.dnd) && flag(r.playing, s.playing);
    }

    private static boolean day(Rule r, int day) { return r.days == 0 || (r.days & (1 << day)) != 0; }

    private static boolean inWindow(Rule r, Signals s) {
        if (r.allDay()) return day(r, s.day);
        if (r.start < r.end) return day(r, s.day) && s.minute >= r.start && s.minute < r.end;
        // Wraps midnight: Fri 22:00–02:00 still belongs to Friday after midnight.
        return s.minute >= r.start && day(r, s.day) || s.minute < r.end && day(r, (s.day + 6) % 7);
    }

    private static boolean flag(int want, boolean actual) { return want == ANY || (want == YES) == actual; }

    /** Returns the first matching rule. A pinned rule wins while its override is valid. */
    static Rule resolve(List<Rule> rules, Signals s, String pinned) {
        if (pinned != null) for (Rule r : rules) if (r.id.equals(pinned)) return r;
        for (Rule r : rules) if (matches(r, s)) return r;
        return rules.isEmpty() ? null : rules.get(rules.size() - 1);
    }

    /** Keeps exactly one fallback rule, last in the list. */
    static void normalize(List<Rule> rules) {
        Rule fallback = null;
        for (Iterator<Rule> it = rules.iterator(); it.hasNext(); ) { Rule r = it.next(); if (r.fallback) { if (fallback == null) fallback = r; it.remove(); } }
        if (fallback == null) fallback = everyday(true, true);
        fallback.enabled = true; rules.add(fallback);
    }

    static Rule everyday(boolean clock, boolean media) {
        Rule r = new Rule("everyday", "everyday"); r.fallback = true;
        if (clock) r.widgets.add("clock");
        if (media) r.widgets.add("media");
        return r;
    }

    static String time(int minute) { return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60); }

    static String days(int mask) {
        if (mask == 0 || mask == 0b1111111) return everyDay;
        if (mask == WEEKDAYS) return weekdays;
        if (mask == WEEKEND) return weekends;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) { if (b.length() > 0) b.append(' '); b.append(dayNames[i]); }
        return b.toString();
    }

    /** Human-readable "why": "weekdays 07:00–09:00 · headphones". */
    static String describe(Rule r) {
        if (r.fallback) return fallbackText;
        ArrayList<String> parts = new ArrayList<>();
        String when = r.days == 0 ? "" : days(r.days);
        if (!r.allDay()) when = (when.isEmpty() ? "" : when + " ") + time(r.start) + "–" + time(r.end);
        if (!when.isEmpty()) parts.add(when);
        condition(parts, r.headphones, headphonesWord);
        condition(parts, r.charging, chargingWord);
        condition(parts, r.dnd, dndWord);
        condition(parts, r.playing, playingWord);
        return parts.isEmpty() ? always : String.join(" · ", parts);
    }

    private static void condition(List<String> parts, int value, String label) {
        if (value == YES) parts.add(label);
        if (value == NO) parts.add(String.format(notFormat, label));
    }

    /** Templates offered by "add context". */
    static Rule template(String kind, String id) {
        Rule r = new Rule(id, kind);
        switch (kind) {
            case "morning": r.days = WEEKDAYS; r.start = 6 * 60; r.end = 10 * 60; r.widgets.addAll(Arrays.asList("clock", "alarm", "agenda")); break;
            case "work": r.days = WEEKDAYS; r.start = 9 * 60; r.end = 17 * 60; r.widgets.addAll(Arrays.asList("clock", "agenda")); r.category = "tools"; break;
            case "headphones": r.headphones = YES; r.widgets.addAll(Arrays.asList("clock", "media")); r.category = "music"; break;
            case "night": r.start = 22 * 60; r.end = 6 * 60; r.widgets.addAll(Arrays.asList("clock", "alarm")); r.header = 20; break;
            case "charging": r.charging = YES; r.widgets.addAll(Arrays.asList("clock", "alarm", "media")); r.header = 40; break;
            default: r.widgets.add("clock");
        }
        return r;
    }
}
