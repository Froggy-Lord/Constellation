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
    private String confirmClear = "";

    public DungeonStatsScreen(Screen parent) { super(Component.literal("Dungeon Stats")); this.parent = parent; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        ConstellationUi.background(g, width, height, delta);
        int x = 12, panelX = 90, w = width - panelX - 12;
        List<OrionConfig.DungeonRunRecord> rows = filtered();
        long best = rows.stream().mapToLong(r -> r.totalMs).filter(v -> v > 0).min().orElse(0);
        double avg = rows.stream().mapToLong(r -> r.totalMs).filter(v -> v > 0).average().orElse(0);
        int bestScore = rows.stream().mapToInt(r -> r.score).max().orElse(0);
        String summary = floor + "  " + rows.size() + " runs  best " + time(best) + "  avg " + time((long) avg) + "  score " + bestScore;
        ConstellationUi.header(g, font, "Dungeon Records", summary, width);
        ConstellationUi.panel(g, 8, 30, 70, height - 42);
        ConstellationUi.panel(g, panelX - 4, 30, w + 4, height - 42);
        for (int i = 0; i < FLOORS.length; i++) {
            String f = FLOORS[i];
            int fx = x + (i % 2) * 31, fy = 32 + (i / 2) * 19;
            ConstellationTheme.surface(g, fx, fy, 30, 16,
                f.equals(floor) ? 0xFF34506A : inside(mx, my, fx, fy, 30, 16) ? 0xFF303044 : 0xFF20202C,
                f.equals(floor) ? ConstellationTheme.ACCENT_DIM : ConstellationTheme.BORDER_SOFT);
            g.text(font, f, fx + (30 - font.width(f)) / 2, fy + 5,
                f.equals(floor) ? 0xFFFFFFFF : ConstellationTheme.TEXT_MUTED, false);
        }
        g.text(font, ConstellationUi.fit(font, "Date          Score   Total    Blood    Boss     Terminal milestones", w - 8),
            panelX, 33, ConstellationTheme.TEXT_MUTED, false);
        int top = 49, bottom = height - 34, y = top - (int) scroll;
        g.enableScissor(panelX - 2, top, width - 10, bottom);
        for (OrionConfig.DungeonRunRecord r : rows) {
            if (y + 27 >= top && y < bottom) drawRow(g, r, panelX, y, w);
            y += 31;
        }
        g.disableScissor();
        ConstellationUi.scrollbar(g, width - 15, top, bottom - top,
            bottom - top, rows.size() * 31, (int) scroll);
        button(g, width - 174, height - 25, 76, "Export JSON", mx, my);
        button(g, width - 92, height - 25, 80,
            confirmClear.equals(floor) ? "Confirm" : "Clear " + floor, mx, my);
    }

    private void drawRow(GuiGraphicsExtractor g, OrionConfig.DungeonRunRecord r, int x, int y, int w) {
        ConstellationTheme.surface(g, x, y, w, 27, 0xC0181825, ConstellationTheme.BORDER_SOFT);
        String terminals = r.terminalMs == null || r.terminalMs.isEmpty() ? "-" : r.terminalMs.stream().limit(8).map(DungeonStatsScreen::time).reduce((a,b) -> a + " " + b).orElse("-");
        String line = DATE.format(Instant.ofEpochMilli(r.timestamp)) + "  " + r.floor + " " + r.score + " " + r.grade
            + "  " + time(r.totalMs) + "  " + time(r.bloodMs) + "  " + time(r.bossMs) + "  " + terminals;
        g.text(font, ConstellationUi.fit(font, line, w - 12), x + 6, y + 9, ConstellationTheme.TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        for (int i = 0; i < FLOORS.length; i++) {
            String f = FLOORS[i];
            int x = 12 + (i % 2) * 31, y = 32 + (i / 2) * 19;
            if (inside((int) event.x(), (int) event.y(), x, y, 30, 16)) {
                floor = f; scroll = 0; confirmClear = ""; return true;
            }
        }
        if (inside((int) event.x(), (int) event.y(), width - 174, height - 25, 76, 18)) { RunStats.export(); return true; }
        if (inside((int) event.x(), (int) event.y(), width - 92, height - 25, 80, 18)) {
            if (confirmClear.equals(floor)) {
                RunStats.clear(floor);
                confirmClear = "";
                scroll = 0;
            } else confirmClear = floor;
            return true;
        }
        confirmClear = "";
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, filtered().size() * 31 - (height - 85));
        scroll = Math.clamp(scroll - sy * 24, 0, max); return true;
    }

    private List<OrionConfig.DungeonRunRecord> filtered() { return RunStats.records().stream().filter(r -> floor.equals("ALL") || r.floor.equals(floor)).toList(); }
    private void button(GuiGraphicsExtractor g, int x, int y, int w, String text, int mx, int my) { ConstellationUi.button(g,font,x,y,w,18,text,inside(mx,my,x,y,w,18),confirmClear.equals(floor)&&text.equals("Confirm")); }
    private static String time(long ms) { if (ms <= 0) return "-"; long s = ms / 1000; return String.format(Locale.ROOT, "%d:%02d.%03d", s / 60, s % 60, ms % 1000); }
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
}
