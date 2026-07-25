package com.froggylord.constellation.core;

import java.util.*;
import java.util.regex.Pattern;

/**
 * central pattern registry — one place for every regex and string matcher.
 * each pattern has a name, a default regex, and at least one real sample that
 * MUST match (seeded from live scrapes). tests can assert every registered
 * pattern passes its samples — build fails if hypixel changes a format.
 *
 * modelled on skyhanni's RepoPattern system but ours is dead simple:
 * declare the pattern once, get it with Patterns.get("name").
 */
public final class Patterns {

    private Patterns() {}

    public record Entry(String name, Pattern pattern, List<String> samples) {}

    private static final List<Entry> entries = new ArrayList<>();
    private static final Map<String, Pattern> byName = new LinkedHashMap<>();

    // ---- register patterns here (name, regex, one-or-more real samples) ----

    static {
        // --- chat: action bar ---
        register("actionbar.health",  "([\\d.,]+[kKmMbB]?)/([\\d.,]+[kKmMbB]?)❤",
            List.of("5.2k/4,805❤"));
        register("actionbar.mana",    "([\\d.,]+[kKmMbB]?)/([\\d.,]+[kKmMbB]?)✎",
            List.of("781/781✎", "1.2k/2.5k✎"));
        register("actionbar.defense", "([\\d.,]+[kKmMbB]?)❈",
            List.of("915❈", "1.2k❈"));

        // --- sidebar: economy ---
        register("sidebar.purse", "(?:Purse|Piggy):\\s*([\\d,]+)",
            List.of("Purse: 19,908", "Purse: 188,699", "Piggy: 50,000"));
        register("sidebar.bits",  "Bits:\\s*([\\d,]+)",
            List.of("Bits: 5,575"));

        // --- sidebar: rift ---
        register("sidebar.motes", "Motes:\\s*([\\d,]+)",
            List.of("Motes: 19,305", "Motes: 15,715", "Motes: 15,715 (+250)"));

        // --- sidebar: mining ---
        register("sidebar.cold", "Cold:?\\s*-?(\\d+)",
            List.of("Cold: 50", "Cold: -75"));
        register("sidebar.powder", "(Mithril|Gemstone|Glacite):?\\s*([\\d,]+)",
            List.of("Mithril: 2,684,037", "Gemstone: 1,546,102", "Glacite: 64,323"));

        // --- sidebar: dungeons ---
        register("dungeon.cleared", "Cleared: (?<c>\\d+)%.*",
            List.of("Cleared: 0% (0)", "Cleared: 87% (12)"));
        register("dungeon.time",    "Time Elapsed: (?:(?<m>\\d+)m )?(?<s>\\d+)s",
            List.of("Time Elapsed: 03s", "Time Elapsed: 4m 12s"));

        // --- chat: economy ---
        register("chat.bazaar", "\\[Bazaar] (Bought|Sold|Order Flipped!)[^f]*for ([\\d,.]+) coins",
            List.of("[Bazaar] Bought 64x Enchanted Sugar for 123,456 coins!",
                    "[Bazaar] Sold 32x Enchanted Lapis for 99,999 coins!"));
        register("chat.essence", "([A-Za-z]+) Essence(?: x(\\d+))?",
            List.of("Wither Essence", "Undead Essence x5", "Dragon Essence x12"));
        register("chat.outbid", null, // pattern-less — just s.contains("outbid") is the real check
            List.of("You have been outbid on your auction!",
                    "[Auction] someone outbid you by 500,000 coins"));

        // --- chat: slayers ---
        register("chat.slayer.start", null,
            List.of("SLAYER QUEST STARTED!",
                    "  §r§5§lSLAYER QUEST STARTED!"));
        register("chat.slayer.slain", null,
            List.of("NICE! SLAYER BOSS SLAIN!",
                    "  §r§6§lNICE! SLAYER BOSS SLAIN!"));
        register("chat.bestiary", "BESTIARY MILESTONE (\\d+)",
            List.of("BESTIARY MILESTONE 25", "BESTIARY MILESTONE 50"));

        // --- chat: reforge ---
        register("chat.reforge", "You reforged your .+ into an? .+!|You applied an? .+ to your .+!",
            List.of("You reforged your Heroic Dreadlord Sword into a Fair Dreadlord Sword!",
                    "You applied a Recombobulator 3000 to your Hyperion!"));

        // --- chat: drops ---
        register("chat.rare_drop", null,
            List.of("§5§lVERY RARE DROP! §r§7(§r§9Bane of Arthropods VI§r§7) §r§b(+137% ✯ Magic Find)"));
        register("chat.pet_drop", null,
            List.of("§d§lPET DROP! §r§7(§r§5Ender Dragon§r§7) §r§b(+200% ✯ Magic Find)"));

        // --- chat: kuudra phases ---
        register("chat.kuudra.down", null,
            List.of("§.\\s*(?:§.)*KUUDRA DOWN!", "KUUDRA DOWN!"));
        register("chat.kuudra.stun", null,
            List.of("Kuudra has been stunned!", "and stun him"));

        // --- chat: fishing ---
        register("chat.sea_creature", null,
            List.of("A Sea Walker has spawned!",
                    "A Sea Archer has spawned!",
                    "You caught a Sea Walker!"));

        // --- chat: garden/visitor ---
        register("chat.visitor", null,
            List.of("A new visitor has arrived at your garden!",
                    "New Visitor: Jacob"));

        // --- sidebar: garden ---
        register("sidebar.garden.copper", "Copper:\\s*([\\d,]+)",
            List.of("Copper: 1,131"));
        register("sidebar.garden.sowdust", "Sowdust:\\s*([\\d,]+)",
            List.of("Sowdust: 101,687,009"));

        // --- tab: rift timer ---
        register("tab.rift.time", "Rift Time Left:\\s*(.+)",
            List.of("Rift Time Left: 43m", "Rift Time Left: 20m"));
        register("tab.rift.souls", "Enigma Souls:\\s*(\\d+)/(\\d+)",
            List.of("Enigma Souls: 30/52"));

        // --- chat: inquisitor ---
        register("chat.diana.inquis", "at Coords (-?\\d+) (-?\\d+) (-?\\d+)",
            List.of("A MINOS INQUISITOR has spawned near [Stillgore Chateau] at Coords 125 78 -340"));

        // --- tab: crystals ---
        register("tab.crystals", "(Jade|Amber|Sapphire|Amethyst|Ruby|Topaz):\\s*(.+)",
            List.of("Jade: ✖ Not Found", "Amber: ✖ Not Found", "Jade: ✔ Found"));

        // --- sidebar: cold (glacite) ---
        register("sidebar.mining.cold", "Cold:?\\s*-?(\\d+)",
            List.of("Cold: 25", "Cold: -99"));

        // --- tab: commissions ---
        register("tab.commissions", "(?<name>[A-Za-z ]+?):\\s*(?<val>\\d+(?:\\.\\d+)?%|DONE)",
            List.of("Onyx Gemstone Collector: 1.8%", "Mineshaft Explorer: DONE"));

        // --- tab: forges ---
        register("tab.forge", "(?<slot>\\d+)\\)\\s*(?:(?<item>.+):\\s*(?<time>\\d+h|\\d+m|\\d+s|Ready!)|EMPTY)",
                List.of("1) Refined Mithril: 4h", "2) EMPTY"));

        // --- tab: faction quests ---
        register("tab.faction", "\\u2716\\s*(.+)",
            List.of("✖ Ashfang", "✖ Kill Kuudra Basic Tier"));

        // --- tab: volcano status ---
        register("tab.volcano", "Volcano:\\s*(.+)",
            List.of("Volcano: INACTIVE", "Volcano: ACTIVE"));

        // --- chat: sea creature spawn ---
        register("chat.sea_creature_spawn", null,
            List.of("A Sea Walker has spawned!", "You caught a Sea Walker!",
                    "A legendary Sea Creature has spawned!"));

        // --- chat: vanquisher spawn ---
        register("chat.vanquisher", null,
            List.of("A Vanquisher is spawning nearby!"));

        // --- chat: golden fish ---
        register("chat.golden_fish", null,
            List.of("GOLDEN FISH!", "You caught a Golden Fish!"));

        // --- tab: glacite / cold ---
        register("tab.cold", "Cold:\\s*(\\d+)",
            List.of("Cold: 25", "Cold: 99"));

        // --- tab: drill fuel ---
        register("tab.drill_fuel", "(?:⛏\\s*)?(?:Drill\\s*)?Fuel:?\\s*([\\d,\\.]+[kKmM]?)\\s*/?\\s*([\\d,\\.]+[kKmM]?)?",
            List.of("⛏ Drill Fuel: 2.5k/3k", "Fuel: 1500/3000"));

        // --- dungeon: chat patterns ---
        register("chat.mimic", null,
            List.of("Mimic dead!", "Mimic Killed!", "$SKYTILS-DUNGEON-SCORE-MIMIC$"));
        register("chat.prince", null,
            List.of("Prince dead!", "Prince Killed!", "A Prince falls. +1 Bonus Score"));
        register("chat.watcher_pass", null,
            List.of("[BOSS] The Watcher: You have proven yourself. You may pass."));
        register("chat.m7.maxor", null,
            List.of("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"));
        register("chat.m7.storm", null,
            List.of("[BOSS] Storm: Pathetic Maxor, just like expected."));
        register("chat.m7.goldor", null,
            List.of("[BOSS] Goldor: Who dares trespass into my domain?"));
        register("chat.m7.necron", null,
            List.of("[BOSS] Necron: You went further than any human before, congratulations."));

        // --- chat: blessing ---
        register("chat.blessing", "Blessing of (Power|Time|Wisdom|Life|Stone|Healing)\\b.*?\\b([IVXLC]+)\\b",
            List.of("Blessing of Power I", "Blessing of Time II"));

        // --- chat: spirit leap ---
        register("chat.spirit_leap", null,
            List.of("spirit leap to", "used a Spirit Leap on"));
    }

    // ---- api ----

    public static void register(String name, String regex, List<String> samples) {
        Pattern p = regex == null ? null : Pattern.compile(regex);
        entries.add(new Entry(name, p, List.copyOf(samples)));
        if (p != null) byName.put(name, p);
    }

    public static Pattern get(String name) {
        Pattern p = byName.get(name);
        if (p == null) throw new IllegalArgumentException("no pattern registered: " + name);
        return p;
    }

    /** for tests: every registered entry with its required samples */
    public static List<Entry> all() { return Collections.unmodifiableList(entries); }

    /** convenience: check if any registered pattern matches the given line */
    public static Optional<String> matchAny(String line) {
        for (var e : entries) {
            if (e.pattern() != null && e.pattern().matcher(line).find())
                return Optional.of(e.name());
        }
        return Optional.empty();
    }
}
