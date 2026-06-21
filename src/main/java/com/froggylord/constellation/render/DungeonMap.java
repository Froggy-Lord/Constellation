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

    /** Player markers: an arrow facing your direction for yourself, dots for others. */
    private static void drawMarkers(GuiGraphicsExtractor g, int ox, int oy, MapItemSavedData data) {
        var decos = ((MapDataAccessor) (Object) data).constellation$decorations();
        if (decos == null) return;
        Minecraft mc = Minecraft.getInstance();

        for (var deco : decos.values()) {
            int mx = clamp((deco.x() / 2) + 64);
            int my = clamp((deco.y() / 2) + 64);
            int sx = ox + (mx * screenSize()) / MAP;
            int sy = oy + (my * screenSize()) / MAP;

            // the player's own marker is the PLAYER/FRAME decoration TYPE (usually unnamed)
            var type = deco.type();
            boolean isSelf = type.equals(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.PLAYER)
                          || type.equals(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.FRAME);

            if (isSelf && mc.player != null) {
                drawArrow(g, sx, sy, mc.player.getYRot());
            } else {
                // teammate marker — small dot
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFF000000);
                g.fill(sx, sy, sx + 1, sy + 1, 0xAAFFFFFF);
            }
        }
    }

    /** A filled arrowhead pointing in the player's facing direction (yaw degrees). */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy, float yaw) {
        double rad = Math.toRadians(yaw + 180); // MC yaw: 0=south(+z); screen +y is south
        double dirX = -Math.sin(rad), dirY = Math.cos(rad);   // facing vector
        double perpX = -dirY, perpY = dirX;                   // perpendicular
        int len = Math.max(4, screenSize() / 32);             // arrow length, scales with map
        int wide = Math.max(2, len / 2);

        // three points: tip, back-left, back-right
        double tipX = cx + dirX * len,        tipY = cy + dirY * len;
        double blX  = cx - dirX * len * 0.4 + perpX * wide, blY = cy - dirY * len * 0.4 + perpY * wide;
        double brX  = cx - dirX * len * 0.4 - perpX * wide, brY = cy - dirY * len * 0.4 - perpY * wide;

        // outline (black) then fill (gold) the triangle by scanning
        fillTriangle(g, tipX, tipY, blX, blY, brX, brY, 0xFF000000, 1.0);
        fillTriangle(g, tipX, tipY, blX, blY, brX, brY, NebulaTheme.ACCENT_GOLD, 0.0);
    }

    /** Scanline-fill a triangle (expand by `grow` px for a cheap outline pass). */
    private static void fillTriangle(GuiGraphicsExtractor g, double ax, double ay,
                                     double bx, double by, double ccx, double ccy, int col, double grow) {
        double cx = (ax + bx + ccx) / 3, cy = (ay + by + ccy) / 3;
        ax += Math.signum(ax - cx) * grow; ay += Math.signum(ay - cy) * grow;
        bx += Math.signum(bx - cx) * grow; by += Math.signum(by - cy) * grow;
        ccx += Math.signum(ccx - cx) * grow; ccy += Math.signum(ccy - cy) * grow;
        int minY = (int) Math.floor(Math.min(ay, Math.min(by, ccy)));
        int maxY = (int) Math.ceil(Math.max(ay, Math.max(by, ccy)));
        for (int y = minY; y <= maxY; y++) {
            Double xL = null, xR = null;
            double[][] edges = {{ax, ay, bx, by}, {bx, by, ccx, ccy}, {ccx, ccy, ax, ay}};
            for (double[] e : edges) {
                double y1 = e[1], y2 = e[3];
                if ((y >= y1 && y < y2) || (y >= y2 && y < y1)) {
                    double t = (y - y1) / (y2 - y1);
                    double x = e[0] + t * (e[2] - e[0]);
                    if (xL == null || x < xL) xL = x;
                    if (xR == null || x > xR) xR = x;
                }
            }
            if (xL != null && xR != null)
                g.fill((int) Math.floor(xL), y, (int) Math.ceil(xR) + 1, y + 1, col);
        }
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
