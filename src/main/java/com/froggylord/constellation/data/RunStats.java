package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.constellation.TerminalBreakdown;
import com.froggylord.constellation.ui.DungeonStatsScreen;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class RunStats {

    private RunStats() {}

    private static int runCount = 0;
    private static int bestScore = 0;
    private static String lastSummary = "";
    private static boolean completed;

    public static void onChat(String message) {
        if (net.minecraft.ChatFormatting.stripFormatting(message).trim().equals("> EXTRA STATS <")) {
            completed = true;
            ConstellationClient.tick().once(2, "save-dungeon-run", RunStats::finishRun);
        }
    }

    public static void finishRun() {
        if (!DungeonScore.hadRun() || !completed) { completed = false; return; }

        int score = DungeonScore.score();
        String grade = DungeonScore.grade();
        int secrets = DungeonScore.secretPercent();
        int t = DungeonScore.timeSeconds();
        int deaths = ConstellationClient.dungeon().deaths();
        int crypts = DungeonScore.crypts();
        String floor = DungeonScore.lastFloor();
        if (floor == null || floor.isBlank()) { completed = false; return; }

        runCount++;
        boolean pb = score > bestScore;
        if (pb) bestScore = score;

        String time = t / 60 + ":" + String.format("%02d", t % 60);
        lastSummary = "§6Score §f" + score + " §7(" + grade + ")  §6Secrets §f" + secrets + "%  "
            + "§6Time §f" + time + "  §6Deaths §f" + deaths + "  §6Crypts §f" + crypts;

        saveRecord(floor, score, grade, secrets, deaths, crypts);
        completed = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§8§m                                        "));
        mc.player.sendSystemMessage(Component.literal("§b✦ Run complete" + (floor.isEmpty() ? "" : " §7(" + floor + ")") + (pb ? "  §a§lNEW BEST" : "")));
        mc.player.sendSystemMessage(Component.literal(lastSummary));
        mc.player.sendSystemMessage(Component.literal("§7Session: §f" + runCount + " run" + (runCount == 1 ? "" : "s") + "  §7best §f" + bestScore));
        mc.player.sendSystemMessage(Component.literal("§8§m                                        "));
    }

    public static void printSession() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b✦ Constellation — session dungeon stats"));
        mc.player.sendSystemMessage(Component.literal("§7Runs: §f" + runCount + "  §7Best score: §f" + bestScore));
        if (!lastSummary.isEmpty())
            mc.player.sendSystemMessage(Component.literal("§7Last: " + lastSummary));
    }

    public static int runCount() { return runCount; }
    public static int bestScore() { return bestScore; }

    // run records ported from Odin (BSD-3-Clause): utils/PersonalBest.kt and utils/skyblock/SplitsManager.kt
    private static void saveRecord(String floor, int score, String grade, int secrets, int deaths, int crypts) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (!cfg.saveDungeonRunHistory) return;
        if (cfg.dungeonRunHistory == null) cfg.dungeonRunHistory = new java.util.ArrayList<>();
        OrionConfig.DungeonRunRecord record = new OrionConfig.DungeonRunRecord();
        record.timestamp = System.currentTimeMillis(); record.floor = floor; record.score = score; record.grade = grade;
        record.secrets = secrets; record.deaths = deaths; record.crypts = crypts;
        record.totalMs = DungeonScore.clearSplitMs(); record.bloodMs = DungeonScore.bloodSplitMs(); record.bossMs = DungeonScore.bossSplitMs();
        record.terminalMs = floor.endsWith("7") ? TerminalBreakdown.lastTerminalTimes() : List.of();
        cfg.dungeonRunHistory.add(record);
        int limit = Math.clamp(cfg.dungeonRunHistoryLimit, 0, 10_000);
        while (cfg.dungeonRunHistory.size() > limit) cfg.dungeonRunHistory.remove(0);
        ConstellationClient.saveConfig();
    }

    public static List<OrionConfig.DungeonRunRecord> records() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg.dungeonRunHistory == null) cfg.dungeonRunHistory = new java.util.ArrayList<>();
        return cfg.dungeonRunHistory.stream().sorted(Comparator.comparingLong((OrionConfig.DungeonRunRecord r) -> r.timestamp).reversed()).toList();
    }

    public static int open() { Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new DungeonStatsScreen(null))); return 1; }
    public static int clear(String floor) {
        OrionConfig cfg = ConstellationClient.cfg().orion; int before = records().size();
        if (floor.equalsIgnoreCase("all")) cfg.dungeonRunHistory.clear();
        else cfg.dungeonRunHistory.removeIf(r -> r.floor.equalsIgnoreCase(floor));
        ConstellationClient.saveConfig(); message("Removed " + (before - cfg.dungeonRunHistory.size()) + " run records."); return 1;
    }
    public static int export() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        try {
            Path dir = Path.of(cfg.dungeonRunExportFolder == null ? "config/constellation-run-stats" : cfg.dungeonRunExportFolder).normalize();
            if (dir.isAbsolute() || dir.startsWith("..")) { message("Export folder must be relative to the instance."); return 0; }
            Files.createDirectories(dir);
            Path file = dir.resolve("dungeon-runs-" + System.currentTimeMillis() + ".json");
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(records()));
            message("Exported " + records().size() + " runs to " + file + '.'); return 1;
        } catch (Exception e) { message("Export failed: " + e.getMessage()); return 0; }
    }
    public static int limit(int runs) { OrionConfig cfg = ConstellationClient.cfg().orion; cfg.dungeonRunHistoryLimit = runs; while (cfg.dungeonRunHistory.size() > runs) cfg.dungeonRunHistory.remove(0); ConstellationClient.saveConfig(); message("History limit set to " + runs + '.'); return 1; }
    public static int folder(String path) { OrionConfig cfg = ConstellationClient.cfg().orion; Path p = Path.of(path).normalize(); if (p.isAbsolute() || p.startsWith("..")) { message("Folder must be relative to the instance."); return 0; } cfg.dungeonRunExportFolder = p.toString(); ConstellationClient.saveConfig(); message("Export folder set to " + p + '.'); return 1; }
    private static void message(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§bDungeon Stats §8> §f" + text)); }
}
