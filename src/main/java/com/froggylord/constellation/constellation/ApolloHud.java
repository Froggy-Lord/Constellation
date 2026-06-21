package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ApolloConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import net.minecraft.client.Minecraft;

public class ApolloHud extends BaseConstellation {

    @Override public String id() { return "apollo"; }
    @Override public String displayName() { return "Apollo"; }
    @Override public String description() { return "Core HUD — info overlays, scoreboard, tab list"; }

    @Override
    public void init(InitContext ctx) {
        ConstellationClient.tick().every(5, "apollo-hud", () -> {});
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
                if (mc.player == null) return "?";
                return String.format("%.0f", mc.player.getHealth());
            },
            toPos(cfg.health), cfg.health.visible));

        hud.register(new HudWidget("apollo-mana", "MN",
            () -> "?", // SB action bar parsing — Phase 2
            toPos(cfg.mana), cfg.mana.visible));

        hud.register(new HudWidget("apollo-defense", "DEF",
            () -> {
                if (mc.player == null) return "?";
                return mc.player.getArmorValue() + "";
            },
            toPos(cfg.defense), cfg.defense.visible));

        hud.register(new HudWidget("apollo-speed", "SPD",
            () -> {
                if (mc.player == null) return "?";
                var vel = mc.player.getDeltaMovement();
                double spd = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20;
                return String.format("%.0f", spd);
            },
            toPos(cfg.speed), cfg.speed.visible));
    }

    private static HudPosition toPos(ApolloConfig.HudEntry e) {
        return new HudPosition(e.x, e.y);
    }
}
