package com.froggylord.constellation.constellation;

import java.util.Locale;

/**
 * Colour + initial lookup for the five Catacombs classes, shared by the Spirit Leap overlay
 * ({@link OrionSpiritLeap}) and the in-world teammate glow ({@link CombatEsp}).
 *
 * Colour palette ported from NoFrills (GPL-3):
 *   - features/dungeons/ClassNametags.java
 *   - features/dungeons/LeapOverlay.java
 * DungeonState / tab-list class names use "Berserk" (not "Berserker").
 */
final class DungeonClassInfo {

    private DungeonClassInfo() {}

    /** Opaque ARGB (0xFF alpha) so the colour reads identically on a see-through glow and a slot badge. */
    static int colour(String dungeonClass) {
        if (dungeonClass == null) return 0;
        return switch (dungeonClass) {
            case "Healer"  -> 0xFFECB50C;
            case "Mage"    -> 0xFF1793C4;
            case "Berserk" -> 0xFFE7413C;
            case "Archer"  -> 0xFF4A14B7;
            case "Tank"    -> 0xFF768F46;
            default -> 0;
        };
    }

    static char initial(String dungeonClass) {
        return (dungeonClass == null || dungeonClass.isEmpty()) ? '?' : dungeonClass.charAt(0);
    }

    /** Scan free text (item name + lore) for a class keyword; used as a fallback when the tab list is stale. */
    static String fromText(String text) {
        if (text == null) return "";
        if (text.contains("Healer")) return "Healer";
        if (text.contains("Mage")) return "Mage";
        if (text.contains("Berserk")) return "Berserk";
        if (text.contains("Archer")) return "Archer";
        if (text.contains("Tank")) return "Tank";
        return "";
    }

    static String keyId(String dungeonClass) {
        return "leap." + dungeonClass.toLowerCase(Locale.ROOT);
    }
}
