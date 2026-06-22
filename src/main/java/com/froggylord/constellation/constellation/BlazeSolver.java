package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The F3/M3 blaze puzzle ("higher or lower"). Each blaze floats under an armour-stand nameplate
 * that holds its health. We pair every blaze to its tag, sort by health and mark the smallest and
 * largest — the two rooms want opposite orders, so showing both lets you pick the right end.
 */
public final class BlazeSolver {

    private static final int LOW = 0xFF55FF55;  // green — smallest health
    private static final int HIGH = 0xFFFF5555; // red — largest health

    private BlazeSolver() {}

    private record Tagged(Blaze blaze, long hp) {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.blazeSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<Blaze> blazes = new ArrayList<>();
        List<ArmorStand> tags = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Blaze b) blazes.add(b);
            else if (e instanceof ArmorStand a && a.getCustomName() != null
                && a.getCustomName().getString().indexOf('❤') >= 0) tags.add(a);
        }
        if (blazes.size() < 2) return;

        List<Tagged> list = new ArrayList<>();
        for (Blaze b : blazes) {
            ArmorStand near = null;
            double best = 4.0;
            Vec3 bp = b.position();
            for (ArmorStand a : tags) {
                double d = a.position().distanceToSqr(bp.x, bp.y + 1.0, bp.z);
                if (d < best) { best = d; near = a; }
            }
            if (near == null) continue;
            long hp = parseHp(near.getCustomName().getString());
            if (hp > 0) list.add(new Tagged(b, hp));
        }
        if (list.size() < 2) return;

        Tagged lo = list.get(0), hi = list.get(0);
        for (Tagged t : list) {
            if (t.hp < lo.hp) lo = t;
            if (t.hp > hi.hp) hi = t;
        }
        ctx.outline(lo.blaze.getBoundingBox().inflate(0.1), LOW, true);
        ctx.label(lo.blaze.position().add(0, 1.2, 0), "LOW", LOW, true);
        ctx.outline(hi.blaze.getBoundingBox().inflate(0.1), HIGH, true);
        ctx.label(hi.blaze.position().add(0, 1.2, 0), "HIGH", HIGH, true);
    }

    private static long parseHp(String name) {
        // the health sits right before the ❤; a "[Lv200]" prefix has its own digits, so only take
        // the run of digits immediately left of the heart
        int heart = name.indexOf('❤');
        if (heart < 0) return 0;
        int i = heart - 1;
        StringBuilder rev = new StringBuilder();
        while (i >= 0) {
            char c = name.charAt(i--);
            if (c >= '0' && c <= '9') rev.append(c);
            else if (c == ',' || c == '.' || c == ' ') continue;
            else break;
        }
        if (rev.length() == 0) return 0;
        try { return Long.parseLong(rev.reverse().toString()); }
        catch (NumberFormatException e) { return 0; }
    }
}
