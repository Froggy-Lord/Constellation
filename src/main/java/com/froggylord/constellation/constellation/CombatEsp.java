package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Combat highlights for dungeons. Everything here is depth-tested (boxes respect walls) —
 * these mark things that move, so x-ray would be against Hypixel's rules, unlike the static
 * secret waypoints.
 *
 * - starred mobs: the objective mobs carry a ✯ in their nameplate. they can be fake players,
 *   so we match on the name rather than the entity class.
 * - secret bats: dungeon secret bats, boxed so they're easy to spot and pop.
 */
public final class CombatEsp {

    private static final double RANGE = 60.0;
    private static final int STAR_COLOUR = 0xFFFF5555; // red
    private static final int BAT_COLOUR = 0xFF55FFFF;  // aqua

    private CombatEsp() {}

    private static final int TEAM_COLOUR = 0xFF55FF55; // green

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || (!cfg.starredMobs && !cfg.secretBats && !cfg.teammateBoxes)) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 me = mc.player.position();
        double r2 = RANGE * RANGE;

        // teammates — box the other (real) players in the run
        if (cfg.teammateBoxes) {
            for (var p : mc.level.players()) {
                if (p == mc.player) continue;
                if (p.isSpectator() || p.getName().getString().isBlank()) continue;
                ctx.outline(p.getBoundingBox(), TEAM_COLOUR, false);
                ctx.label(p.position().add(0, p.getBbHeight() + 0.3, 0), p.getName().getString(), TEAM_COLOUR, false);
            }
        }

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            if (e.distanceToSqr(me) > r2) continue;

            if (cfg.secretBats && e instanceof Bat) {
                // ignore bats that are part of wither/blood door animations — real secret
                // bats are never right next to a door block
                if (!nearDoorBlock(e)) ctx.outline(e.getBoundingBox().inflate(0.05), BAT_COLOUR, false);
                continue;
            }
            if (cfg.starredMobs && isStarred(e)) {
                ctx.outline(e.getBoundingBox().inflate(0.05), STAR_COLOUR, false);
            }
        }
    }

    private static boolean isStarred(Entity e) {
        // the star can live in the custom name, the display name, or the plain entity name
        for (var name : new Component[]{e.getCustomName(), e.getDisplayName(), e.getName()}) {
            if (name == null) continue;
            String s = name.getString();
            if (s.isEmpty()) continue;
            if (s.indexOf('✯') >= 0 || s.indexOf('✦') >= 0 || s.contains("✯") || s.contains("✦"))
                return true;
        }
        return false;
    }

    private static boolean nearDoorBlock(Entity bat) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        // scan a 2-block radius around the bat for door-building blocks
        var center = bat.blockPosition();
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++) {
                    var bs = mc.level.getBlockState(center.offset(dx, dy, dz));
                    String id = bs.getBlock().getDescriptionId();
                    // coal blocks, red clay, black clay are the door cues
                    if (id.contains("coal_block") || id.contains("stained_clay") || id.contains("terracotta"))
                        return true;
                }
        return false;
    }

    /** Box for an arbitrary entity (used by teammate boxes etc.). */
    static void boxEntity(WorldRenderer.Ctx ctx, Entity e, int colour, boolean throughWalls) {
        AABB b = e.getBoundingBox();
        ctx.outline(b, colour, throughWalls);
    }
}
