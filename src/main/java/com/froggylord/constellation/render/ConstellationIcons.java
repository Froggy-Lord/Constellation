package com.froggylord.constellation.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ConstellationIcons {
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("constellation", "textures/gui/constellation_icons.png");
    private static final List<String> IDS = List.of(
        "apollo", "cassiopeia", "orion", "phoenix", "aquila",
        "lyra", "cygnus", "perseus", "hercules", "draco",
        "hydra", "andromeda", "pegasus", "auriga", "artemis"
    );
    private static final int TILE = 32;
    private static final int WIDTH = IDS.size() * TILE;

    private ConstellationIcons() {}

    public static void draw(GuiGraphicsExtractor graphics, String id, int x, int y, int size) {
        int index = IDS.indexOf(id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        if (index < 0 || size <= 0) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, index * TILE, 0,
            size, size, TILE, TILE, WIDTH, TILE);
    }
}
