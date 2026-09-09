package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Drop / secret ESP for dungeons: secret bats and dropped secret items.
 *
 * Detection logic ported from NoFrills (GPL-3):
 *   - features/dungeons/SecretBatHighlight.java + misc/DungeonUtil.isSecretBat
 *   - features/dungeons/SecretChime.java (authoritative secret-item list)
 *   - misc/Utils.isBaseHealth
 * Adapted from NoFrills' event+cache model to constellation's per-frame WorldRenderer.
 */
public final class DropEsp {

    private DropEsp() {}

    private static final double RANGE = 60.0;
    private static final int ITEM_COLOUR = 0xFFFFFF55; // yellow
    private static final int BAT_COLOUR = 0xFF55FF55;  // green (NoFrills SecretBatHighlight default)
    private static final int SPIRIT_BOW_COLOUR = 0xFFAF00FF;
    private static final Set<Integer> ALERTED_RARE_DROPS = new HashSet<>();

    // dungeon secret / notable drops, keyed by display name. The core set is ported from
    // NoFrills SecretChime.secretItems; a few common mob-secret drops are kept from before.
    private static final String[] WATCH = {
        "Spirit Leap", "Decoy", "Training Weights", "Architect's First Draft",
        "Trap", "Defuse Kit", "Inflatable Jerry", "Dungeon Chest Key",
        "Treasure Talisman", "Revive Stone", "Candycomb", "Healing VIII",
        "Spirit Bow", "Premium Flesh"
    };

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null) return;
        if (!cfg.dropEsp && !cfg.secretBats && !cfg.spiritBowHighlight && !cfg.rareDropAlerts) return;
        if (!ConstellationClient.loc().inDungeons()) {
            ALERTED_RARE_DROPS.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 me = mc.player.position();
        double r2 = RANGE * RANGE;

        boolean f4Boss = isF4Boss();

        for (var e : mc.level.entitiesForRendering()) {
            // ported from devonian (GPL-3.0): features/dungeons/clear/RareDungeonMobDropAlert.kt
            // cross-checked with Athen (BSD-3-Clause): modules/impl/dungeon/RareItemAlert.kt
            if (cfg.rareDropAlerts && e instanceof ArmorStand stand && stand.hasCustomName()) {
                String name = stand.getCustomName().getString();
                String drop = null;
                if (name.contains("Ice Spray Wand")) {
                    drop = "Ice Spray Wand";
                } else if (name.contains("Skeleton Master Chestplate") && isM7()) {
                    drop = "Skeleton Master Chestplate";
                }
                if (drop != null && ALERTED_RARE_DROPS.add(stand.getId())) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(Component.literal("§6RARE DROP: §e" + drop));
                }
            }

            if (e.distanceToSqr(me) > r2) continue;

            // ported from NoFrills (GPL-3.0): features/dungeons/SpiritBowHighlight.java
            if (cfg.spiritBowHighlight && f4Boss && e instanceof ArmorStand stand
                && stand.hasCustomName() && stand.getCustomName().getString().equals("Spirit Bow")) {
                BlockPos ground = stand.blockPosition();
                for (int down = 0; down < 4 && mc.level.getBlockState(ground).isAir(); down++)
                    ground = ground.below();
                Vec3 centre = new Vec3(stand.getX(), ground.getY() + 2, stand.getZ());
                ctx.highlight(AABB.ofSize(centre, 0.8, 1.75, 0.8), SPIRIT_BOW_COLOUR, true);
                ctx.label(centre.add(0, 1, 0), "Spirit Bow", SPIRIT_BOW_COLOUR, true);
                continue;
            }

            // secret bats — 100-HP (or multiple thereof) bats, ported from DungeonUtil.isSecretBat.
            // ambient/decoration bats have 6 HP so this cleanly excludes them.
            if (cfg.secretBats && e instanceof Bat bat) {
                if (!f4Boss && isBaseHealth(bat, 100.0f)) {
                    ctx.outline(bat.getBoundingBox().inflate(0.05), BAT_COLOUR, false);
                }
                continue;
            }

            if (cfg.dropEsp && e instanceof ItemEntity ie) {
                String name = ie.getItem().getHoverName().getString();
                for (String w : WATCH) {
                    if (name.contains(w)) {
                        ctx.outline(ie.getBoundingBox().inflate(0.15), ITEM_COLOUR, false);
                        ctx.label(new Vec3(ie.getX(), ie.getY() + 0.5, ie.getZ()), w, ITEM_COLOUR, false);
                        break;
                    }
                }
            }
        }
    }

    // ported from NoFrills Utils.isBaseHealth — true when the entity's health is a multiple of the
    // given base value (secret bats spawn with 100 HP; scaled floors give 200/300/...)
    private static boolean isBaseHealth(Bat bat, float health) {
        float current = bat.getHealth();
        return current >= health && current % health == 0;
    }

    private static boolean isF4Boss() {
        var dungeon = ConstellationClient.dungeon();
        return dungeon.inBoss() && dungeon.floor().endsWith("4");
    }

    private static boolean isM7() {
        return "M7".equalsIgnoreCase(ConstellationClient.dungeon().floor());
    }
}
