package com.froggylord.constellation.render;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.mixin.MapDataAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Renders Hypixel's own dungeon map (the filled-map item it puts in your hotbar) as a
 * movable HUD overlay. Reads the map item's colour bytes + decoration markers directly;
 * no room detection needed for the picture itself. Cleared/secret room ticks are redrawn
 * at full resolution on top so they stay crisp. Player markers + current room name overlaid.
 */
public class DungeonMap {

    private static final int MAP = 128;   // the map item is 128x128 px
    private static final int STEP = 2;    // sample every other pixel
    private static final int WHITE = 34;  // packed colour id: cleared-room tick
    private static final int GREEN = 30;  // packed colour id: all-secrets tick

    private static int cellPx() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        return Math.max(1, Math.min(5, cfg == null ? 2 : cfg.mapScale));
    }

    public static int screenSize() { return (MAP / STEP) * cellPx(); }

    /** Is the map showing right now (in a dungeon, enabled, map item present)? */
    public static boolean visibleNow() {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.dungeonMap) return false;
        if (!ConstellationClient.loc().inDungeons()) return false;
        MapItemSavedData data = findMap();
        return data != null && data.colors.length >= MAP * MAP;
    }

    /** Render the map with its top-left at (x, y) screen pixels. */
    public static void renderAt(GuiGraphicsExtractor g, int x, int y) {
        try {
            MapItemSavedData data = findMap();
            if (data == null || data.colors.length < MAP * MAP) return;
            drawMap(g, x, y, data.colors);
            drawTicks(g, x, y, data.colors);
            drawMarkers(g, x, y, data);
            drawRoomName(g, x, y);
        } catch (Exception ignored) {
            // bad map data shouldn't take the frame down
        }
    }

    /** The base map, sampled every STEP pixels. */
    public static void drawMap(GuiGraphicsExtractor g, int ox, int oy, byte[] colors) {
        int cell = cellPx();
        g.fill(ox - 2, oy - 2, ox + screenSize() + 2, oy + screenSize() + 2, 0xA00A0A14); // backdrop
        for (int my = 0; my < MAP; my += STEP) {
            for (int mx = 0; mx < MAP; mx += STEP) {
                int packed = colors[mx + my * MAP] & 0xFF;
                if (packed < 4) continue; // 0-3 = transparent
                int argb = MapColor.getColorFromPackedId(packed);
                int sx = ox + (mx / STEP) * cell;
                int sy = oy + (my / STEP) * cell;
                g.fill(sx, sy, sx + cell, sy + cell, argb);
            }
        }
    }

    /** Redraw cleared/secret ticks at full res so the downsample doesn't eat them. */
    private static void drawTicks(GuiGraphicsExtractor g, int ox, int oy, byte[] colors) {
        int sz = Math.max(1, screenSize() / MAP);
        for (int my = 0; my < MAP; my++) {
            for (int mx = 0; mx < MAP; mx++) {
                int packed = colors[mx + my * MAP] & 0xFF;
                if (packed != WHITE && packed != GREEN) continue;
                int sx = ox + (mx * screenSize()) / MAP;
                int sy = oy + (my * screenSize()) / MAP;
                g.fill(sx, sy, sx + sz, sy + sz, MapColor.getColorFromPackedId(packed));
            }
        }
    }

    private static final net.minecraft.resources.Identifier ARROW_TEX =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "textures/map/decorations/player.png");
    private static final com.mojang.blaze3d.pipeline.RenderPipeline VANILLA_TEX_PIPE =
        net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;

    /** Player markers: the vanilla arrow for yourself, teammate heads for everyone else. */
    private static void drawMarkers(GuiGraphicsExtractor g, int ox, int oy, MapItemSavedData data) {
        var decos = ((MapDataAccessor) (Object) data).constellation$decorations();
        if (decos == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // self = the decoration mapped nearest to the player's real position (robust even when
        // several markers share a type). fall back to FRAME/PLAYER type if uncalibrated.
        Object self = pickSelf(decos.values(), mc);

        for (var deco : decos.values()) {
            int mpx = (deco.x() / 2) + 64, mpz = (deco.y() / 2) + 64;
            int sx = ox + (clamp(mpx) * screenSize()) / MAP;
            int sy = oy + (clamp(mpz) * screenSize()) / MAP;

            if (deco == self) {
                drawArrow(g, sx, sy, mc.player.getYRot());
            } else {
                drawHead(g, sx, sy, skinForDeco(mc, mpx, mpz));
            }
        }
    }

    private static Object pickSelf(Iterable<? extends net.minecraft.world.level.saveddata.maps.MapDecoration> decos, Minecraft mc) {
        Object self = null;
        double best = Double.MAX_VALUE;
        for (var deco : decos) {
            int[] w = com.froggylord.constellation.data.MapSegments.worldXZFromMapPixel((deco.x() / 2) + 64, (deco.y() / 2) + 64);
            if (w == null) continue;
            double dx = w[0] - mc.player.getX(), dz = w[1] - mc.player.getZ();
            double d = dx * dx + dz * dz;
            if (d < best) { best = d; self = deco; }
        }
        if (self != null) return self;
        // uncalibrated fallback: first FRAME, else first decoration
        for (var deco : decos) {
            var t = deco.type();
            if (t.equals(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.FRAME)
             || t.equals(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.PLAYER)) return deco;
        }
        return null;
    }

    /** Skin of the loaded player nearest this marker's world position; default skin otherwise. */
    private static net.minecraft.resources.Identifier skinForDeco(Minecraft mc, int mpx, int mpz) {
        int[] w = com.froggylord.constellation.data.MapSegments.worldXZFromMapPixel(mpx, mpz);
        if (w != null && mc.level != null) {
            net.minecraft.client.player.AbstractClientPlayer best = null;
            double bestD = 22 * 22;
            for (var p : mc.level.players()) {
                if (p == mc.player) continue;
                double dx = p.getX() - w[0], dz = p.getZ() - w[1];
                double d = dx * dx + dz * dz;
                if (d < bestD) { bestD = d; best = p; }
            }
            if (best != null) return best.getSkin().body().texturePath();
        }
        return net.minecraft.client.resources.DefaultPlayerSkin.get(java.util.UUID.nameUUIDFromBytes(
            new byte[]{ (byte) mpx, (byte) mpz })).body().texturePath();
    }

    /** A player head (face + hat overlay) drawn upright at a map marker. */
    private static void drawHead(GuiGraphicsExtractor g, int cx, int cy, net.minecraft.resources.Identifier skin) {
        int s = Math.max(7, screenSize() / 13);
        int h = s / 2;
        g.blit(VANILLA_TEX_PIPE, skin, cx - h, cy - h, 8f, 8f, s, s, 8, 8, 64, 64);   // face
        g.blit(VANILLA_TEX_PIPE, skin, cx - h, cy - h, 40f, 8f, s, s, 8, 8, 64, 64);  // hat overlay
    }

    /** The real vanilla map decorations player pointer (8×8 arrow), blitted rotated by the pose
     *  matrix so it stays crisp at any angle — identical to Hypixel's held-map marker. */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy, float yaw) {
        int s = Math.max(5, screenSize() / 27);   // size, scales with map
        int h = s / 2;
        g.pose().pushMatrix();
        g.pose().translate(cx + 0.5f, cy + 0.5f);
        g.pose().rotate((float) Math.toRadians(yaw + 180)); // MC yaw 0 = south(+z) = down on map
        g.blit(VANILLA_TEX_PIPE, ARROW_TEX, -h, -h, 0, 0, s, s, 8, 8);
        g.pose().popMatrix();
    }

    /** Current room name overlaid along the top of the map. */
    private static void drawRoomName(GuiGraphicsExtractor g, int ox, int oy) {
        String room = RoomMatch.currentRoom();
        if (room == null || room.isBlank()) return;
        String pretty = room.replace('-', ' ');
        Minecraft mc = Minecraft.getInstance();
        int w = mc.font.width(pretty);
        int x = ox + (screenSize() - w) / 2;
        int y = oy + 2;
        g.fill(x - 2, y - 1, x + w + 2, y + mc.font.lineHeight, 0xC00A0A14);
        g.text(mc.font, pretty, x, y, NebulaTheme.ACCENT_GOLD, true);
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, MAP - 1); }

    /** First inventory item carrying a map id = Hypixel's dungeon map. */
    private static MapItemSavedData findMap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            MapId id = stack.get(DataComponents.MAP_ID);
            if (id == null) continue;
            MapItemSavedData data = mc.level.getMapData(id);
            if (data != null) return data;
        }
        return null;
    }
}
