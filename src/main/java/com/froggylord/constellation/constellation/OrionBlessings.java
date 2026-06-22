package com.froggylord.constellation.constellation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OrionBlessings {

    private OrionBlessings() {}

    private static final Pattern BLESS = Pattern.compile("Blessing of (Power|Time|Wisdom|Life|Stone|Healing)\\b.*?\\b([IVXLC]+)\\b");
    private static final Map<String, Integer> levels = new LinkedHashMap<>();

    public static void onChat(String s) {
        if (!s.contains("Blessing of")) return;
        Matcher m = BLESS.matcher(s);
        if (!m.find()) return;
        int lvl = roman(m.group(2));
        levels.merge(m.group(1), lvl, Math::max);
    }

    public static void reset() { levels.clear(); }

    public static String display() {
        if (levels.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("§5✦ ");
        boolean first = true;
        for (var e : levels.entrySet()) {
            if (!first) sb.append(" §8| ");
            first = false;
            sb.append("§d").append(e.getKey().charAt(0)).append(" §f").append(e.getValue());
        }
        return sb.toString();
    }

    private static int roman(String r) {
        int total = 0, prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            int v = switch (r.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10;
                case 'L' -> 50; case 'C' -> 100; default -> 0;
            };
            if (v < prev) total -= v; else { total += v; prev = v; }
        }
        return total;
    }
}
