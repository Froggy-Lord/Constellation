package com.froggylord.constellation.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ConstellationIcons {
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("constellation", "textures/gui/constellation_icons.png");
    private static final Identifier ACTION_TEXTURE =
        Identifier.fromNamespaceAndPath("constellation", "textures/gui/interface_icons.png");
    private static final List<String> IDS = List.of(
        "apollo", "cassiopeia", "orion", "phoenix", "aquila",
        "lyra", "cygnus", "perseus", "hercules", "draco",
        "hydra", "andromeda", "pegasus", "auriga", "artemis"
    );
    private static final int TILE = 32;
    private static final int WIDTH = IDS.size() * TILE;
    private static final List<String> ACTION_IDS = List.of(
        "search", "settings", "hud", "filter", "back", "close",
        "info", "next", "reset", "save", "delete", "sort"
    );
    private static final int ACTION_TILE = 16;
    private static final int ACTION_WIDTH = ACTION_IDS.size() * ACTION_TILE;

    private ConstellationIcons() {}

    public static void draw(GuiGraphicsExtractor graphics, String id, int x, int y, int size) {
        int index = IDS.indexOf(id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        if (index < 0 || size <= 0) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, index * TILE, 0,
            size, size, TILE, TILE, WIDTH, TILE);
    }

    public static void drawAction(GuiGraphicsExtractor graphics, String id, int x, int y, int size) {
        int index = ACTION_IDS.indexOf(id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        if (index < 0 || size <= 0) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ACTION_TEXTURE, x, y, index * ACTION_TILE, 0,
            size, size, ACTION_TILE, ACTION_TILE, ACTION_WIDTH, ACTION_TILE);
    }
}
