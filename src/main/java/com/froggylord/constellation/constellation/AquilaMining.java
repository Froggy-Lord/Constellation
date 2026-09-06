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

public class AquilaMining extends BaseConstellation {

    @Override public String id() { return "aquila"; }
    @Override public String displayName() { return "Aquila"; }
    @Override public String description() { return "mining hud"; }

    // live tab data: "Mithril: 2,684,037" - no "Powder" word between
    private static final Pattern POWDER = Pattern.compile("(Mithril|Gemstone|Glacite):?\\s*([\\d,]+)");
    private static final Pattern COMMISSION = Pattern.compile("(?<name>[A-Za-z ]+?): (?<val>\\d+(?:\\.\\d+)?%|DONE)");
    // live tab data: "1) EMPTY" - parens not dot
    private static final Pattern FORGE = Pattern.compile("(?<slot>\\d+)\\)\\s*(?<item>.+):\\s*(?<time>\\d+h|\\d+m|\\d+s|Ready!)");
    private static final Pattern COMPASS = Pattern.compile("Wishing Compass:?\\s*(\\d+)\\s*(\\d+)\\s*(\\d+)");
    private static final Pattern FUEL = Pattern.compile("Fuel:?\\s*(\\d+\\.?\\d*)/(\\d+\\.?\\d*)k?");
    private static final Pattern COLD = Pattern.compile("Cold:?\\s*-?(\\d+)");
    private static final Pattern HOTM = Pattern.compile("HOTM:?\\s*(\\d+)");
    private static final Pattern DRILL_FUEL = Pattern.compile("(?:Drill\\s*)?Fuel:?\\s*([\\d,\\.]+[kKmM]?)\\s*/?\\s*([\\d,\\.]+[kKmM]?)?");
    private static final Pattern PICKONIMBUS = Pattern.compile("Pickonimbus:?\\s*([\\d,]+)\\s*/?\\s*([\\d,]+)");
    private static double compassX = Double.NaN, compassZ = Double.NaN;
    private static long compassSetAt = 0;
    private static int scathaKills = 0;

    
    private static final int[] COLD_STEPS = {25, 50, 75, 90, 95, 99};

    private AquilaConfig cfg;

    private static int lastColdStep = 0;

    @Override
    public void init(InitContext ctx) {
        cfg = (AquilaConfig) config;
        AquilaMiningProgress.init(cfg);
        AquilaCorpseHelper.init(cfg);
        AquilaMiningGuidance.init(cfg);
        AquilaForgeHelper.init(cfg);
        registerRenderer(context -> { if (isEnabled() && cfg.enabled) { AquilaCorpseHelper.draw(context); AquilaMiningGuidance.draw(context); } });
    }

    private static int readCold() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = COLD.matcher(line);
            if (m.find()) return Math.abs(Integer.parseInt(m.group(1))); // skyhanni takes absolute
        }
        return 0;
    }

    @Override
    public void registerHud(HudManager hud) {
        var c = (AquilaConfig) config;
        hud.register(new com.froggylord.constellation.hud.MiningCommissionHudWidget(HudPosition.of(2, 22),
            () -> c.enabled && c.miningProgressSuite && c.miningCommissionProgressHud));
        hud.register(new com.froggylord.constellation.hud.MiningCrystalHudWidget(HudPosition.of(2, 36),
            () -> c.enabled && c.miningProgressSuite && c.miningCrystalStatusHud));
        hud.register(new com.froggylord.constellation.hud.MineshaftPityHudWidget(HudPosition.of(78, 22),
            () -> c.enabled && c.miningProgressSuite && c.miningMineshaftPityHud));
        hud.register(new com.froggylord.constellation.hud.MineshaftTimerHudWidget(HudPosition.of(78, 38),
            () -> c.enabled && c.miningProgressSuite && c.miningMineshaftTimerHud));
        hud.register(new com.froggylord.constellation.hud.CorpseKeyHudWidget(HudPosition.of(78, 54),
            () -> c.enabled && c.corpseSuite && c.corpseKeyHud));
        hud.register(new com.froggylord.constellation.hud.CorpseTrackerHudWidget(HudPosition.of(78, 68),
            () -> c.enabled && c.corpseSuite && c.corpseTrackerHud));
        hud.register(new com.froggylord.constellation.hud.MiningDailyHudWidget(HudPosition.of(78, 82),
            () -> c.enabled && c.miningGuidanceSuite && c.miningDailyHud));
        hud.register(new com.froggylord.constellation.hud.ForgeHudWidget(HudPosition.of(78, 96),
            () -> c.enabled && c.forgeSuite && c.forgeTrackerHud));
    }

    @Override
    public void registerCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher) {
        AquilaMiningProgress.registerCommands(dispatcher);
        AquilaCorpseHelper.registerCommands(dispatcher);
        AquilaMiningGuidance.registerCommands(dispatcher);
        AquilaForgeHelper.registerCommands(dispatcher);
    }

    private static boolean inMining() {
        SkyblockArea a = ConstellationClient.loc().area();
        return a == SkyblockArea.DWARVEN_MINES || a == SkyblockArea.CRYSTAL_HOLLOWS
            || a == SkyblockArea.GLACITE_TUNNELS || a == SkyblockArea.GLACITE_MINESHAFT;
    }

    // live tab data: "Powders:" section to "Mithril: 2,684,037" etc
    private static String powderLine() {
        var tab = TabList.lines();
        boolean inSection = false;
        String mithril = null, gemstone = null, glacite = null;
        for (String line : tab) {
            if (line.startsWith("Powders")) { inSection = true; continue; }
            if (!inSection) continue;
            if (line.length() < 3) break;
            Matcher m = POWDER.matcher(line);
            if (!m.find()) break;
            switch (m.group(1)) {
                case "Mithril" -> mithril = m.group(2);
                case "Gemstone" -> gemstone = m.group(2);
                case "Glacite" -> glacite = m.group(2);
                default -> { }
            }
        }
        if (mithril == null && gemstone == null && glacite == null) {
            ConstellationClient.verifyLog("aquila-powder", false, "no powder section in tab");
            return null;
        }
        ConstellationClient.verifyLog("aquila-powder", true, "M:" + mithril + " G:" + gemstone + " Gl:" + glacite);
        StringBuilder sb = new StringBuilder();
        if (mithril != null) sb.append("§2").append(mithril).append("m");
        if (gemstone != null) sb.append(sb.length() > 0 ? "  " : "").append("§d").append(gemstone).append("g");
        if (glacite != null) sb.append(sb.length() > 0 ? "  " : "").append("§b").append(glacite).append("g");
        return sb.toString();
    }

    private static String commissionLine() {
        List<String> tab = TabList.lines();
        boolean inSection = false;
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String line : tab) {
            if (line.startsWith("Commissions")) { inSection = true; continue; }
            if (!inSection) continue;
            Matcher m = COMMISSION.matcher(line);
            if (!m.matches()) break; 
            if (shown++ > 0) sb.append("  §7| ");
            String val = m.group("val");
            sb.append("§f").append(m.group("name").trim()).append(" §b").append(val);
            if (shown >= 2) break;
        }
        if (sb.length() == 0) {
            ConstellationClient.verifyLog("aquila-commission", false, "no commissions in tab");
            return null;
        }
        ConstellationClient.verifyLog("aquila-commission", true, sb.toString());
        return sb.toString();
    }

    private static String forgeLine() {
        var tab = TabList.lines();
        boolean section = false;
        StringBuilder sb = new StringBuilder();
        for (String line : tab) {
            if (line.contains("Forge")) { section = true; continue; }
            if (!section) continue;
            Matcher m = FORGE.matcher(line);
            if (!m.find()) continue; // skip EMPTY slots, don't exit section
            if (sb.length() > 0) sb.append(" §7|");
            String time = m.group("time");
            sb.append("§f").append(m.group("item").trim()).append(" §7").append(time);
        }
        if (sb.length() == 0) {
            ConstellationClient.verifyLog("aquila-forge", false, "no active forges");
            return null;
        }
        ConstellationClient.verifyLog("aquila-forge", true, sb.toString());
        return "§6" + sb.toString();
    }

    private static String compassLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = COMPASS.matcher(line);
            if (m.find()) return "§6Compass " + m.group(1) + " " + m.group(2) + " " + m.group(3);
        }
        return null;
    }

    private static String fuelLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = FUEL.matcher(line);
            if (m.find()) return "§2Fuel §f" + m.group(1) + "/" + m.group(2) + "k";
        }
        return null;
    }
}
