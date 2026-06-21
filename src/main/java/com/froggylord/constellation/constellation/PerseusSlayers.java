package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Perseus — slayers. Reads the boss name + remaining HP off the bossbar (the vanilla bossbar
 * becomes the slayer boss on Hypixel) and the slayer XP off the sidebar. Both are stable
 * sidebar-/bar-based signals, no chat-message format guessing needed.
 */
public class PerseusSlayers extends BaseConstellation {

    @Override public String id() { return "perseus"; }
    @Override public String displayName() { return "Perseus"; }
    @Override public String description() { return "Slayers — boss timer, XP bar, miniboss alerts"; }

    private static final Pattern SLAYER_XP = Pattern.compile("Slayer XP:?\\s*([\\d,]+)");
    private static String bossName = "";
    private static double bossHealth = 0;
    private static long bossSince = 0;
    private static long sessionXp = -1;

    private PerseusConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (PerseusConfig) getConfig();
        ConstellationClient.tick().every(4, "perseus-boss", PerseusSlayers::readBossbar);
    }

    private static void readBossbar() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        var events = ((com.froggylord.constellation.mixin.BossHealthOverlayAccessor)(Object)mc.gui.getBossOverlay()).constellation$events();
        if (events.isEmpty()) { bossName = ""; bossHealth = 0; bossSince = 0; return; }
        var e = (net.minecraft.client.gui.components.LerpingBossEvent) events.values().stream().reduce((a,b) -> b).orElse(null);
        if (e == null) { bossName = ""; bossHealth = 0; bossSince = 0; return; }
        bossName = e.getName().getString();
        bossHealth = e.getProgress(); // 0..1
        if (bossSince == 0) bossSince = System.currentTimeMillis();
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (PerseusConfig) getConfig();
        if (cfg == null) return;

        if (cfg.bossTimer) {
            hud.register(new HudWidget("perseus-boss", "Boss",
                () -> {
                    if (bossName.isEmpty() || bossHealth <= 0) return null;
                    long s = (System.currentTimeMillis() - bossSince) / 1000;
                    return "§c" + bossName + " §7" + (int)(bossHealth*100) + "% §f" + s/60 + ":" + String.format("%02d", s%60);
                },
                HudPosition.of(50, 78), cfg.bossTimer));
        }
        if (cfg.xpBar) {
            hud.register(new HudWidget("perseus-xp", "SlayerXP",
                () -> readXp() > 0 ? "§d" + compact(readXp()) + " XP" : null,
                HudPosition.of(50, 86), cfg.xpBar));
        }
    }

    private static long readXp() {
        if (sessionXp < 0) {
            for (String line : ConstellationClient.loc().getSidebarLines()) {
                Matcher m = SLAYER_XP.matcher(line);
                if (m.find()) { sessionXp = parse(m.group(1)); break; }
            }
        }
        return sessionXp;
    }

    private static long parse(String s) { try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; } }
    private static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        return String.format("%.2fM", n / 1_000_000.0);
    }
}
