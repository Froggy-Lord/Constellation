package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import java.util.Map;

// ported from NoFrills (GPL-3.0): features/dungeons/RelicHighlight.java
public final class M7RelicHighlight {
    private record Relic(int x, int y, int z, int colour) {}

    private static final Map<String, Relic> RELICS = Map.of(
        "Corrupted Green Relic", new Relic(49, 7, 44, 0xFF00FF00),
        "Corrupted Red Relic", new Relic(51, 7, 42, 0xFFFF0000),
        "Corrupted Purple Relic", new Relic(54, 7, 41, 0xFFAA00AA),
        "Corrupted Orange Relic", new Relic(57, 7, 42, 0xFFFFAA00),
        "Corrupted Blue Relic", new Relic(59, 7, 44, 0xFF55FFFF)
    );

    private M7RelicHighlight() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.m7RelicHighlight) return;
        if (!ConstellationClient.loc().inDungeons() || !ConstellationClient.dungeon().inBoss()) return;
        if (!ConstellationClient.dungeon().floor().equalsIgnoreCase("M7")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stack = mc.player.getInventory().getItem(8);
        if (stack.isEmpty()) return;
        Relic relic = RELICS.get(stack.getHoverName().getString());
        if (relic == null) return;

        AABB box = new AABB(relic.x, relic.y, relic.z, relic.x + 1, relic.y + 1, relic.z + 1);
        ctx.highlight(box, relic.colour, true);
        ctx.beam(relic.x + 0.5, relic.y + 1, relic.z + 0.5, relic.colour, 12, true);
        ctx.label(box.getCenter().add(0, 1.2, 0), "Place " + stack.getHoverName().getString(), relic.colour, true);
    }
}
