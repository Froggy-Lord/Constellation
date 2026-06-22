package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

/**
 * M7/F7 dragon fight — highlight each dragon with a name tag so you can pick the right one
 * at a glance. The dragon with the lowest health gets a green box; others get yellow.
 * (cmp. Skyblocker M7Dragons / Odin DragonPriority)
 */
public final class M7Dragons {

    private M7Dragons() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.m7DragonMarkers) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        EnderDragon lowest = null;
        float lowestHp = Float.MAX_VALUE;
        java.util.List<EnderDragon> dragons = new java.util.ArrayList<>();

        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof EnderDragon d) {
                dragons.add(d);
                if (d.getHealth() < lowestHp) { lowestHp = d.getHealth(); lowest = d; }
            }
        }
        if (dragons.isEmpty()) return;

        for (EnderDragon d : dragons) {
            boolean priority = d == lowest;
            Vec3 pos = d.position().add(0, d.getBbHeight() + 1, 0);
            int col = priority ? 0xFF55FF55 : 0xFFFFFF55;
            ctx.outline(d.getBoundingBox().inflate(0.3), col, false);
            ctx.label(pos, priority ? "✦ PRIORITY" : "Dragon", col, false);
        }
    }
}
