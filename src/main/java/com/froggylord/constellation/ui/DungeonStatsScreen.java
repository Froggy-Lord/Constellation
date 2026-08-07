package com.froggylord.constellation.ui;

import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RunStats;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class DungeonStatsScreen extends Screen {
    private static final String[] FLOORS = {"ALL", "E", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final Screen parent;
    private String floor = "ALL";
    private double scroll;

    public DungeonStatsScreen(Screen parent) { super(Component.literal("Dungeon Stats")); this.parent = parent; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xE0080810);
        int x = 12, panelX = 90, w = width - panelX - 12;
        g.text(font, "Dungeon Records", 12, 12, ConstellationTheme.ACCENT_BRIGHT, false);
        int fy = 32;
        for (String f : FLOORS) {
            g.fill(x, fy, x + 62, fy + 16, f.equals(floor) ? 0xFF34506A : inside(mx, my, x, fy, 62, 16) ? 0xFF303044 : 0xFF20202C);
            g.text(font, f, x + 6, fy + 5, f.equals(floor) ? 0xFFFFFFFF : ConstellationTheme.TEXT_MUTED, false);
            fy += 19;
        }
        List<OrionConfig.DungeonRunRecord> rows = filtered();
        long best = rows.stream().mapToLong(r -> r.totalMs).filter(v -> v > 0).min().orElse(0);
        double avg = rows.stream().mapToLong(r -> r.totalMs).filter(v -> v > 0).average().orElse(0);
        int bestScore = rows.stream().mapToInt(r -> r.score).max().orElse(0);
        g.text(font, floor + "  runs " + rows.size() + "  best " + time(best) + "  average " + time((long) avg) + "  best score " + bestScore,
            panelX, 13, ConstellationTheme.TEXT, false);
        g.text(font, "Date          Score   Total    Blood    Boss     Terminal milestones", panelX, 33, ConstellationTheme.TEXT_MUTED, false);
        int y = 49 - (int) scroll;
        for (OrionConfig.DungeonRunRecord r : rows) {
            if (y > 42 && y < height - 34) drawRow(g, r, panelX, y, w);
            y += 31;
        }
        button(g, width - 174, height - 25, 76, "Export JSON", mx, my);
        button(g, width - 92, height - 25, 80, "Clear " + floor, mx, my);
    }

    private void drawRow(GuiGraphicsExtractor g, OrionConfig.DungeonRunRecord r, int x, int y, int w) {
        g.fill(x, y, x + w, y + 27, 0xC0181825);
        String terminals = r.terminalMs == null || r.terminalMs.isEmpty() ? "-" : r.terminalMs.stream().limit(8).map(DungeonStatsScreen::time).reduce((a,b) -> a + " " + b).orElse("-");
        String line = DATE.format(Instant.ofEpochMilli(r.timestamp)) + "  " + r.floor + " " + r.score + " " + r.grade
            + "  " + time(r.totalMs) + "  " + time(r.bloodMs) + "  " + time(r.bossMs) + "  " + terminals;
        g.text(font, line, x + 6, y + 9, ConstellationTheme.TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int y = 32;
        for (String f : FLOORS) {
            if (inside((int) event.x(), (int) event.y(), 12, y, 62, 16)) { floor = f; scroll = 0; return true; }
            y += 19;
        }
        if (inside((int) event.x(), (int) event.y(), width - 174, height - 25, 76, 18)) { RunStats.export(); return true; }
        if (inside((int) event.x(), (int) event.y(), width - 92, height - 25, 80, 18)) { RunStats.clear(floor); return true; }
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, filtered().size() * 31 - (height - 85));
        scroll = Math.clamp(scroll - sy * 24, 0, max); return true;
    }

    private List<OrionConfig.DungeonRunRecord> filtered() { return RunStats.records().stream().filter(r -> floor.equals("ALL") || r.floor.equals(floor)).toList(); }
    private void button(GuiGraphicsExtractor g, int x, int y, int w, String text, int mx, int my) { g.fill(x,y,x+w,y+18,inside(mx,my,x,y,w,18)?0xFF3C3C55:0xFF252538); g.text(font,text,x+(w-font.width(text))/2,y+6,ConstellationTheme.TEXT,false); }
    private static String time(long ms) { if (ms <= 0) return "-"; long s = ms / 1000; return String.format(Locale.ROOT, "%d:%02d.%03d", s / 60, s % 60, ms % 1000); }
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
}
