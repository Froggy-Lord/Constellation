package com.froggylord.constellation.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Matcher;

/**
 * validates every registered pattern against its real samples.
 * build fails if hypixel changes a format — this is the canary.
 * add new samples whenever you scrape a verified match.
 */
class PatternsTest {

    @Test
    void allPatternsMatchTheirSamples() {
        var failures = new StringBuilder();
        for (var e : Patterns.all()) {
            String name = e.name();
            var regex = e.pattern();
            for (String sample : e.samples()) {
                String stripped = sample.replaceAll("§[0-9a-fk-or]", ""); // no MC dep
                if (regex == null) continue; // pattern-less — tested by hand
                Matcher m = regex.matcher(stripped);
                if (!m.find()) {
                    failures.append(String.format("  %-35s regex did NOT match: %s%n",
                        name, stripped));
                }
            }
        }
        if (failures.length() > 0) {
            fail("Pattern mismatches — Hypixel likely changed a format:%n" + failures);
        }
    }

    @Test
    void patternCountSanity() {
        assertTrue(Patterns.all().size() >= 48, "should have at least 48 registered patterns");
    }

    @Test
    void actionBarHealthMatches() {
        var m = Patterns.get("actionbar.health").matcher("5.2k/4,805❤");
        assertTrue(m.find());
        assertEquals("5.2k", m.group(1));
        assertEquals("4,805", m.group(2));
    }

    @Test
    void sidebarPurseMatches() {
        var m = Patterns.get("sidebar.purse").matcher("Purse: 19,908");
        assertTrue(m.find());
        assertEquals("19,908", m.group(1));
    }

    @Test
    void dungeonClearedMatches() {
        var m = Patterns.get("dungeon.cleared").matcher("Cleared: 87% (12)");
        assertTrue(m.find());
        assertEquals("87", m.group("c"));
    }

    @Test
    void bazaarChatMatches() {
        var m = Patterns.get("chat.bazaar").matcher("[Bazaar] Bought 64x Enchanted Sugar for 123,456 coins!");
        assertTrue(m.find());
        assertEquals("Bought", m.group(1));
        assertEquals("123,456", m.group(2));
    }

    @Test
    void reforgeChatMatches() {
        var m = Patterns.get("chat.reforge").matcher("You reforged your Heroic Dreadlord Sword into a Fair Dreadlord Sword!");
        assertTrue(m.find());
    }
}
