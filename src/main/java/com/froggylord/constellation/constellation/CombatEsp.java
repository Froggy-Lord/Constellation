package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Combat / mob ESP for dungeons: starred mobs and floor minibosses.
 *
 * Detection logic ported from NoFrills (GPL-3):
 *   - features/dungeons/StarredMobHighlight.java
 *   - features/dungeons/MinibossHighlight.java
 *   - misc/Utils.java (isMob, isPlayer, findNametagOwner)
 * Adapted from NoFrills' event+cache model to constellation's per-frame WorldRenderer.
 */
public final class CombatEsp {

    private static final double RANGE = 60.0;

    // starred-mob outline: NoFrills default cyan
    private static final int STAR_COLOUR = 0xFF00FFFF;

    // the Skyblock star symbol lives on the floating nametag armor stand, not the mob itself
    private static final char STAR = '✯';

    // ported from NoFrills MinibossHighlight.minibossList (extended with F3/F6 archaeologist)
    private static final Set<String> MINIBOSSES = new HashSet<>(List.of(
        "Lost Adventurer",
        "Diamond Guy",
        "Shadow Assassin",
        "King Midas",
        "Spirit Bear",
        "Angry Archeologist"
    ));

    private CombatEsp() {}

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null) return;
        if (!cfg.starredMobs && !cfg.teammateBoxes && !cfg.minibossHighlights
            && !cfg.guardianHealth && !cfg.doorTracker
            && !DungeonEncounterVisibility.anyOverlayEnabled(cfg)) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 me = mc.player.position();
        double r2 = RANGE * RANGE;

        DungeonEncounterVisibility.draw(ctx, mc, cfg);

        // class teammate glow — see-through outline coloured by each teammate's dungeon class,
        // parsed from the tab list via DungeonState. Ported from NoFrills (GPL-3):
        // features/dungeons/ClassNametags.java (teammate-entity resolution + class colour palette).
        if (cfg.teammateBoxes) {
            var dungeon = ConstellationClient.dungeon();
            for (var p : mc.level.players()) {
                if (p == mc.player) continue;
                if (p.isSpectator() || p.getName().getString().isBlank()) continue;
                String dungeonClass = dungeon.classOf(p.getName().getString());
                if (dungeonClass.isEmpty()) continue;
                int col = DungeonClassInfo.colour(dungeonClass);
                if (col == 0) continue;
                // throughWalls so you can track a teammate's role across rooms
                ctx.outline(p.getBoundingBox(), col, true);
                ctx.label(p.position().add(0, p.getBbHeight() + 0.4, 0),
                    "[" + DungeonClassInfo.initial(dungeonClass) + "] " + p.getName().getString(), col, true);
            }
        }

        // one pass over the world to gather candidate mobs (for nametag-owner resolution) and
        // to run the per-entity highlights (minibosses, guardian health).
        List<Entity> mobCandidates = new ArrayList<>();
        List<Entity> starNametags = new ArrayList<>();
        Set<Entity> boxed = new HashSet<>();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            if (e.distanceToSqr(me) > r2) continue;

            // ported from NoFrills (GPL-3.0): features/dungeons/KeyHighlight.java
            if (cfg.doorTracker && e.hasCustomName()) {
                Component keyName = e.getCustomName();
                if (keyName != null && isDungeonKey(keyName.getString())) {
                    AABB keyBox = AABB.ofSize(e.position().add(0, 1.5, 0), 1, 1, 1);
                    ctx.highlight(keyBox, 0xFF00FF00, true);
                    ctx.beam(keyBox.getCenter().x, keyBox.maxY, keyBox.getCenter().z,
                        0xFF00FF00, 32, true);
                    ctx.label(keyBox.getCenter().add(0, 0.8, 0), keyName.getString(), 0xFF00FF00, true);
                    continue;
                }
            }

            // a starred mob's name is rendered on a nearby ArmorStand nametag; collect those
            // stands so we can map each back to the living mob it labels
            if (cfg.starredMobs && e instanceof ArmorStand && e.hasCustomName()) {
                Component cn = e.getCustomName();
                if (cn != null && hasSingleStar(cn.getString())) {
                    starNametags.add(e);
                    continue;
                }
            }

            if (cfg.starredMobs && isMob(e)) mobCandidates.add(e);

            // minibosses are NPC player entities matched by name (ported from MinibossHighlight)
            if (cfg.minibossHighlights && isMiniboss(e)) {
                int col = minibossColour(e.getName().getString());
                ctx.outline(e.getBoundingBox().inflate(0.1), col, false);
                ctx.label(e.position().add(0, e.getBbHeight() + 0.3, 0), e.getName().getString(), col, false);
                boxed.add(e);
            }

            if (cfg.guardianHealth && e instanceof net.minecraft.world.entity.monster.Guardian g) {
                Component name = g.getCustomName();
                if (name != null && name.getString().contains("❤")) {
                    ctx.outline(g.getBoundingBox().inflate(0.2), 0xFF55FFFF, false);
                    ctx.label(g.position().add(0, g.getBbHeight() + 0.5, 0),
                        name.getString(), 0xFF55FFFF, false);
                }
            }
        }

        // resolve each starred nametag to its owning mob and box that mob
        if (cfg.starredMobs) {
            for (Entity tag : starNametags) {
                Entity owner = findNametagOwner(tag, mobCandidates);
                if (owner != null && boxed.add(owner)) {
                    ctx.outline(owner.getBoundingBox().inflate(0.05), STAR_COLOUR, false);
                }
            }
        }

    }

    // ported from NoFrills StarredMobHighlight.isStarred — exactly one star means a mob nametag
    // (boss/other multi-star labels are ignored)
    private static boolean hasSingleStar(String name) {
        int index = name.indexOf(STAR);
        return index != -1 && index == name.lastIndexOf(STAR);
    }

    private static boolean isDungeonKey(String name) {
        return name.equals("Wither Key") || name.equals("Blood Key");
    }

    // ported from NoFrills Utils.isPlayer — real players have a v4 (random) UUID; Skyblock NPCs do not
    private static boolean isRealPlayer(Player p) {
        return p.getUUID().version() == 4;
    }

    // ported from NoFrills Utils.isMob
    private static boolean isMob(Entity e) {
        if (e instanceof Player p) return !isRealPlayer(p) && p.isAlive();
        return e instanceof LivingEntity && e.isAlive();
    }

    // ported from NoFrills MinibossHighlight.isMiniboss
    private static boolean isMiniboss(Entity e) {
        if (!(e instanceof Player p) || isRealPlayer(p)) return false;
        String name = p.getName().getString();
        if (!MINIBOSSES.contains(name)) return false;
        // on F4/M4 the "crowd" NPCs share these names above y=76; only the arena copy counts
        if (isF4Boss()) return p.position().y() < 76.0;
        // otherwise ignore the Spirit Bear the F7 Watcher spawns
        return !name.equals("Spirit Bear");
    }

    private static boolean isF4Boss() {
        var dungeon = ConstellationClient.dungeon();
        return dungeon.inBoss() && dungeon.floor().endsWith("4");
    }

    // ported from NoFrills Utils.findNametagOwner — closest living mob horizontally, sitting below
    // the nametag stand (the label floats above the mob's head)
    private static Entity findNametagOwner(Entity nametag, List<Entity> candidates) {
        AABB search = AABB.ofSize(nametag.position(), 0.5, 2.0, 0.5);
        Entity owner = null;
        float lowest = 2.0f;
        double maxY = nametag.position().y();
        for (Entity c : candidates) {
            if (!c.getBoundingBox().intersects(search)) continue;
            float dist = horizontalDistance(c.position(), nametag.position());
            if (c.position().y() < maxY && dist < lowest) {
                owner = c;
                lowest = dist;
            }
        }
        return owner;
    }

    private static float horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static int minibossColour(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("shadow assassin")) return 0xFFAA00FF; // purple
        if (n.contains("king midas")) return 0xFFFFAA00;      // gold
        if (n.contains("spirit bear")) return 0xFFFFFFFF;     // white
        return 0xFFFF5555;                                    // red (adventurer / diamond guy / archeologist)
    }
}
