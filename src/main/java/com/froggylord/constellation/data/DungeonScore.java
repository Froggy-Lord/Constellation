package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonScore {

    private DungeonScore() {}

    
    private static final Pattern FLOOR = Pattern.compile("The Catacombs \\((?<f>[^)]+)\\)");
    private static final Pattern CLEARED = Pattern.compile("Cleared: (?<c>\\d+)%.*");
    private static final Pattern TIME = Pattern.compile("Time Elapsed: (?:(?<m>\\d+)m )?(?<s>\\d+)s");
    // ---- tab ----
    private static final Pattern SECRETS = Pattern.compile("Secrets Found: (?<p>\\d+\\.?\\d*)%");
    private static final Pattern COMPLETED = Pattern.compile(" *Completed Rooms: (?<r>\\d+)");
    private static final Pattern CRYPTS = Pattern.compile("Crypts: (?<c>\\d+)");
    private static final Pattern DEATHS_SIDEBAR = Pattern.compile("Deaths: (?<d>\\d+)");
    private static final Pattern PUZZLE_COUNT = Pattern.compile("Puzzles: \\((?<n>\\d+)\\)");
    private static final Pattern PUZZLE = Pattern.compile(".+?: \\[(?<state>.)](?: \\(\\w*\\))?");

    
    private static boolean active;
    private static int deaths;
    private static boolean mimicKilled;
    private static boolean princeKilled;
    private static boolean inBoss;
    private static long bloodAt; // set when watcher says pass
    private static long bossAt;
    private static long clearAt; // set once when the run completes (boss cleared)
    // 5.2s delay after watcher pass before blood room counts as "done" (matches skyblocker)
    private static boolean bloodDone() { return bloodAt > 0 && System.currentTimeMillis() - bloodAt > 5200; }
    private static boolean mayorPaul; 
    private static boolean sent270;
    private static boolean sent300;

    
    private static int score;
    private static String grade = "D";
    private static double secretPct;
    private static int crypts;
    private static int timeSecs;
    private static String floorName = "";
    private static boolean mimicFloor;
    // stashed for the score hud breakdown (derived from the same hypixel aggregates the score uses)
    private static int roomsCleared;
    private static int roomsTotal;
    private static int completionPct;

    public static void update() {
        DungeonState state = ConstellationClient.dungeon();
        if (!state.runStarted()) {
            active = false;
            return;
        }
        active = true;
        // capture the clear split once the run is flagged complete (boss killed / dungeon cleared)
        if (clearAt == 0 && state.runEnded()) clearAt = System.currentTimeMillis();
        // run time comes from DungeonState (elapsed since the run's start timestamp) — it already
        // owns the clock, so we don't re-parse the "Time Elapsed" sidebar line a second time here.
        long start = state.startTime();
        timeSecs = start > 0 ? (int) ((System.currentTimeMillis() - start) / 1000) : 0;

        floorName = state.floor();
        mimicFloor = floorName.matches("[FM][67]");
        FloorReq floor = FloorReq.from(floorName);
        boolean entrance = floor == FloorReq.E;

        // dungeon-wide aggregates are hypixel-authoritative and drive the canonical score: clear%
        // from the sidebar, secrets%/completed-rooms/crypts from the tab list — exactly the inputs
        // skyblocker reads. per-room detection (SecretWaypoints/MapSegments) is NOT a valid
        // substitute here: it only knows the current room, not the whole run.
        List<String> side = ConstellationClient.loc().getSidebarLines();
        double cleared = clearedFrac(side);

        List<String> tab = TabList.lines();
        secretPct = secretsPct(tab);
        int completed = completedRooms(tab);
        crypts = crypts(tab);
        deaths = state.deaths();
        int incompletePuzzles = incompletePuzzles(tab);

        int total = cleared > 0 ? (int) Math.round(completed / cleared) : 0;
        int extra = extraRooms(entrance);

        // expose rooms + completion for the hud without a second parse
        roomsCleared = completed;
        roomsTotal = total;
        completionPct = (int) Math.round(cleared * 100);

        int timeScore = timeScore(floor);
        int exploreScore = exploreScore(floor, total, completed, extra);
        int skillScore = skillScore(total, completed, extra, incompletePuzzles);
        int bonusScore = bonusScore();

        int raw = timeScore + exploreScore + skillScore + bonusScore;
        if (entrance) raw = Math.round(timeScore * 0.7f) + Math.round(exploreScore * 0.7f)
                          + Math.round(skillScore * 0.7f) + Math.round(bonusScore * 0.7f);
        score = raw;
        grade = grade(score);
        checkMilestones();
    }

    
    private static void checkMilestones() {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || (!cfg.scorePings && !cfg.partyMessages)) return;
        ConstellationClient.verifyLog("orion-score", score > 0, "score=" + score);
        // ported from devonian (GPL-3.0): features/dungeons/clear/ScoreTime.kt
        // the local system message is display-only and never goes to the server
        if (!sent270 && score >= 270 && score < 300) {
            if (cfg.scorePings)
                ping(cfg, "§6§lS §r§ereached at §a" + formatTime(timeSecs) + " §7(" + floorName + ")", 1.2f);
            com.froggylord.constellation.constellation.PartyMessages.send("score-270",
                java.util.Map.of("score", 270, "time", formatTime(timeSecs), "floor", floorName));
            sent270 = true;
        }
        if (!sent300 && score >= 300) {
            if (cfg.scorePings)
                ping(cfg, "§b§lS+ §r§ereached at §a" + formatTime(timeSecs) + " §7(" + floorName + ")", 1.8f);
            com.froggylord.constellation.constellation.PartyMessages.send("score-300",
                java.util.Map.of("score", 300, "time", formatTime(timeSecs), "floor", floorName));
            sent300 = true;
        }
    }

    private static void ping(com.froggylord.constellation.config.OrionConfig cfg, String text, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
        if (cfg.scorePingTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal(text));
        }
        if (cfg.scorePingSound) {
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, pitch);
        }
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    

    private static int skillScore(int total, int completed, int extra, int incompletePuzzles) {
        int roomScore = total != 0 ? clamp((int) (80.0 * (completed + extra) / total), 0, 80) : 0;
        return 20 + clamp(roomScore - incompletePuzzles * 10 - deathPenalty(), 0, 80);
    }

    private static int exploreScore(FloorReq floor, int total, int completed, int extra) {
        int roomScore = total != 0 ? clamp((int) (60.0 * (completed + extra) / total), 0, 60) : 0;
        int secretScore = floor.pct > 0 ? clamp((int) (40 * Math.min(floor.pct, secretPct) / floor.pct), 0, 40) : 0;
        return roomScore + secretScore;
    }

    private static int timeScore(FloorReq floor) {
        int base = 100;
        if (timeSecs < floor.time) return base;
        double past = ((double) (timeSecs - floor.time) / floor.time) * 100;
        if (past < 20) return base - (int) past / 2;
        if (past < 40) return base - (int) (10 + (past - 20) / 4);
        if (past < 50) return base - (int) (15 + (past - 40) / 5);
        if (past < 60) return base - (int) (17 + (past - 50) / 6);
        return clamp(base - (int) (18 + (2.0 / 3.0) + (past - 60) / 7), 0, 100);
    }

    private static int bonusScore() {
        int paul = mayorPaul ? 10 : 0;
        int crypt = clamp(crypts, 0, 5);
        int mimic = mimicKilled ? 2 : 0;
        if (secretPct >= 100 && mimicFloor) mimic = 2; 
        int prince = princeKilled ? 1 : 0;
        return paul + crypt + mimic + prince;
    }

    private static int deathPenalty() { return deaths * 2; }

    
    // settles sooner instead of jump...
    private static int extraRooms(boolean entrance) {
        if (!bloodDone()) return entrance ? 1 : 2;
        if (!ConstellationClient.dungeon().inBoss() && !entrance) return 1;
        return 0;
    }

    

    private static final Pattern DEATH = Pattern.compile("\\s*" + '\u2620' + " \\S+ .*");

    public static void onChat(String msg) {
        if (DEATH.matcher(msg).matches()) return;
        // old score-mod compatibility marker plus the raw hypixel lines
        if (msg.endsWith("Mimic dead!") || msg.endsWith("Mimic Killed!")
            || msg.contains("SKY" + "TILS-DUNGEON-SCORE-MIMIC")) { mimicKilled = true; return; }
        if (msg.endsWith("Prince dead!") || msg.endsWith("Prince Killed!")
            || msg.equals("A Prince falls. +1 Bonus Score")) { princeKilled = true; return; }
        // watcher says pass — blood is done but score needs +1 room, delay 5.2s like skyblocker does
        if (msg.equals("[BOSS] The Watcher: You have proven yourself. You may pass.")) { bloodAt = System.currentTimeMillis(); return; }
        if (msg.startsWith("[BOSS] ") && !msg.startsWith("[BOSS] The Watcher")) bossAt = System.currentTimeMillis();
        // m7 phase from the exact boss dialogue (real hypixel lines)
        if (msg.startsWith("[BOSS] Maxor: WELL! WELL! WELL!")) m7Phase = "P1 Maxor";
        else if (msg.startsWith("[BOSS] Storm: Pathetic Maxor")) m7Phase = "P2 Storm";
        else if (msg.startsWith("[BOSS] Goldor: Who dares trespass")) m7Phase = "P3 Goldor";
        else if (msg.startsWith("[BOSS] Necron: You went further")) m7Phase = "P4 Necron";
        else if (msg.startsWith("[BOSS] Necron: All this, for nothing")) m7Phase = "P5 Dragons";
    }

    private static String m7Phase = "";
    public static String m7Phase() { return ConstellationClient.dungeon().bossPhase(); }

    public static void reset() {
        active = false; deaths = 0; mimicKilled = false; princeKilled = false;
        bloodAt = 0; bossAt = 0; clearAt = 0; inBoss = false; score = 0; grade = "D"; secretPct = 0; crypts = 0; timeSecs = 0; m7Phase = "";
        sent270 = false; sent300 = false; floorName = ""; mimicFloor = false;
        roomsCleared = 0; roomsTotal = 0; completionPct = 0;
    }

    public static void setMayorPaul(boolean paul) { mayorPaul = paul; }

    

    private static String floorName(List<String> side) {
        Matcher m = matchContains(side, FLOOR);
        if (m == null) return "";
        String f = m.group("f").trim();
        if (f.isEmpty()) return "";
        
        return Character.isDigit(f.charAt(f.length() - 1)) ? f : "E";
    }

    private static double clearedFrac(List<String> side) {
        Matcher m = matchContains(side, CLEARED);
        return m != null ? Integer.parseInt(m.group("c")) / 100.0 : 0;
    }

    private static double secretsPct(List<String> tab) {
        Matcher m = TabList.find(tab, SECRETS);
        return m != null ? Double.parseDouble(m.group("p")) : 0;
    }

    private static int completedRooms(List<String> tab) {
        Matcher m = TabList.find(tab, COMPLETED);
        return m != null ? Integer.parseInt(m.group("r")) : 0;
    }

    private static int crypts(List<String> tab) {
        Matcher m = TabList.find(tab, CRYPTS);
        return m != null ? Integer.parseInt(m.group("c")) : 0;
    }

    private static int sidebarDeaths(List<String> side) {
        Matcher m = matchContains(side, DEATHS_SIDEBAR);
        return m != null ? Integer.parseInt(m.group("d")) : 0;
    }

    private static int incompletePuzzles(List<String> tab) {
        int n = 0;
        boolean inSection = false;
        int remaining = 0;
        for (String line : tab) {
            if (!inSection) {
                Matcher pc = PUZZLE_COUNT.matcher(line);
                if (pc.matches()) { inSection = true; remaining = Integer.parseInt(pc.group("n")); }
                continue;
            }
            if (remaining <= 0) break;
            Matcher pm = PUZZLE.matcher(line);
            if (!pm.matches()) break;
            remaining--;
            String state = pm.group("state");
            if (state.equals("\u2716") || state.equals("\u2726")) n++;
        }
        return n;
    }

    private static Matcher matchContains(List<String> lines, Pattern p) {
        for (String l : lines) { Matcher m = p.matcher(l); if (m.find()) return m; }
        return null;
    }

    private static String grade(int s) {
        if (s >= 300) return "S+";
        if (s >= 270) return "S";
        if (s >= 230) return "A";
        if (s >= 160) return "B";
        if (s >= 100) return "C";
        return "D";
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    
    public static boolean isActive() { return active; }
    public static int score() { return score; }
    public static String grade() { return grade; }
    public static int secretPercent() { return (int) secretPct; }
    public static int crypts() { return crypts; }
    public static int deaths() { return ConstellationClient.dungeon().deaths(); }
    public static int timeSeconds() { return timeSecs; }
    public static String floor() { return ConstellationClient.dungeon().floor(); }
    public static boolean isMimicFloor() { return mimicFloor; }
    public static boolean mimicKilled() { return mimicKilled; }
    public static boolean hadRun() { return timeSecs > 10; } 
    public static boolean inBoss() { return ConstellationClient.dungeon().inBoss(); }
    public static String lastFloor() { return floorName.isEmpty() ? null : floorName; }
    public static int roomsCleared() { return roomsCleared; }
    public static int roomsTotal() { return roomsTotal; }
    public static int completionPercent() { return completionPct; }
    /** argb grade colour matching skyblocker's DungeonScoreHUD, for the score panel headline. */
    public static int gradeColor() {
        if (score >= 270) return 0xFFF1E252; // S / S+ — gold
        if (score >= 230) return 0xFF7F3FB2; // A — purple
        if (score >= 160) return 0xFF7FCC19; // B — green
        if (score >= 100) return 0xFF4141FF; // C — blue
        return 0xFFDC1A1A;                    // D — red
    }
    // section splits are anchored to DungeonState's authoritative run-start timestamp (the same
    // clock timeSecs uses), so each split is the true elapsed time when that event fired.
    public static long bloodSplitMs() { long st = ConstellationClient.dungeon().startTime(); return bloodAt > 0 && st > 0 ? bloodAt - st : 0; }
    public static long bossSplitMs() { long st = ConstellationClient.dungeon().startTime(); return bossAt > 0 && st > 0 ? bossAt - st : 0; }
    public static long clearSplitMs() { long st = ConstellationClient.dungeon().startTime(); return clearAt > 0 && st > 0 ? clearAt - st : 0; }

    private enum FloorReq {
        E(30, 1200), F1(30, 600), F2(40, 600), F3(50, 600), F4(60, 720), F5(70, 600),
        F6(85, 720), F7(100, 840), M1(100, 480), M2(100, 480), M3(100, 480), M4(100, 480),
        M5(100, 480), M6(100, 600), M7(100, 840), NONE(0, 0);

        final int pct;
        final int time;
        FloorReq(int pct, int time) { this.pct = pct; this.time = time; }

        static FloorReq from(String name) {
            try { return name.isEmpty() ? NONE : valueOf(name); }
            catch (IllegalArgumentException e) { return NONE; }
        }
    }
}
