package com.froggylord.constellation.render;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class HudRenderer {

    public static void init() {}

    public static void onRenderOverlay(GuiGraphicsExtractor g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        ConstellationClient.hudManager().render(g, mc.font, w, h);
    }
}
