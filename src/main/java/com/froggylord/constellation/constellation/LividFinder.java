package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * F5/M5 Livid fight — 9 clones, one real. Scan for "Livid"-named entities each frame;
 * hide the fakes and box the real one (the clone with the most HP, since the real Livid
 * has substantially more health than the decoys).
 */
public final class LividFinder {

    private LividFinder() {}

    private static String correctColor = "";

    public static void init() {
        // try to catch the real Livid's colour from the boss-spawn chat
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || !ConstellationClient.loc().inDungeons()) return;
            String s = msg.getString();
            if (s.contains("Livid") && (s.contains("real") || s.contains("correct") || s.contains("the one"))) {
                for (String c : new String[]{"§c","§a","§b","§e","§d","§9","§5","§6","§f"}) {
                    if (s.contains(c)) { correctColor = c; break; }
                }
            }
        });
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.lividFinder) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<LivingEntity> livids = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            var name = le.getCustomName();
            if (name == null) continue;
            if (!name.getString().contains("Livid")) continue;
            livids.add(le);
        }
        if (livids.isEmpty()) return;

        // pick the real one — highest health, or correct colour if known
        LivingEntity real = null;
        LivingEntity highest = livids.get(0);
        for (LivingEntity l : livids) {
            if (l.getHealth() > highest.getHealth()) highest = l;
            if (!correctColor.isEmpty()) {
                var n = l.getCustomName();
                if (n != null && n.getString().contains(correctColor)) { real = l; break; }
            }
        }
        if (real == null) real = highest;

        // hide fakes, box the real one
        for (LivingEntity l : livids) {
            if (l != real) {
                l.setInvisible(true);
            } else {
                // box in green with a name tag
                ctx.outline(l.getBoundingBox().inflate(0.2), 0xFF55FF55, false);
                ctx.label(new Vec3(l.getX(), l.getY() + l.getBbHeight() + 0.4, l.getZ()),
                    "LIVID", 0xFF55FF55, false);
                l.setInvisible(false);
            }
        }
    }
}
