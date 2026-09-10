package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

// ported from NoFrills (GPL-3.0): src/main/java/nofrills/features/dungeons/LividSolver.java
// the real Livid's wool/glass colour is mirrored on a block in the arena wall; the
// entity whose name matches that colour is the one you must hit, the rest are decoys.
// wool position / y-range cross-checked against Odin and Skyblocker.
public final class LividFinder {

    private LividFinder() {}

    // colour token (from the block's registry id) -> in-game Livid entity name.
    // the arena block is either coloured wool or the matching stained glass.
    private static final Map<String, String> COLOUR_TO_LIVID = Map.of(
        "red",     "Hockey Livid",
        "yellow",  "Arcade Livid",
        "lime",    "Smile Livid",
        "green",   "Frog Livid",
        "blue",    "Scream Livid",
        "magenta", "Crossed Livid",
        "purple",  "Purple Livid",
        "gray",    "Doctor Livid",
        "white",   "Vendetta Livid"
    );

    // refs disagree on the exact block (Skyblocker 5,110,42 / Odin 5,108,43),
    // so scan the small x=5 column that covers both of them.
    private static final int SCAN_X = 5;
    private static final int SCAN_Y_MIN = 107;
    private static final int SCAN_Y_MAX = 110;
    private static final int[] SCAN_Z = {42, 43};

    private static String correctName = "";
    private static String correctColour = "";
    private static String announcedColour = "";
    private static long lastWoolCheck = 0;

    public static void init() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.lividFinder) {
            reset();
            return;
        }
        if (!ConstellationClient.loc().inDungeons()) {
            reset();
            return;
        }
        // Livid only exists in the F5/M5 boss room
        if (!ConstellationClient.dungeon().inBoss() || !isFloorFive()) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // re-read the arena colour block every 2s (it is static once you arrive)
        long now = System.currentTimeMillis();
        if (now - lastWoolCheck > 2000) {
            lastWoolCheck = now;
            correctName = scanArenaColour(mc);
            if (!correctColour.isEmpty() && !correctColour.equals(announcedColour)) {
                announcedColour = correctColour;
                PartyMessages.send("livid-" + correctColour, Map.of(
                    "color", correctColour.substring(0, 1).toUpperCase() + correctColour.substring(1),
                    "livid", correctName));
            }
        }
        if (correctName.isEmpty()) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player)) continue;
            String nm = e.getName().getString();
            if (!COLOUR_TO_LIVID.containsValue(nm)) continue;
            if (nm.equals(correctName)) {
                ctx.outline(e.getBoundingBox().inflate(0.3), 0xFF55FF55, true);
                ctx.label(new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.5, e.getZ()),
                    nm, 0xFF55FF55, true);
            }
        }
    }

    private static void reset() {
        correctName = "";
        correctColour = "";
        announcedColour = "";
        lastWoolCheck = 0;
    }

    private static String scanArenaColour(Minecraft mc) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z : SCAN_Z) {
            for (int y = SCAN_Y_MIN; y <= SCAN_Y_MAX; y++) {
                pos.set(SCAN_X, y, z);
                BlockState state = mc.level.getBlockState(pos);
                String colour = colourToken(state);
                String livid = COLOUR_TO_LIVID.get(colour);
                if (livid != null) { correctColour = colour; return livid; }
            }
        }
        correctColour = "";
        return "";
    }

    // "block.minecraft.red_wool" -> "red"; "light_gray_wool" -> "light_gray" (not a Livid, so no match)
    private static String colourToken(BlockState state) {
        String id = state.getBlock().getDescriptionId();
        int dot = id.lastIndexOf('.');
        String name = dot >= 0 ? id.substring(dot + 1) : id;
        if (name.endsWith("_wool")) return name.substring(0, name.length() - "_wool".length());
        if (name.endsWith("_stained_glass")) return name.substring(0, name.length() - "_stained_glass".length());
        return "";
    }

    private static boolean isFloorFive() {
        String floor = ConstellationClient.dungeon().floor();
        return !floor.isEmpty() && floor.charAt(floor.length() - 1) == '5';
    }
}
