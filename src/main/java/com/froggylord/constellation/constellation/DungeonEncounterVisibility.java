package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dungeon-only danger highlights and clutter hiding. */
public final class DungeonEncounterVisibility {

    private static final int DEATHMITE_COLOUR = 0xFF8B0000;
    private static final int FEL_COLOUR = 0xFFFF00FF;
    private static final int MOVING_SKULL_COLOUR = 0xFFFFAA00;
    private static final long MOVING_SKULL_VISIBLE_MS = 200L;
    private static final long FEL_ACTIVE_MS = 750L;
    private static final String SOULWEAVER_TEXTURE = "eyJ0aW1lc3RhbXAiOjE1NTk1ODAzNjI1NTMsInByb2ZpbGVJZCI6ImU3NmYwZDlhZjc4MjQyYzM5NDY2ZDY3MjE3MzBmNDUzIiwicHJvZmlsZU5hbWUiOiJLbGxscmFoIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yZjI0ZWQ2ODc1MzA0ZmE0YTFmMGM3ODViMmNiNmE2YTcyNTYzZTlmM2UyNGVhNTVlMTgxNzg0NTIxMTlhYTY2In19fQ==";

    private static final Map<UUID, Motion> MOTION = new HashMap<>();
    private static Object trackedLevel;

    private DungeonEncounterVisibility() {}

    public static boolean anyOverlayEnabled(OrionConfig cfg) {
        return cfg.deathmiteHighlight || cfg.felHighlight || cfg.highlightMovingSkeletonSkulls;
    }

    public static void draw(WorldRenderer.Ctx ctx, Minecraft mc, OrionConfig cfg) {
        if (mc.level == null || mc.player == null) return;
        resetForLevel(mc.level);
        long now = System.currentTimeMillis();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.distanceToSqr(mc.player) > 3600.0) continue;
            Motion motion = updateMotion(entity, now);

            if (cfg.deathmiteHighlight && isDeathmite(entity, mc)) {
                AABB box = entity.getBoundingBox().inflate(0.12);
                ctx.highlight(box, DEATHMITE_COLOUR, true);
                ctx.label(entity.position().add(0, entity.getBbHeight() + 0.35, 0), "Deathmite", DEATHMITE_COLOUR, true);
                if (cfg.deathmiteTracer) ctx.line(mc.player.getEyePosition(1f), box.getCenter(), DEATHMITE_COLOUR, true);
            }

            // ported from NoammAddons (CC0-1.0):
            // src/main/kotlin/com/github/noamm9/features/impl/dungeon/HiddenMobs.kt
            // src/main/kotlin/com/github/noamm9/features/impl/dungeon/StarMobESP.kt
            if (cfg.felHighlight && isFel(entity) && (cfg.felHighlightActive || now - motion.lastMovedMs >= FEL_ACTIVE_MS)) {
                AABB box = entity.getBoundingBox().inflate(0.1);
                ctx.highlight(box, FEL_COLOUR, true);
                ctx.label(entity.position().add(0, entity.getBbHeight() + 0.35, 0), "Fel", FEL_COLOUR, true);
                if (cfg.felTracer) ctx.line(mc.player.getEyePosition(1f), box.getCenter(), FEL_COLOUR, true);
            }

            // ported from SkyHanni (LGPL-2.1):
            // src/main/java/at/hannibal2/skyhanni/features/dungeon/DungeonHideItems.kt
            if (cfg.highlightMovingSkeletonSkulls && isSkeletonSkull(entity)
                && now - motion.lastMovedMs < MOVING_SKULL_VISIBLE_MS) {
                ctx.highlight(entity.getBoundingBox().inflate(0.08), MOVING_SKULL_COLOUR, false);
            }
        }

        MOTION.entrySet().removeIf(entry -> now - entry.getValue().lastSeenMs > 5_000L);
    }

    public static boolean shouldHide(Entity entity) {
        if (!ConstellationClient.loc().inDungeons() || !(entity instanceof ArmorStand)) return false;
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null) return false;

        if (cfg.hideSoulweaverSkulls && isSoulweaverSkull(entity)) return true;
        if (!cfg.hideSkeletonSkulls || !isSkeletonSkull(entity)) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        resetForLevel(mc.level);
        long now = System.currentTimeMillis();
        Motion motion = updateMotion(entity, now);
        return now - motion.lastMovedMs >= MOVING_SKULL_VISIBLE_MS;
    }

    private static boolean isDeathmite(Entity entity, Minecraft mc) {
        if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand) return false;
        if (name(entity).equals("Deathmite")) return true;
        Vec3 pos = entity.position();
        for (Entity nearby : mc.level.getEntities(entity, entity.getBoundingBox().inflate(1.0, 2.5, 1.0))) {
            if (nearby instanceof ArmorStand && nearby.position().y() >= pos.y() && name(nearby).equals("Deathmite")) return true;
        }
        return false;
    }

    private static boolean isFel(Entity entity) {
        return entity instanceof EnderMan && name(entity).equals("Dinnerbone");
    }

    private static String name(Entity entity) {
        return entity.getCustomName() == null ? "" : entity.getCustomName().getString().trim();
    }

    private static boolean isSkeletonSkull(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) return false;
        ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
        return !helmet.isEmpty() && helmet.getHoverName().getString().trim().equals("Skeleton Skull");
    }

    // ported from Skyblocker (LGPL-3.0):
    // src/main/java/de/hysky/skyblocker/mixins/EntityRenderDispatcherMixin.java
    // src/main/java/de/hysky/skyblocker/skyblock/item/HeadTextures.java
    private static boolean isSoulweaverSkull(Entity entity) {
        if (!(entity instanceof ArmorStand stand) || !entity.isInvisible()) return false;
        ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.is(Items.PLAYER_HEAD)) return false;
        ResolvableProfile resolvable = helmet.get(DataComponents.PROFILE);
        if (resolvable == null) return false;
        GameProfile profile = resolvable.partialProfile();
        for (Property property : profile.properties().values()) {
            if (property.name().equals("textures") && property.value().equals(SOULWEAVER_TEXTURE)) return true;
        }
        return false;
    }

    private static Motion updateMotion(Entity entity, long now) {
        Motion old = MOTION.get(entity.getUUID());
        Vec3 pos = entity.position();
        if (old == null) {
            Motion created = new Motion(pos, now, 0L);
            MOTION.put(entity.getUUID(), created);
            return created;
        }
        if (old.position.distanceToSqr(pos) > 0.0001) old.lastMovedMs = now;
        old.position = pos;
        old.lastSeenMs = now;
        return old;
    }

    private static void resetForLevel(Object level) {
        if (trackedLevel == level) return;
        trackedLevel = level;
        MOTION.clear();
    }

    private static final class Motion {
        private Vec3 position;
        private long lastSeenMs;
        private long lastMovedMs;

        private Motion(Vec3 position, long lastSeenMs, long lastMovedMs) {
            this.position = position;
            this.lastSeenMs = lastSeenMs;
            this.lastMovedMs = lastMovedMs;
        }
    }
}
