package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AnvilMenu;

public final class AurigaHelpers {

    private AurigaHelpers() {}

    private static AurigaConfig cfg;

    public static void init(AurigaConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            if (!(cs.getMenu() instanceof AnvilMenu am)) return;
            if (!ConstellationClient.loc().onHypixel()) return;
            ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                if (cfg == null || !cfg.anvilHelper) return;
                try { render(cs, am, g); } catch (Exception ignored) {}
            });
        });
    }

    private static void render(AbstractContainerScreen<?> cs, AnvilMenu am, GuiGraphicsExtractor g) {
        int cost = am.getCost();
        if (cost <= 0) return;
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        var font = Minecraft.getInstance().font;
        String text = cost + " levels";
        int col = cost <= 30 ? 0xFF55FF55 : cost <= 60 ? 0xFFFFAA00 : 0xFFFF5555;
        
        g.text(font, text, left - font.width(text) - 4, top + 30, col, true);
    }
}
