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
    }
}
