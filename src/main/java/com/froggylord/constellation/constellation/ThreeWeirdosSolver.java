package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3): skyblock/dungeon/puzzle/ThreeWeirdos.java
// answer lists cross-checked against devonian (GPL-3): features/dungeons/solvers/ThreeWeirdosSolver.kt
//
// three npcs stand on fixed room-relative spots, each with a chest one block +x of them.
// exactly one of them says a line from the truthful set — that npc's chest has the reward.
// the old version of this hooked a container screen titled "weirdo" which never opens, so
// it could never fire. this one watches chat and boxes the chest in the world.
public final class ThreeWeirdosSolver {

    private ThreeWeirdosSolver() {}

    private static OrionConfig cfg;

    // only the lines that mean "my chest is the one" — anything else is a liar
    private static final Pattern SOLUTION = Pattern.compile(
        "^\\[NPC] ([A-Z][a-z]+): (?:The reward is(?: not in my chest!|n't in any of our chests\\.)"
        + "|My chest (?:doesn't have the reward\\. We are all telling the truth\\.|has the reward and I'm telling the truth!)"
        + "|At least one of them is lying, and the reward is not in [A-Z][a-z]+'s chest!"
        + "|Both of them are telling the truth\\. Also, [A-Z][a-z]+ has the reward in their chest!)$");

    // the three npc stands, room-relative. chest sits at +1 x from each
    private static final BlockPos[] NPC_SPOTS = {
        new BlockPos(13, 69, 24),
        new BlockPos(15, 69, 25),
        new BlockPos(17, 69, 24),
    };

    private static final int GREEN = 0x8033FF33;

    private static BlockPos chest = null;
    private static String roomKey = "";

    public static void init(OrionConfig config) {
        cfg = config;
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || cfg == null || !cfg.threeWeirdosSolver) return;
            if (!ConstellationClient.loc().inDungeons()) return;
            if (!inRoom()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            Matcher m = SOLUTION.matcher(ChatFormatting.stripFormatting(msg.getString()));
            if (!m.matches()) return;
            findChest(mc.level, m.group(1));
        });
    }

    public static void reset() {
        chest = null;
        roomKey = "";
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (cfg == null || !cfg.threeWeirdosSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        if (!inRoom()) { chest = null; return; }
        if (chest == null) return;
        ctx.highlight(new AABB(chest), GREEN, false);
        ctx.outline(new AABB(chest), 0xFF33FF33, false);
    }

    // gate hard on the room, otherwise npc chatter anywhere would draw a box
    private static boolean inRoom() {
        if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("three-chests")) return false;
        String key = RoomMatch.currentRoom() + ':' + RoomMatch.anchorX() + ':' + RoomMatch.anchorZ() + ':' + RoomMatch.currentDir();
        if (!key.equals(roomKey)) {
            roomKey = key;
            chest = null;
        }
        return true;
    }

    // whichever stand is actually holding that name, its chest is the answer
    private static void findChest(ClientLevel level, String name) {
        for (BlockPos spot : NPC_SPOTS) {
            BlockPos npc = world(spot);
            List<ArmorStand> found = level.getEntitiesOfClass(
                ArmorStand.class,
                AABB.encapsulatingFullBlocks(npc, npc),
                e -> e.getName().getString().equals(name));
            if (found.isEmpty()) continue;
            chest = world(spot.offset(1, 0, 0));
            return;
        }
    }

    private static BlockPos world(BlockPos rel) {
        long[] w = RoomTransform.relativeToActual(
            RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(),
            rel.getX(), rel.getY(), rel.getZ());
        return new BlockPos((int) w[0], (int) w[1], (int) w[2]);
    }
}
