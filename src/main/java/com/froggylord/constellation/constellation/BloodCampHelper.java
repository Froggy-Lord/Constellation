package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.authlib.properties.Property;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dungeon/BloodCampHelper.java
// cross-checked with Odin (BSD-3-Clause): features/impl/dungeon/BloodCamp.kt
public final class BloodCampHelper {
    private static final Set<String> WATCHER_SKINS = Set.of(
        "2739d7f4e66a7db2ea6cd414e4c4ba41df7a92455c9fc42caab014665c367ad5",
        "bf6e1e7ed36586c2d98057002bc1adc981e2889f7bd7b5b3852bc55cc7802204",
        "e5c1dc47a04ce57001a8b726f018cdef40b7ea9d7bd6d835ca495a0ef169f893",
        "5662b6fb4b8b586dc4cdf803b0444d9b41d245cdf668dab38fa6c064afe8e461",
        "4cec40008e1c31c1984f4d650abb3410f2037119fd624afc953563b73515a077",
        "9fd61e8055f6ee97ab5b6196a8d7ec98078ac37e00376157b6b520eaaa2f93af",
        "b37dd18b5983a767e556dc64424af4b9abdb75d4c9e8b097818afbc431bf0e09",
        "f5f0d78fe38d1d7f75f08cdcf2a1855d6da0337e114a3c63e3bf3c618bc732b0",
        "51967db5e3199916252021903cf4e9952ef7cec220faaca1ba79bafe5938bd80"
    );
    private static final Set<String> BLOOD_MOB_SKINS = Set.of(
        "fb156cee370706408bb067261f59386f281eaf0bc24d168d9d01b13012946d04",
        "ac91f9afd84f2365cee8a53b61b9442b28e4f0e25bc6b6b1badbcdafc3e30c49",
        "7de7bbbdf22bfe17980d4e20687e386f11d59ee1db6f8b4762391b79a5ac532d",
        "3260325171a7ba8460830c0eea515c757a665e5b16a14207ba1a3182752bee87",
        "ad22772f769045fdc5be819ad68b01a97ac04c60886d2ca7afee39b282f7a383",
        "62d8fd3aa5617b1dac0aae9c81f6dd70ad93a59942f460d27e4d55a5cb8918e8",
        "c1007c5b7114abec734206d4fc613da4f3a0e99f71ff949cedadc99079135a0b",
        "69198f410a10f99314aa0fbe9a3db10697bbc1c011f019507d96673c64217f5a",
        "49f7cec00afe9f7c624ae8df5c033cb419f6ea41017021b9befd91970b833a5c",
        "3b48ec9c3e23a09e8aa2e1efbff9afb25e7315f9390984d01671dd0ae3c469ab",
        "12716ecbf5b8da00b05f316ec6af61e8bd02805b21eb8e440151468dc656549c",
        "a89f6303af85877610912dc04b8b1e89724752f0a7eea05ab6547e228179c06f",
        "aa23c8cde2943c84249de8351bc3540be5f8afaaba8b2cb032fc5acad78a269b",
        "9171f35b8f508142bd8c65417d0f324153ab9147739ee4d10dea733cc80eaa20",
        "b5ba76e02cab72fa7d8ac54ceec849976ab0b00a01068d68c266766bf70c3997",
        "7d12b2ade413a6cd7cca3c95e961ba9f0ae7165fa41fc7b5d5f094a01240c609",
        "67237eddaebdbbdaacfa912885560ccdc65da93b4c3d513532868ec23bb5b448",
        "5cccd53f5191c29a9dc8f0170fbdc4e59e66476aae33de27b468f1de1b7cf3b2",
        "5a79860aca799407c0faa10b1bbcf42998fad4ebcf31d7a214180826b4ac94e1",
        "c919e5b8d56f062a21d224de14af771e2f55d09b59e7b099d09daa57540b79cf",
        "4774871190c878c9a2c4496c1e10257c6c4ea13807d72c15d7ac6ab3a7a9a8dc"
    );
    private static final Map<ArmorStand, TrackedMob> MOBS = new HashMap<>();
    private static final Map<String, String> TEXTURE_HASHES = new HashMap<>();
    private static int mobsPredicted;
    private static boolean wasActive;
    private static boolean inited;

    private BloodCampHelper() {}

    public static void init() {
        if (inited) return;
        inited = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        var cfg = ConstellationClient.cfg().orion;
        boolean active = cfg != null && cfg.bloodCampHelper && ConstellationClient.loc().inDungeons()
            && !ConstellationClient.dungeon().inBoss() && BloodTimer.isActive() && mc.level != null;
        if (!active) {
            if (wasActive) reset();
            wasActive = false;
            return;
        }
        if (!wasActive) reset();
        wasActive = true;

        Set<Zombie> watchers = new HashSet<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Zombie zombie && WATCHER_SKINS.contains(headHash(zombie.getItemBySlot(EquipmentSlot.HEAD)))) {
                watchers.add(zombie);
            }
        }
        if (watchers.isEmpty()) return;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || MOBS.containsKey(stand)) continue;
            if (!BLOOD_MOB_SKINS.contains(headHash(stand.getItemBySlot(EquipmentSlot.HEAD)))) continue;
            if (watchers.stream().anyMatch(watcher -> watcher.distanceToSqr(stand) <= 400.0)) {
                MOBS.put(stand, new TrackedMob(stand));
            }
        }

        long now = System.currentTimeMillis();
        MOBS.entrySet().removeIf(entry -> !entry.getKey().isAlive());
        for (TrackedMob mob : MOBS.values()) mob.update(mob.entity.position(), now);
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.bloodCampHelper || !wasActive) return;
        long now = System.currentTimeMillis();
        for (TrackedMob mob : MOBS.values()) {
            if (mob.predictedPos == null) continue;
            Vec3 current = mob.entity.position().add(0, 2, 0);
            Vec3 predicted = mob.predictedPos.add(0, 2, 0);
            AABB endBox = new AABB(predicted.x - 0.5, predicted.y, predicted.z - 0.5,
                predicted.x + 0.5, predicted.y + 2, predicted.z + 0.5);
            ctx.outline(mob.entity.getBoundingBox().move(0, 2, 0), 0xFF55FF55, true);
            ctx.line(current, predicted, 0xFFFFFF55, true);
            ctx.outline(endBox, 0xFFFF5555, true);
            long remaining = (mob.firstWave ? 3900L : 1900L) - (now - mob.startTime);
            int colour = remaining > 1500 ? 0xFF55FF55 : remaining > 500 ? 0xFFFFAA00 : remaining > 0 ? 0xFFFF5555 : 0xFF55FFFF;
            ctx.label(predicted.add(0, 2.2, 0), String.format(Locale.ROOT, "%.1fs", remaining / 1000.0), colour, true);
        }
    }

    private static void reset() {
        MOBS.clear();
        mobsPredicted = 0;
    }

    // ported from Skyblocker (LGPL-3.0-or-later): utils/ItemUtils.java (getHeadTexture)
    private static String headHash(ItemStack stack) {
        if (!stack.is(Items.PLAYER_HEAD)) return "";
        var profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return "";
        String value = profile.partialProfile().properties().get("textures").stream()
            .filter(java.util.Objects::nonNull).map(Property::value).findFirst().orElse("");
        if (value.isEmpty()) return "";
        return TEXTURE_HASHES.computeIfAbsent(value, BloodCampHelper::decodeHash);
    }

    private static String decodeHash(String value) {
        try {
            String json = new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
            int start = json.indexOf("/texture/");
            if (start < 0 || json.length() < start + 73) return "";
            return json.substring(start + 9, start + 73).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/dungeon/BloodCampHelper.java (TrackedMob)
    private static final class TrackedMob {
        private static final int DELTA_SAMPLES = 5;
        private final ArmorStand entity;
        private final Vec3 startPos;
        private final Deque<Vec3> deltas = new ArrayDeque<>();
        private Vec3 lastPos;
        private long lastTime;
        private long startTime = -1;
        private boolean firstWave;
        private Vec3 predictedPos;

        private TrackedMob(ArmorStand entity) {
            this.entity = entity;
            this.startPos = entity.position();
            this.lastPos = startPos;
            this.lastTime = System.currentTimeMillis();
        }

        private void update(Vec3 currentPos, long now) {
            long dt = now - lastTime;
            lastTime = now;
            Vec3 delta = currentPos.subtract(lastPos);
            lastPos = currentPos;
            if (delta.lengthSqr() > 0 && dt > 0) {
                if (startTime < 0) startTime = now - dt;
                if (deltas.size() == DELTA_SAMPLES) deltas.removeFirst();
                deltas.addLast(delta);
            }
            if (deltas.size() != DELTA_SAMPLES || predictedPos != null) return;
            Vec3 total = Vec3.ZERO;
            for (Vec3 sample : deltas) total = total.add(sample);
            if (total.lengthSqr() == 0) return;
            firstWave = mobsPredicted < 4;
            mobsPredicted++;
            predictedPos = startPos.add(total.normalize().scale(firstWave ? 16.1 : 11.9));
        }
    }
}
