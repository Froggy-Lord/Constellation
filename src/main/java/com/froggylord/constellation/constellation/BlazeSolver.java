package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Odin (BSD-3): src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/BlazeSolver.kt
// Blaze puzzle: kill the blazes in max-HP order. The direction depends on which of the two
// blaze rooms you are in — the room NAME resolves it (Odin's "Higher Blaze" / "Lower Blaze"):
//   Blaze-Room-1-High  == "Higher Blaze" -> lowest max-HP first  (ascending)
//   Blaze-Room-1-Low   == "Lower Blaze"  -> highest max-HP first (descending)
// The HP comes from the floating nametag armour stand, not the blaze entity.
public final class BlazeSolver {

    private static final int NEXT = 0xFF55FF55;   // green  — hit this one next
    private static final int SECOND = 0xFFFFFF55; // yellow — the one after
    private static final int REST = 0xFFFF5555;   // red    — remaining, in order

    // [Lv15] ♨ Blaze 1,000/2,000❤  — group 1 = max HP
    private static final Pattern BLAZE = Pattern.compile("^\\[Lv15] ♨ Blaze [\\d,]+/([\\d,]+)❤$");

    private BlazeSolver() {}

    private static boolean sawBlazes;
    private static boolean announced;
    private static int lastBlazeCount;

    private record Tagged(ArmorStand stand, long hp) {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.blazeSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;

        // gate on the confirmed blaze room — the name tells us which way to order the kills
        if (!RoomMatch.isMatched()) return;
        String room = RoomMatch.currentRoom().toLowerCase(Locale.ROOT);
        boolean high = room.contains("blaze") && room.contains("high");
        boolean low = room.contains("blaze") && room.contains("low");
        if (!high && !low) { sawBlazes = false; announced = false; lastBlazeCount = 0; return; }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<Tagged> list = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand a) || a.getCustomName() == null) continue;
            Matcher m = BLAZE.matcher(a.getCustomName().getString());
            if (!m.matches()) continue;
            long hp;
            try { hp = Long.parseLong(m.group(1).replace(",", "")); }
            catch (NumberFormatException ex) { continue; }
            list.add(new Tagged(a, hp));
        }
        if (list.isEmpty()) {
            if (sawBlazes && lastBlazeCount == 1 && !announced) {
                announced = true;
                PartyMessages.send("blaze-done");
            }
            return;
        }
        sawBlazes = true;
        lastBlazeCount = list.size();

        // Lower Blaze -> descending (highest first); Higher Blaze -> ascending (lowest first)
        if (low) list.sort((x, y) -> Long.compare(y.hp, x.hp));
        else list.sort((x, y) -> Long.compare(x.hp, y.hp));

        for (int i = 0; i < list.size(); i++) {
            int col = i == 0 ? NEXT : i == 1 ? SECOND : REST;
            // Odin's box: inflate around the nametag and drop it 1 block onto the blaze body
            AABB box = list.get(i).stand.getBoundingBox().inflate(0.5, 1.0, 0.5).move(0, -1.0, 0);
            ctx.outline(box, col, true);
        }
        // call out the next blaze to hit
        ctx.label(list.get(0).stand.position().add(0, 0.3, 0), "HIT NEXT", NEXT, true);
    }
}
