package com.froggylord.constellation.hud;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.DungeonMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Wraps the dungeon map as a HUD element so it lives in the one editor + registry.
 * Position is mirrored into OrionConfig.mapX/mapY (stored as screen %).
 */
public class MapHudElement implements HudElement {

    @Override public String id() { return "orion-map"; }
    @Override public String editorLabel() { return "Dungeon Map"; }

    @Override
    public HudPosition position() {
        OrionConfig c = ConstellationClient.cfg().orion;
        return new HudPosition(c.mapX, c.mapY);
    }

    @Override
    public void setPosition(HudPosition pos) {
        OrionConfig c = ConstellationClient.cfg().orion;
        c.mapX = pos.x();
        c.mapY = pos.y();
    }

    @Override
    public boolean isEnabled() {
        OrionConfig c = ConstellationClient.cfg().orion;
        return c != null && c.dungeonMap;
    }

    @Override
    public void setEnabled(boolean e) {
        OrionConfig c = ConstellationClient.cfg().orion;
        if (c != null) c.dungeonMap = e;
    }

    @Override public boolean visibleNow() { return DungeonMap.visibleNow(); }
    @Override public int width() { return DungeonMap.screenSize(); }
    @Override public int height() { return DungeonMap.screenSize(); }

    @Override
    public void render(GuiGraphicsExtractor g, int px, int py) {
        DungeonMap.renderAt(g, px, py);
    }
}
