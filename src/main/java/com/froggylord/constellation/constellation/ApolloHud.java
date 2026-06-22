package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ApolloConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ApolloHud extends BaseConstellation {

    @Override public String id() { return "apollo"; }
    @Override public String displayName() { return "Apollo"; }
    @Override public String description() { return "Core HUD — info overlays, scoreboard, tab list"; }

    private static int tps = 20;
    private static long lastReal = 0, lastGameTime = 0;

    @Override
    public void init(InitContext ctx) {
        // compact damage numbers — strip trailing zeros from chat damage lines
        ApolloConfig cfg2 = (ApolloConfig) getConfig();
        if (cfg2 != null && cfg2.compactDamage) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
                String s = message.getString();
                // "✧ 1,234,567 ✧" → "✧ 1.2M ✧" on damage lines
                if (s.contains("✧") && s.length() < 40) {
                    s = java.util.regex.Pattern.compile("[\\d,]{4,}").matcher(s).replaceAll(
                        mr -> compactDamage(mr.group()));
                    return Component.literal(s);
                }
                return message;
            });
        }
        // estimate server TPS: how fast world game-time advances against the wall clock
        ConstellationClient.tick().every(20, "apollo-tps", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) { lastReal = 0; return; }
            long now = System.currentTimeMillis();
            long gt = mc.level.getGameTime();
            if (lastReal > 0) {
                double secs = (now - lastReal) / 1000.0;
                if (secs > 0.4) tps = (int) Math.round(Math.max(0, Math.min(20, (gt - lastGameTime) / secs)));
            }
            lastReal = now; lastGameTime = gt;
        });
    }

    @Override
    public void registerHud(HudManager hud) {
        ApolloConfig cfg = (ApolloConfig) getConfig();
        if (cfg == null) return;

        Minecraft mc = Minecraft.getInstance();

        hud.register(new HudWidget("apollo-fps", "FPS",
            () -> mc.getFps() + "",
            toPos(cfg.fps), cfg.fps.visible));

        hud.register(new HudWidget("apollo-ping", "Ping",
            () -> {
                if (mc.player == null || mc.getConnection() == null) return "-";
                var server = mc.getCurrentServer();
                return server != null ? server.ping + "ms" : "-";
            },
            toPos(cfg.ping), cfg.ping.visible));

        hud.register(new HudWidget("apollo-tps", "TPS",
            () -> tps + "",
            toPos(cfg.tps), cfg.tps.visible));

        hud.register(new HudWidget("apollo-clock", "Clock",
            () -> {
                var t = java.time.LocalTime.now();
                return String.format("%02d:%02d", t.getHour(), t.getMinute());
            },
            toPos(cfg.clock), cfg.clock.visible));

        hud.register(new HudWidget("apollo-coords", "XYZ",
            () -> {
                if (mc.player == null) return "?";
                var p = mc.player.position();
                return String.format("%.0f %.0f %.0f", p.x, p.y, p.z);
            },
            toPos(cfg.coords), cfg.coords.visible));

        hud.register(new HudWidget("apollo-health", "HP",
            () -> {
                if (com.froggylord.constellation.core.ActionBar.hasData())
                    return "§c" + compact(com.froggylord.constellation.core.ActionBar.health())
                        + "§7/" + compact(com.froggylord.constellation.core.ActionBar.maxHealth());
                if (mc.player == null) return "?";
                return String.format("%.0f", mc.player.getHealth()); // vanilla fallback off-Hypixel
            },
            toPos(cfg.health), cfg.health.visible));

        hud.register(new HudWidget("apollo-mana", "MN",
            () -> {
                if (com.froggylord.constellation.core.ActionBar.hasData())
                    return "§b" + compact(com.froggylord.constellation.core.ActionBar.mana())
                        + "§7/" + compact(com.froggylord.constellation.core.ActionBar.maxMana());
                return "?";
            },
            toPos(cfg.mana), cfg.mana.visible));

        hud.register(new HudWidget("apollo-defense", "DEF",
            () -> {
                if (com.froggylord.constellation.core.ActionBar.hasData())
                    return "§a" + compact(com.froggylord.constellation.core.ActionBar.defense());
                if (mc.player == null) return "?";
                return mc.player.getArmorValue() + "";
            },
            toPos(cfg.defense), cfg.defense.visible));

        hud.register(new HudWidget("apollo-area", "Area",
            () -> {
                var a = ConstellationClient.loc().area();
                if (a == null || a.name().equals("UNKNOWN")) return null;
                return pretty(a.name());
            },
            toPos(cfg.area), cfg.area.visible));

        hud.register(new HudWidget("apollo-facing", "Facing",
            () -> {
                if (mc.player == null) return "?";
                return facing(mc.player.getYRot());
            },
            toPos(cfg.facing), cfg.facing.visible));

        hud.register(new HudWidget("apollo-speed", "SPD",
            () -> {
                if (mc.player == null) return "?";
                var vel = mc.player.getDeltaMovement();
                double spd = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20;
                return String.format("%.0f", spd);
            },
            toPos(cfg.speed), cfg.speed.visible));

        hud.register(new HudWidget("apollo-purse", "Purse",
            () -> {
                for (String line : ConstellationClient.loc().getSidebarLines()) {
                    var m = java.util.regex.Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+)").matcher(line);
                    if (m.find()) {
                        try {
                            long n = Long.parseLong(m.group(1).replace(",", ""));
                            return "§6" + compactLong(n);
                        } catch (NumberFormatException e) { return m.group(1); }
                    }
                }
                return null;
            },
            HudPosition.of(2, 58), true));

        hud.register(new HudWidget("apollo-bits", "Bits",
            () -> {
                for (String line : ConstellationClient.loc().getSidebarLines()) {
                    var m = java.util.regex.Pattern.compile("Bits:\\s*([\\d,]+)").matcher(line);
                    if (m.find()) return "§b" + compactLong(Long.parseLong(m.group(1).replace(",", "")));
                }
                return null;
            },
            HudPosition.of(2, 66), true));

        hud.register(new HudWidget("apollo-pet", "Pet",
            () -> {
                for (String line : ConstellationClient.loc().getSidebarLines()) {
                    if (line.startsWith("Pet:") || line.contains("Pet: ")) {
                        String pet = line.substring(line.indexOf(":") + 1).trim();
                        if (pet.isEmpty()) return null;
                        return "§d" + pet;
                    }
                }
                return null;
            },
            HudPosition.of(2, 74), true));

    private static HudPosition toPos(ApolloConfig.HudEntry e) {
        return new HudPosition(e.x, e.y);
    }

    private static String compactDamage(String num) {
        try {
            long n = Long.parseLong(num.replace(",", ""));
            if (n < 10000) return num;
            if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
            if (n < 1_000_000_000) return String.format("%.1fM", n / 1_000_000.0);
            return String.format("%.1fB", n / 1_000_000_000.0);
        } catch (NumberFormatException e) { return num; }
    }

    /** 1234 -> 1.2k, 1500000 -> 1.5M */
    private static String compact(int n) {
        if (n < 1000) return Integer.toString(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        return String.format("%.1fM", n / 1_000_000.0);
    }

    private static String compactLong(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        if (n < 1_000_000_000) return String.format("%.2fM", n / 1_000_000.0);
        return String.format("%.2fB", n / 1_000_000_000.0);
    }

    private static String pretty(String enumName) {
        String[] parts = enumName.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static String facing(float yaw) {
        yaw = (yaw % 360 + 360) % 360;
        if (yaw < 22.5 || yaw >= 337.5) return "S";
        if (yaw < 67.5) return "SW";
        if (yaw < 112.5) return "W";
        if (yaw < 157.5) return "NW";
        if (yaw < 202.5) return "N";
        if (yaw < 247.5) return "NE";
        if (yaw < 292.5) return "E";
        return "SE";
    }
}
