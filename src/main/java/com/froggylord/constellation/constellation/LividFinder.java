package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class LividFinder {

    private LividFinder() {}

    private static final BlockPos WOOL_POS = new BlockPos(5, 110, 42);

    static {
        WOOL_TO_LIVID = Map.of(
            Blocks.WOOL.red(),      "Hockey Livid",
            Blocks.WOOL.yellow(),   "Arcade Livid",
            Blocks.WOOL.lime(),     "Smile Livid",
            Blocks.WOOL.green(),    "Frog Livid",
            Blocks.WOOL.blue(),     "Scream Livid",
            Blocks.WOOL.magenta(),  "Crossed Livid",
            Blocks.WOOL.purple(),   "Purple Livid",
            Blocks.WOOL.gray(),     "Doctor Livid",
            Blocks.WOOL.white(),    "Vendetta Livid"
        );
    }
    private static final Map<Block, String> WOOL_TO_LIVID;

    private static String correctName = "";
    private static int correctEntityId = -1;
    private static long lastWoolCheck = 0;

    public static void init() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.lividFinder) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // read the wool block every 2s t...
        long now = System.currentTimeMillis();
        if (now - lastWoolCheck > 2000) {
            lastWoolCheck = now;
            Block wool = mc.level.getBlockState(WOOL_POS).getBlock();
            correctName = WOOL_TO_LIVID.getOrDefault(wool, "");
        }
        if (correctName.isEmpty()) return;

        
        boolean foundAny = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player)) continue;
            var name = e.getName();
            String nm = name.getString();
            if (!WOOL_TO_LIVID.containsValue(nm)) continue;
            foundAny = true;
            if (nm.equals(correctName)) {
                
                e.setInvisible(false);
                correctEntityId = e.getId();
                ctx.outline(e.getBoundingBox().inflate(0.3), 0xFF55FF55, false);
                ctx.label(new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.5, e.getZ()),
                    "✦ " + nm, 0xFF55FF55, false);
            } else {
                
                if (e.getId() != correctEntityId) e.setInvisible(true);
            }
        }
        
        if (!foundAny) correctEntityId = -1;
    }
}
