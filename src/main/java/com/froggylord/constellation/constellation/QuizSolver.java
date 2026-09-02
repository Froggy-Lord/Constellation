package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3): skyblock/dungeon/puzzle/Trivia.java
// answer table taken from devonian (GPL-3): features/dungeons/solvers/TriviaSolver.kt
// (devonian hardcodes the fairy soul counts; skyblocker pulls them from its own fairy soul
//  data which this mod doesn't have, so devonian's table is the one that ports cleanly)
//
// oruo asks a question in chat then lists three answers as a/b/c lines. the three answer
// blocks sit on fixed room-relative spots and you press the button next to the right one.
// old version hooked a container screen titled "trivia" — never opens, never fired, and it
// had a fallback that boxed slot 10 when it didn't know the answer. both are gone.
public final class QuizSolver {

    private QuizSolver() {}

    private static OrionConfig cfg;

    // circled a/b/c. kept as escapes so the glyph audit stays clean
    private static final String A = "\u24D0";
    private static final String B = "\u24D1";
    private static final String C = "\u24D2";

    private static final Pattern QUESTION = Pattern.compile("^ +([A-Za-z,' ]*\\?)$");
    private static final Pattern CHOICE = Pattern.compile("^ +([" + A + B + C + "]) (.+)$");
    private static final Pattern DONE = Pattern.compile(
        "^\\[STATUE] Oruo the Omniscient: (?:\\w+ answered Question #\\d+ correctly!"
        + "|I bestow upon you all the power of a hundred years!|Yikes)$");

    // answer blocks, room-relative. the buttons are on the sides of these
    private static final Map<String, BlockPos> CHOICE_BLOCK = Map.of(
        A, new BlockPos(20, 70, 6),
        B, new BlockPos(15, 70, 9),
        C, new BlockPos(10, 70, 6));

    private static final int GREEN_FILL = 0x8033FF33;
    private static final int GREEN_LINE = 0xFF33FF33;

    // "which of these is NOT ..." questions have several valid texts, hence the lists
    private static final Map<String, List<String>> ANSWERS = buildAnswers();

    private static List<String> solutions = List.of();
    private static String correct = "";
    private static String roomKey = "";

    public static void init(OrionConfig config) {
        cfg = config;
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || cfg == null || !cfg.triviaSolver) return;
            if (!ConstellationClient.loc().inDungeons()) return;
            if (!inRoom()) return;

            String s = ChatFormatting.stripFormatting(msg.getString());

            if (DONE.matcher(s).matches()) { clear(); return; }

            Matcher q = QUESTION.matcher(s);
            if (q.matches()) {
                // new question — drop the old highlight before looking the new one up
                clear();
                // note: "what skyblock year is it?" is deliberately unanswered, no year clock here
                solutions = ANSWERS.getOrDefault(q.group(1).trim(), List.of());
                return;
            }

            Matcher c = CHOICE.matcher(s);
            if (c.matches() && !solutions.isEmpty() && solutions.contains(c.group(2).trim())) {
                correct = c.group(1);
            }
        });
    }

    public static void reset() {
        clear();
        roomKey = "";
    }

    private static void clear() {
        solutions = List.of();
        correct = "";
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (cfg == null || !cfg.triviaSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        if (!inRoom()) { clear(); return; }
        if (correct.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos rel = CHOICE_BLOCK.get(correct);
        if (rel == null) return;

        for (AABB box : buttons(mc.level, world(rel))) {
            ctx.highlight(box, GREEN_FILL, false);
            ctx.outline(box, GREEN_LINE, false);
        }
    }

    // the pressable buttons are the solid blocks stuck to the sides of the answer block
    private static List<AABB> buttons(ClientLevel level, BlockPos block) {
        List<AABB> out = new ArrayList<>();
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos p = block.relative(d);
            if (level.getBlockState(p).isAir()) continue;
            out.add(new AABB(p));
        }
        return out;
    }

    private static boolean inRoom() {
        if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("trivia-room")) return false;
        String key = RoomMatch.currentRoom() + ':' + RoomMatch.anchorX() + ':' + RoomMatch.anchorZ() + ':' + RoomMatch.currentDir();
        if (!key.equals(roomKey)) {
            roomKey = key;
            clear();
        }
        return true;
    }

    private static BlockPos world(BlockPos rel) {
        long[] w = RoomTransform.relativeToActual(
            RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(),
            rel.getX(), rel.getY(), rel.getZ());
        return new BlockPos((int) w[0], (int) w[1], (int) w[2]);
    }

    private static Map<String, List<String>> buildAnswers() {
        Map<String, List<String>> m = new java.util.HashMap<>();
        m.put("What is the status of The Watcher?", List.of("Stalker"));
        m.put("What is the status of Bonzo?", List.of("New Necromancer"));
        m.put("What is the status of Scarf?", List.of("Apprentice Necromancer"));
        m.put("What is the status of The Professor?", List.of("Professor"));
        m.put("What is the status of Thorn?", List.of("Shaman Necromancer"));
        m.put("What is the status of Livid?", List.of("Master Necromancer"));
        m.put("What is the status of Sadan?", List.of("Necromancer Lord"));
        m.put("What is the status of Maxor?", List.of("The Wither Lords"));
        m.put("What is the status of Storm?", List.of("The Wither Lords"));
        m.put("What is the status of Goldor?", List.of("The Wither Lords"));
        m.put("What is the status of Necron?", List.of("The Wither Lords"));
        m.put("What is the status of Maxor, Storm, Goldor, and Necron?", List.of("The Wither Lords"));

        m.put("How many total Fairy Souls are there?", List.of("273 Fairy Souls"));
        m.put("How many Fairy Souls are there in Spider's Den?", List.of("19 Fairy Souls"));
        m.put("How many Fairy Souls are there in Spiders Den?", List.of("19 Fairy Souls"));
        m.put("How many Fairy Souls are there in The End?", List.of("12 Fairy Souls"));
        m.put("How many Fairy Souls are there in The Farming Islands?", List.of("20 Fairy Souls"));
        m.put("How many Fairy Souls are there in Crimson Isle?", List.of("29 Fairy Souls"));
        m.put("How many Fairy Souls are there in The Park?", List.of("12 Fairy Souls"));
        m.put("How many Fairy Souls are there in Jerry's Workshop?", List.of("5 Fairy Souls"));
        m.put("How many Fairy Souls are there in Hub?", List.of("80 Fairy Souls"));
        m.put("How many Fairy Souls are there in The Hub?", List.of("80 Fairy Souls"));
        m.put("How many Fairy Souls are there in Deep Caverns?", List.of("21 Fairy Souls"));
        m.put("How many Fairy Souls are there in Gold Mine?", List.of("12 Fairy Souls"));
        m.put("How many Fairy Souls are there in Dungeon Hub?", List.of("7 Fairy Souls"));

        m.put("Which brother is on the Spider's Den?", List.of("Rick"));
        m.put("Which brother is on the Spiders Den?", List.of("Rick"));
        m.put("What is the name of Rick's brother?", List.of("Pat"));
        m.put("What is the name of the person that upgrades pets?", List.of("Kat"));
        m.put("What is the name of the lady of the Nether?", List.of("Elle"));
        m.put("Which villager in the Village gives you a Rogue Sword?", List.of("Jamie"));
        m.put("How many unique minions are there?", List.of("61 Minions"));
        // hypixel wraps this one over two lines so only the tail arrives as the question
        m.put("glass?", List.of("Wool Weaver"));
        m.put("What is the name of the vendor in the Hub who sells stained glass?", List.of("Wool Weaver"));

        // the "not" questions — every listed text is a valid answer, hypixel picks 3 at random
        List<String> notSpider = List.of("Zombie Spider", "Cave Spider", "Wither Skeleton",
            "Dashing Spooder", "Broodfather", "Night Spider");
        m.put("Which of these enemies does not spawn in the Spider's Den?", notSpider);
        m.put("Which of these enemies does not spawn in the Spiders Den?", notSpider);
        m.put("Which of these monsters only spawns at night?", List.of("Zombie Villager", "Ghast"));
        m.put("Which of these is not a dragon in The End?", List.of("Zoomer Dragon", "Weak Dragon",
            "Stonk Dragon", "Holy Dragon", "Boomer Dragon", "Booger Dragon", "Older Dragon",
            "Elder Dragon", "Stable Dragon", "Professor Dragon"));
        return m;
    }
}
