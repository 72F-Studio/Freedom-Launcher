package com.studio72f.eigen;

import java.text.Normalizer;
import java.util.Locale;

/** Original local heuristics. Unknown apps remain available under tools and all apps. */
public final class CategoryRules {
    private CategoryRules() { }
    public static String classify(String packageName, int androidCategory) {
        String p = packageName.toLowerCase(Locale.ROOT);
        if (contains(p, "camera", "gallery", "photos", "snapseed", "lightroom")) return "photos";
        if (contains(p, "dialer", "messaging", "contacts", "whatsapp", "telegram", "signal", "discord", "instagram", "facebook", "twitter", "slack", "mail")) return "social";
        if (contains(p, "spotify", "music", "podcast", "audiobook")) return "music";
        if (contains(p, "youtube", "netflix", "vlc", "video", "twitch")) return "video";
        if (contains(p, "maps", "navigation", "uber", "lyft", "transit")) return "travel";
        return switch (androidCategory) {
            case 0 -> "games";
            case 1 -> "music";
            case 2 -> "video";
            case 3 -> "photos";
            case 4 -> "social";
            case 6 -> "travel";
            default -> "tools";
        };
    }
    private static boolean contains(String value, String... parts) {
        for (String part : parts) if (value.contains(part)) return true;
        return false;
    }
    public static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
    public static String initial(String name) {
        String s = normalized(name).trim();
        if (s.isEmpty()) return "#";
        char c = s.charAt(0);
        return c >= 'a' && c <= 'z' ? String.valueOf(Character.toUpperCase(c)) : "#";
    }
    public static String resolve(String auto, String manual, java.util.Map<String,String> merges) {
        String current = manual == null ? auto : manual;
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        while (merges.containsKey(current) && seen.add(current)) current = merges.get(current);
        return current;
    }
}
