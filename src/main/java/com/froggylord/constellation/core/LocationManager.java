package com.froggylord.constellation.core;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.*;

public class LocationManager {

    public enum SkyblockArea {
        UNKNOWN, HUB, PRIVATE_ISLAND, DUNGEON_HUB,
        CATACOMBS, MASTER_MODE,
        CRIMSON_ISLE, KUUDRA,
        DWARVEN_MINES, CRYSTAL_HOLLOWS, GLACITE_TUNNELS, GLACITE_MINESHAFT,
        GARDEN, THE_RIFT,
        SPIDER_DEN, BLAZING_FORTRESS, THE_END,
        BARN, MUSHROOM_DESERT, PARK, HOWLING_CAVES, GOLD_MINE, DEEP_CAVERNS
    }

    private SkyblockArea currentArea = SkyblockArea.UNKNOWN;
    private boolean onHypixel = false;
    private boolean inDungeons = false;
    private final List<String> sidebarLines = new ArrayList<>();

    public void init() {
        ConstellationClient.tick().every(20, "location-detect", this::tick);
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            currentArea = SkyblockArea.UNKNOWN;
            onHypixel = false;
            inDungeons = false;
            return;
        }

        readSidebar(mc);
        onHypixel = sidebarLines.stream().anyMatch(l -> l.contains("SKYBLOCK"));
        currentArea = classifySidebar();

        inDungeons = currentArea == SkyblockArea.CATACOMBS || currentArea == SkyblockArea.MASTER_MODE;
    }

    private void readSidebar(Minecraft mc) {
        sidebarLines.clear();
        if (mc.level == null) return;

        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return;

        List<String> lines = new ArrayList<>();
        for (ScoreHolder holder : sb.getTrackedPlayers()) {
            if (!sb.listPlayerScores(holder).containsKey(obj)) continue;
            PlayerTeam team = sb.getPlayersTeam(holder.getScoreboardName());
            if (team == null) continue;

            String line = team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
            if (line.trim().isEmpty()) continue;
            lines.add(net.minecraft.ChatFormatting.stripFormatting(line));
        }
        lines.add(obj.getDisplayName().getString());
        Collections.reverse(lines);
        sidebarLines.addAll(lines);
    }

    private SkyblockArea classifySidebar() {
        for (String line : sidebarLines) {
            String l = line.toLowerCase(Locale.ROOT);
            if (l.contains("the catacombs")) {
                return l.contains("master") ? SkyblockArea.MASTER_MODE : SkyblockArea.CATACOMBS;
            }
            if (l.contains("crimson isle")) return SkyblockArea.CRIMSON_ISLE;
            if (l.contains("dwarven mines")) return SkyblockArea.DWARVEN_MINES;
            if (l.contains("crystal hollows")) return SkyblockArea.CRYSTAL_HOLLOWS;
            if (l.contains("glacite")) return SkyblockArea.GLACITE_TUNNELS;
            if (l.contains("garden")) return SkyblockArea.GARDEN;
            if (l.contains("the rift")) return SkyblockArea.THE_RIFT;
            if (l.contains("spider")) return SkyblockArea.SPIDER_DEN;
            if (l.contains("the end")) return SkyblockArea.THE_END;
            if (l.contains("barn")) return SkyblockArea.BARN;
            if (l.contains("park")) return SkyblockArea.PARK;
            if (l.contains("gold mine")) return SkyblockArea.GOLD_MINE;
            if (l.contains("deep caverns")) return SkyblockArea.DEEP_CAVERNS;
            if (l.contains("village") || l.contains("hub")) return SkyblockArea.HUB;
        }
        return SkyblockArea.UNKNOWN;
    }

    public SkyblockArea area() { return currentArea; }
    public boolean onHypixel() { return onHypixel; }
    public boolean inDungeons() { return inDungeons; }
    public List<String> getSidebarLines() { return Collections.unmodifiableList(sidebarLines); }
}
