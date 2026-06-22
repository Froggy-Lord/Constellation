package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hercules — farming. Pulls your current Jacob's contest progress off the tab list and
 * visitor queue status off the sidebar. Both are stable Hypixel signals; the garden is the
 * only place visitors appear.
 */
public class HerculesFarming extends BaseConstellation {

    @Override public String id() { return "hercules"; }
    @Override public String displayName() { return "Hercules"; }
    @Override public String description() { return "Farming — contests, visitors, garden helpers"; }

    private static final Pattern CONTEST = Pattern.compile("(?<crop>[A-Za-z ]+):?\\s*(?<pct>\\d+(?:\\.\\d+)?%|DONE)");
    private static final Pattern VISITORS = Pattern.compile("Visitors:?\\s*(\\d+)");
    private static final Pattern PESTS = Pattern.compile("Pests:?\\s*(\\d+)");

    private HerculesConfig cfg;

    private static long lastContestAlert = 0;
    private static int lastPestCount = 0;
    private static long lastPestAlert = 0;

    @Override
    public void init(InitContext ctx) {
        cfg = (HerculesConfig) getConfig();
        if (cfg == null) return;
        // contest start notification — check sidebar for "Starts in: 1m" or similar
        ConstellationClient.tick().every(20, "hercules-contest-alert", () -> {
            if (!inGarden()) return;
            for (String line : ConstellationClient.loc().getSidebarLines()) {
                if (line.contains("Starts in") || line.contains("Soon")) {
                    long now = System.currentTimeMillis();
                    if (now - lastContestAlert > 60_000) {
                        lastContestAlert = now;
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.gui.hud.resetTitleTimes();
                            mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§a🌾 Contest starting!"));
                            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.0f);
                        }
                    }
                    break;
                }
            }
        });
        // pest watch — the scoreboard shows a live "Pests: N" while any are loose in your plots
        ConstellationClient.tick().every(20, "hercules-pests", () -> {
            if (cfg == null || !cfg.pestAlert || !inGarden()) return;
            int pests = readPests();
            if (pests > lastPestCount && System.currentTimeMillis() - lastPestAlert > 20_000) {
                lastPestAlert = System.currentTimeMillis();
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§c🐛 " + pests + " Pest" + (pests > 1 ? "s" : "") + "!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f);
                }
            }
            lastPestCount = pests;
        });
    }

    private static int readPests() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = PESTS.matcher(line);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (HerculesConfig) getConfig();
        if (cfg == null) return;

        if (cfg.contestHud) {
            hud.register(new HudWidget("hercules-contest", "Contest",
                () -> inGarden() ? contestLine() : null,
                HudPosition.of(2, 110), cfg.contestHud));
        }
        if (cfg.visitorsHud) {
            hud.register(new HudWidget("hercules-visitors", "Visitors",
                () -> inGarden() ? visitorsLine() : null,
                HudPosition.of(2, 120), cfg.visitorsHud));
        }
        if (cfg.pestHud) {
            hud.register(new HudWidget("hercules-pests", "Pests",
                () -> {
                    if (!inGarden()) return null;
                    int p = readPests();
                    return p > 0 ? "§c🐛 " + p : null;
                },
                HudPosition.of(2, 130), cfg.pestHud));
        }
    }

    private static boolean inGarden() {
        return ConstellationClient.loc().area() == SkyblockArea.GARDEN;
    }

    private static String contestLine() {
        var tab = com.froggylord.constellation.data.TabList.lines();
        boolean section = false;
        StringBuilder sb = new StringBuilder();
        for (String line : tab) {
            if (line.startsWith("Jacob") || line.contains("Contest")) { section = true; continue; }
            if (!section) continue;
            Matcher m = CONTEST.matcher(line);
            if (!m.find()) break;
            sb.append("§a").append(m.group("crop").trim()).append(" §f").append(m.group("pct"));
            break; // first crop line
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String visitorsLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = VISITORS.matcher(line);
            if (m.find()) return "§e" + m.group(1) + " visiting";
        }
        return null;
    }
}
