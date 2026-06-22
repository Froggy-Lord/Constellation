package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Boxes the dungeon utility items that get dropped on the floor mid-run — easy to walk past in a
 * fight, annoying to lose. Through-walls off (depth-tested) so it stays within the rules for
 * things that move.
 */
public final class DropEsp {

    private DropEsp() {}

    private static final int COLOUR = 0xFFFFFF55; // yellow
    private static final String[] WATCH = {
        "Spirit Leap", "Decoy", "Training Weights", "Architect's First Draft",
        "Trap", "Defuse Kit", "Inflatable Jerry", "Spirit Bow", "Premium Flesh"
    };

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.dropEsp || !ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity ie)) continue;
            String name = ie.getItem().getHoverName().getString();
            for (String w : WATCH) {
                if (name.contains(w)) {
                    ctx.outline(ie.getBoundingBox().inflate(0.15), COLOUR, false);
                    ctx.label(new Vec3(ie.getX(), ie.getY() + 0.5, ie.getZ()), w, COLOUR, false);
                    break;
                }
            }
        }
    }
}
