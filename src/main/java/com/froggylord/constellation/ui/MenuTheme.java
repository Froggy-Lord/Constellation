package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.VisualConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

public final class MenuTheme {
    private static boolean renderingTitle;
    private MenuTheme() {}

    public static VisualConfig config() {
        try {
            return ConstellationClient.cfg().visual;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean titleBackdrop(Screen screen) {
        VisualConfig config = config();
        return config != null && config.enabled && config.titleBackdrop
            && Minecraft.getInstance().level == null && screen instanceof TitleScreen;
    }

    public static boolean titleButtons() {
        VisualConfig config = config();
        return config != null && config.enabled && config.titleButtons
            && Minecraft.getInstance().level == null && renderingTitle;
    }

    public static void setRenderingTitle(boolean rendering) { renderingTitle = rendering; }
}
