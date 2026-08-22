package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// ported from NoFrills (GPL-3.0): features/dungeons/PlatformHighlight.java
public final class HealerPlatformHighlight {
    private static final AABB PLATFORM = new AABB(53, 63, 113, 56, 64, 116);

    private HealerPlatformHighlight() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        var cfg = ConstellationClient.cfg().orion;
        var dungeon = ConstellationClient.dungeon();
        if (cfg == null || !cfg.healerPlatformHighlight) return;
        if (!ConstellationClient.loc().inDungeons() || !dungeon.inBoss()) return;
        if (!dungeon.floor().endsWith("7") || !dungeon.playerClass().equalsIgnoreCase("Healer")) return;

        ctx.outline(PLATFORM, 0xFF55FF55, false);
        ctx.label(new Vec3(54.5, 64.5, 114.5), "Healer platform", 0xFF55FF55, false);
    }
}
