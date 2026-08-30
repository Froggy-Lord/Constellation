package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

// ported from Skyblocker (LGPL-3.0): skyblock/dungeon/device/LightsOn.java
public final class LightsOnDevice {
    private static final BlockPos[] LEVERS = {
        new BlockPos(62, 136, 142),
        new BlockPos(58, 136, 142),
        new BlockPos(60, 135, 142),
        new BlockPos(60, 134, 142),
        new BlockPos(62, 133, 142),
        new BlockPos(58, 133, 142)
    };

    private LightsOnDevice() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        var cfg = ConstellationClient.cfg().orion;
        var dungeon = ConstellationClient.dungeon();
        if (cfg == null || !cfg.lightsOnSolver) return;
        if (!ConstellationClient.loc().inDungeons() || !dungeon.inBoss() || !dungeon.floor().endsWith("7")) return;
        if (!dungeon.bossPhase().equals("Maxor")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (BlockPos pos : LEVERS) {
            var state = mc.level.getBlockState(pos);
            if (state.is(Blocks.LEVER) && state.hasProperty(BlockStateProperties.POWERED)
                && !state.getValue(BlockStateProperties.POWERED)) {
                ctx.highlight(new AABB(pos), 0xC0FF5555, false);
            }
        }
    }
}
