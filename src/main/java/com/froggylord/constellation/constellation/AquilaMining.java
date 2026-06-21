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

    private AquilaConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (AquilaConfig) getConfig();
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
}
