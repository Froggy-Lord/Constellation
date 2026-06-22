package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.data.TabList;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aquila — mining. For now: powder totals off the sidebar and commission progress off the tab
 * list, shown only in the mining worlds. Patterns are best-effort English literals; a live line
 * may need a tweak, but the structure (sidebar for powder, tab for commissions) matches how the
 * other mods read them.
 */
public class AquilaMining extends BaseConstellation {

    @Override public String id() { return "aquila"; }
    @Override public String displayName() { return "Aquila"; }
    @Override public String description() { return "Mining — powder, commissions, Crystal Hollows"; }

    private static final Pattern POWDER = Pattern.compile("(Mithril|Gemstone|Glacite) Powder:?\\s*([\\d,]+)");
    private static final Pattern COMMISSION = Pattern.compile("(?<name>[A-Za-z ]+?): (?<val>\\d+(?:\\.\\d+)?%|DONE)");
    private static final Pattern FORGE = Pattern.compile("(?<slot>\\d+)\\. (?<item>.+): (?<time>\\d+h|\\d+m|\\d+s|Ready!)");
    private static final Pattern COMPASS = Pattern.compile("Wishing Compass:?\\s*(\\d+)\\s*(\\d+)\\s*(\\d+)");
    private static final Pattern FUEL = Pattern.compile("Fuel:?\\s*(\\d+\\.?\\d*)/(\\d+\\.?\\d*)k?");
    private static final Pattern COLD = Pattern.compile("Cold:?\\s*-?(\\d+)");
    private static final Pattern HOTM = Pattern.compile("HOTM:?\\s*(\\d+)");
    private static final int[] COLD_STEPS = {25, 50, 75, 90, 95, 99};

    private AquilaConfig cfg;

    private static int lastColdStep = 0;

    @Override
    public void init(InitContext ctx) {
        cfg = (AquilaConfig) getConfig();
        // cold thresholds — a clean title ping instead of the screen-filling vignette
        ConstellationClient.tick().every(10, "aquila-cold", () -> {
            if (cfg == null || !cfg.coldWarning || !inMining()) return;
            int cold = readCold();
            int step = 0;
            for (int s : COLD_STEPS) if (cold >= s) step = s;
            if (step > lastColdStep) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§b❄ Cold " + cold + "!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, step >= 90 ? 0.4f : 0.8f);
                }
            }
            lastColdStep = step;
        });
        // mineshaft entry — the rare portal everyone wants to know they hit
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || cfg == null || !cfg.mineshaftAlert || !ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();
            if (s.contains("You have entered a Glacite Mineshaft") || s.contains("found a Glacite Mineshaft")) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§b⛏ Mineshaft!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.0f);
                }
            }
        });
    }

    private static int readCold() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = COLD.matcher(line);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (AquilaConfig) getConfig();
        if (cfg == null) return;

        if (cfg.powderHud) {
            hud.register(new HudWidget("aquila-powder", "Powder",
                () -> inMining() ? powderLine() : null,
                HudPosition.of(2, 60), cfg.powderHud));
        }
        if (cfg.commissionHud) {
            hud.register(new HudWidget("aquila-commissions", "Commissions",
                () -> inMining() ? commissionLine() : null,
                HudPosition.of(2, 70), cfg.commissionHud));
            hud.register(new HudWidget("aquila-forge", "Forge",
                () -> inMining() ? forgeLine() : null,
                HudPosition.of(2, 80), cfg.commissionHud));
            hud.register(new HudWidget("aquila-compass", "Compass",
                () -> inMining() ? compassLine() : null,
                HudPosition.of(2, 90), cfg.commissionHud));
            hud.register(new HudWidget("aquila-fuel", "Fuel",
                () -> inMining() ? fuelLine() : null,
                HudPosition.of(2, 98), cfg.commissionHud));
        }
        if (cfg.coldHud) {
            hud.register(new HudWidget("aquila-cold", "Cold",
                () -> {
                    if (!inMining()) return null;
                    int c = readCold();
                    if (c <= 0) return null;
                    String col = c >= 90 ? "§c" : c >= 50 ? "§b" : "§7";
                    return col + "❄ " + c + "/100";
                },
                HudPosition.of(2, 106), cfg.coldHud));
        }
        if (cfg.hotmHud) {
            hud.register(new HudWidget("aquila-hotm", "HOTM",
                () -> {
                    if (!inMining()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = HOTM.matcher(line);
                        if (m.find()) return "§6HOTM " + m.group(1);
                    }
                    return null;
                },
                HudPosition.of(2, 114), cfg.hotmHud));
        }
    }

    private static boolean inMining() {
        SkyblockArea a = ConstellationClient.loc().area();
        return a == SkyblockArea.DWARVEN_MINES || a == SkyblockArea.CRYSTAL_HOLLOWS
            || a == SkyblockArea.GLACITE_TUNNELS || a == SkyblockArea.GLACITE_MINESHAFT;
    }

    /** All powder types from the sidebar. */
    private static String powderLine() {
        String mithril = null, gemstone = null, glacite = null;
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = POWDER.matcher(line);
            if (!m.find()) continue;
            switch (m.group(1)) {
                case "Mithril" -> mithril = m.group(2);
                case "Gemstone" -> gemstone = m.group(2);
                case "Glacite" -> glacite = m.group(2);
                default -> { }
            }
        }
        if (mithril == null && gemstone == null && glacite == null) return null;
        StringBuilder sb = new StringBuilder();
        if (mithril != null) sb.append("§2").append(mithril).append("m");
        if (gemstone != null) sb.append(sb.length() > 0 ? "  " : "").append("§d").append(gemstone).append("g");
        if (glacite != null) sb.append(sb.length() > 0 ? "  " : "").append("§b").append(glacite).append("g");
        return sb.toString();
    }

    /** Active commissions + progress from the tab list. */
    private static String commissionLine() {
        List<String> tab = TabList.lines();
        boolean inSection = false;
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String line : tab) {
            if (line.startsWith("Commissions")) { inSection = true; continue; }
            if (!inSection) continue;
            Matcher m = COMMISSION.matcher(line);
            if (!m.matches()) break; // section ended
            if (shown++ > 0) sb.append("  §7| ");
            String val = m.group("val");
            sb.append("§f").append(m.group("name").trim()).append(" §b").append(val);
            if (shown >= 2) break;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Forge slots with remaining time. */
    private static String forgeLine() {
        var tab = TabList.lines();
        boolean section = false;
        StringBuilder sb = new StringBuilder();
        for (String line : tab) {
            if (line.contains("Forge")) { section = true; continue; }
            if (!section) continue;
            Matcher m = FORGE.matcher(line);
            if (!m.find()) break;
            if (sb.length() > 0) sb.append(" §7|");
            String time = m.group("time");
            sb.append("§f").append(m.group("item").trim()).append(" §7").append(time);
        }
        return sb.length() == 0 ? null : "§6" + sb.toString();
    }

    private static String compassLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = COMPASS.matcher(line);
            if (m.find()) return "§6🧭 " + m.group(1) + " " + m.group(2) + " " + m.group(3);
        }
        return null;
    }

    private static String fuelLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = FUEL.matcher(line);
            if (m.find()) return "§2⛏ Fuel §f" + m.group(1) + "/" + m.group(2) + "k";
        }
        return null;
    }
}
