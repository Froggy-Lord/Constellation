package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

// charge pitches and measured heights ported from Odin (BSD-3-Clause): features/impl/skyblock/SpringBoots.kt
// cross-checked with Devonian (GPL-3.0): features/misc/SpringBootsProgress.kt
// cross-checked with NoammAddons (CC0-1.0): features/impl/visual/SpringBoots.kt
public final class SpringBootsHelper {
    private static final Set<Float> HIGH = Set.of(0.82539684f, 0.8888889f, 0.93650794f, 1.0476191f, 1.1746032f, 1.3174603f, 1.7777778f);
    private static final float LOW = 0.6984127f;
    private static final float[] HEIGHTS = {0,3,6.5f,9,11.5f,13.5f,16,18,19,20.5f,22.5f,25,26.5f,28,29,30,31,33,34,35.5f,37,38,39.5f,40,41,42.5f,43.5f,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61};
    private static OrionConfig cfg;
    private static int highs, lows;
    private static float height;
    private static double reachableHeight;
    private static boolean obstructed;
    private static boolean initialized;

    private SpringBootsHelper() {}

    public static void init(OrionConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.instance().packets().register(packet -> { if (packet instanceof ClientboundSoundPacket sound) onSound(sound); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void onSound(ClientboundSoundPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null) return;
        var id = packet.getSound().value().location();
        float pitch = packet.getPitch();
        if (id.equals(SoundEvents.NOTE_BLOCK_PLING.value().location()) && mc.player.isCrouching() && wearing()) {
            if (Float.compare(pitch, LOW) == 0) lows = Math.min(2, lows + 1);
            else if (HIGH.contains(pitch)) highs++;
            height = HEIGHTS[Math.clamp(lows + highs, 0, HEIGHTS.length - 1)];
        } else if (id.equals(SoundEvents.FIREWORK_ROCKET_LAUNCH.location())
            && (Float.compare(pitch, 0.0952381f) == 0 || Float.compare(pitch, 1.6984127f) == 0)) reset();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || !wearing() || (!mc.player.isCrouching() && mc.player.onGround())) { reset(); return; }
        calculateReachable();
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || mc.level == null || height <= 0 || !wearing()) return;
        Vec3 start = mc.player.position();
        double clearY = start.y + reachableHeight;
        int colour = obstructed ? 0xFFFF5555 : cfg.springBootsColour;
        boolean walls = cfg.springBootsThroughWalls;
        Vec3 end = new Vec3(start.x, clearY, start.z);
        if (cfg.springBootsLine) ctx.line(start.add(0, 0.1, 0), end, colour, walls);
        if (cfg.springBootsBox) ctx.highlight(new AABB(end.x - 0.45, end.y, end.z - 0.45, end.x + 0.45, end.y + 1.8, end.z + 0.45), colour, walls);
        ctx.label(end.add(0, 2.05, 0), (obstructed ? "Blocked at " : "Spring Boots ") + format(reachableHeight) + " blocks", colour, walls);
    }

    public static String hudText() {
        if (!active() || height <= 0 || !wearing()) return null;
        int count = Math.clamp(lows + highs, 0, HEIGHTS.length - 1);
        return String.format("§d%.1f blocks §7(%d%%)", height, Math.round(100f * count / (HEIGHTS.length - 1)));
    }

    private static boolean wearing() { Minecraft mc = Minecraft.getInstance(); return mc.player != null && id(mc.player.getItemBySlot(EquipmentSlot.FEET)).equals("SPRING_BOOTS"); }
    private static String id(ItemStack stack) { CustomData data = stack.get(DataComponents.CUSTOM_DATA); CompoundTag extra = data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes"); return extra.getStringOr("id", ""); }
    private static boolean active() { return cfg != null && cfg.enabled && cfg.springBootsHelper && ConstellationClient.loc().onHypixel(); }
    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, value)); }
    private static void calculateReachable() {
        Minecraft mc = Minecraft.getInstance();
        reachableHeight = height;
        obstructed = false;
        if (mc.player == null || mc.level == null) return;
        AABB box = mc.player.getBoundingBox();
        for (double rise = 0.25; rise <= height; rise += 0.25) {
            if (!mc.level.noCollision(mc.player, box.move(0, rise, 0))) {
                reachableHeight = Math.max(0, rise - 0.25);
                obstructed = true;
                return;
            }
        }
    }
    private static void reset() { highs = 0; lows = 0; height = 0; reachableHeight = 0; obstructed = false; }
}
