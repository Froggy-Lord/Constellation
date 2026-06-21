package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import net.minecraft.client.Minecraft;

public class PhoenixQol extends BaseConstellation {

    @Override public String id() { return "phoenix"; }
    @Override public String displayName() { return "Phoenix"; }
    @Override public String description() { return "Quality of Life — fullbright, hide annoyances, smooth gameplay"; }

    @Override
    public void init(InitContext ctx) {
        PhoenixConfig cfg = (PhoenixConfig) getConfig();
        if (cfg == null) return;

        if (cfg.autoSprint) {
            ConstellationClient.tick().every(1, "phoenix-sprint", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.options.keyUp.isDown()) {
                    mc.options.keySprint.setDown(true);
                }
            });
        }
        // hide fire overlay — just keep extinguishing the fire ticks each frame
        if (cfg.hideFireOverlay) {
            ConstellationClient.tick().every(1, "phoenix-fire", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.setRemainingFireTicks(0);
            });
        }
        if (cfg.disableVignette) {
            // vignette is a client option — set once per init and leave it off
            Minecraft mc = Minecraft.getInstance();
            mc.options.vignette().set(false);
        }
        if (cfg.disableFog) {
            // fog is toggled via the client option
            Minecraft mc = Minecraft.getInstance();
            mc.options.fovEffectScale().set(0.0);
        }
    }
}
