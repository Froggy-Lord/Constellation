package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RunStats {

    private RunStats() {}

    private static int runCount = 0;
    private static int bestScore = 0;
    private static String lastSummary = "";

    public static void finishRun() {
        if (!DungeonScore.hadRun()) return;

        int score = DungeonScore.score();
        String grade = DungeonScore.grade();
        int secrets = DungeonScore.secretPercent();
        int t = DungeonScore.timeSeconds();
        int deaths = DungeonScore.deaths();
        int crypts = DungeonScore.crypts();
        String floor = DungeonScore.floor();

        runCount++;
        boolean pb = score > bestScore;
        if (pb) bestScore = score;

        String time = t / 60 + ":" + String.format("%02d", t % 60);
        lastSummary = "§6Score §f" + score + " §7(" + grade + ")  §6Secrets §f" + secrets + "%  "
            + "§6Time §f" + time + "  §6Deaths §f" + deaths + "  §6Crypts §f" + crypts;

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
}
