package com.froggylord.constellation.constellation;

import com.froggylord.constellation.config.CassiopeiaConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// ported from Devonian (GPL-3.0): features/misc/ActionbarParser.kt
public final class ActionBarCleaner {
    private static final Pattern SPLIT = Pattern.compile("\\s{2,}");
    private static final Pattern HEALTH = Pattern.compile("[\\d,.]+[kKmMbB]?/[\\d,.]+[kKmMbB]?[❤](?:\\+[\\d,.]+[▁-▆])?");
    private static final Pattern DEFENSE = Pattern.compile("[\\d,.]+[kKmMbB]?[❈](?: Defense)?");
    private static final Pattern MANA = Pattern.compile("[\\d,.]+[kKmMbB]?/[\\d,.]+[kKmMbB]?[✎](?: Mana)?(?: [\\d,.]+[kKmMbB]?[ʬ])?");
    private static final Pattern MANA_USE = Pattern.compile("-[\\d,.]+ Mana \\(.*\\)");
    private static final Pattern TRUE_DEFENSE = Pattern.compile("[\\d,.]+[❂❈](?: True Defense)");
    private static final Pattern SKILL = Pattern.compile("\\+[\\d,.]+[kKmMbB]? [A-Za-z ]+ \\(.*\\)");
    private static final Pattern SECRETS = Pattern.compile("\\d+/\\d+ Secrets");
    private static final Pattern DRILL = Pattern.compile("[\\d,.]+[kKmMbB]?/[\\d,.]+[kKmMbB]? Drill Fuel");
    private static final Pattern ARMOR = Pattern.compile("[\\d,.]+[ᝐ⁑⚶Ѫ҉]");
    private static final Pattern RIFT = Pattern.compile("(?:(?:[\\d,]+m)?[\\d,]+s)[ф] Left");
    private static final Pattern GECKO = Pattern.compile("\\[.*x\\d+.*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESSENCE = Pattern.compile("\\+?\\d+ [A-Za-z]+ Essence");
    private static final Pattern BITS = Pattern.compile("\\+?[\\d,]+ Bits from Cookie Buff!", Pattern.CASE_INSENSITIVE);

    private ActionBarCleaner() {}

    public static Component filter(Component message, CassiopeiaConfig cfg) {
        String filtered = filter(message.getString(), cfg);
        if (filtered == null || filtered.isEmpty()) return Component.empty();
        if (filtered.equals(message.getString().trim())) return message;
        return Component.literal(filtered);
    }

    public static String filter(String message, CassiopeiaConfig cfg) {
        if (message == null || message.isBlank()) return message;
        List<String> kept = new ArrayList<>();
        for (String raw : SPLIT.split(message.trim())) {
            String segment = raw.trim();
            if (segment.isEmpty() || hidden(type(segment), cfg)) continue;
            kept.add(segment);
        }
        return String.join("     ", kept);
    }

    private static boolean hidden(Type type, CassiopeiaConfig cfg) {
        return switch (type) {
            case HEALTH -> cfg.actionBarHideHealth;
            case DEFENSE -> cfg.actionBarHideDefense;
            case MANA -> cfg.actionBarHideMana;
            case MANA_USE -> cfg.actionBarHideManaUse;
            case TRUE_DEFENSE -> cfg.actionBarHideTrueDefense;
            case SKILL -> cfg.actionBarHideSkillXp;
            case SECRETS -> cfg.actionBarDungeonSegments && cfg.actionBarHideSecrets;
            case TERMINAL_LASER -> cfg.actionBarDungeonSegments && cfg.actionBarHideTerminalLaser;
            case ESSENCE -> cfg.actionBarDungeonSegments && cfg.actionBarHideEssence;
            case RAGNAROCK -> cfg.actionBarDungeonSegments && cfg.actionBarHideRagnarock;
            case AURORA_RUNE -> cfg.actionBarDungeonSegments && cfg.actionBarHideAuroraRune;
            case SOUL_ESOWARD -> cfg.actionBarDungeonSegments && cfg.actionBarHideSoulEsoward;
            case DRILL_FUEL -> cfg.actionBarWorldSegments && cfg.actionBarHideDrillFuel;
            case ARMOR_STACKS -> cfg.actionBarWorldSegments && cfg.actionBarHideArmorStacks;
            case RIFT_TIME -> cfg.actionBarWorldSegments && cfg.actionBarHideRiftTime;
            case GECKO_COMBO -> cfg.actionBarWorldSegments && cfg.actionBarHideGeckoCombo;
            case BITS -> cfg.actionBarWorldSegments && cfg.actionBarHideBits;
            case UNKNOWN -> false;
        };
    }

    private static Type type(String segment) {
        String plain = segment.replaceAll("§.", "").trim();
        if (MANA_USE.matcher(plain).matches()) return Type.MANA_USE;
        if (TRUE_DEFENSE.matcher(plain).matches()) return Type.TRUE_DEFENSE;
        if (HEALTH.matcher(plain).matches()) return Type.HEALTH;
        if (DEFENSE.matcher(plain).matches()) return Type.DEFENSE;
        if (MANA.matcher(plain).matches()) return Type.MANA;
        if (SECRETS.matcher(plain).matches()) return Type.SECRETS;
        if (SKILL.matcher(plain).matches()) return Type.SKILL;
        if (plain.equals("T1") || plain.equals("T2") || plain.equals("T3!")) return Type.TERMINAL_LASER;
        if (DRILL.matcher(plain).matches()) return Type.DRILL_FUEL;
        if (ARMOR.matcher(plain).matches()) return Type.ARMOR_STACKS;
        if (RIFT.matcher(plain).matches()) return Type.RIFT_TIME;
        if (GECKO.matcher(plain).matches()) return Type.GECKO_COMBO;
        if (ESSENCE.matcher(plain).matches()) return Type.ESSENCE;
        if (matchesAny(plain, s -> s.startsWith("CASTING IN ") || s.equals("CASTING") || s.equals("CANCELLED"))) return Type.RAGNAROCK;
        if (plain.equals("Defender") || plain.equals("Virtuoso") || plain.equals("Mediator")) return Type.AURORA_RUNE;
        if (plain.equals("INVULNERABLE") || plain.equals("IMMUNITY")) return Type.SOUL_ESOWARD;
        if (BITS.matcher(plain).matches()) return Type.BITS;
        return Type.UNKNOWN;
    }

    private static boolean matchesAny(String value, Predicate<String> predicate) {
        return predicate.test(value.toUpperCase(Locale.ROOT));
    }

    private enum Type {
        HEALTH, DEFENSE, MANA, MANA_USE, TRUE_DEFENSE, SKILL, SECRETS, TERMINAL_LASER,
        DRILL_FUEL, ARMOR_STACKS, RIFT_TIME, GECKO_COMBO, ESSENCE, RAGNAROCK,
        AURORA_RUNE, SOUL_ESOWARD, BITS, UNKNOWN
    }
}
