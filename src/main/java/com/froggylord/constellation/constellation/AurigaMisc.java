package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

/**
 * Auriga — experiments + enchanting + misc. Starter: experiments table helper indicator. Full
 * experiment solvers (Chronomatron / Ultrasequencer / Superpairs) come later as GUI-slot
 * overlays since the 26.2 screen API moved to an extract-render-state model.
 */
public class AurigaMisc extends BaseConstellation {

    @Override public String id() { return "auriga"; }
    @Override public String displayName() { return "Auriga"; }
    @Override public String description() { return "Experiments — helpers, enchanting, minions"; }

    private AurigaConfig cfg;

    @Override
    public void init(InitContext ctx) { cfg = (AurigaConfig) getConfig(); }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (AurigaConfig) getConfig();
        if (cfg == null) return;

        if (cfg.experimentHelper) {
            hud.register(new HudWidget("auriga-exp", "Experiments",
                () -> ConstellationClient.loc().onHypixel() ? "§e🧪" : null,
                HudPosition.of(50, 94), cfg.experimentHelper));
        }
    }
}
