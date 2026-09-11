package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// ported from Odin (BSD-3-Clause):
// src/main/kotlin/com/odtheking/odin/features/impl/boss/WitherDragonsEnum.kt
// src/main/kotlin/com/odtheking/odin/features/impl/boss/DragonPriority.kt
public final class M7Dragons {

    private enum Dragon {
        RED("Red", 27, 14, 59, 0xFFFF5555),
        ORANGE("Orange", 85, 14, 56, 0xFFFFAA00),
        GREEN("Green", 27, 14, 94, 0xFF55FF55),
        BLUE("Blue", 84, 14, 94, 0xFF55FFFF),
        PURPLE("Purple", 56, 14, 125, 0xFFAA00AA);

        final String name;
        final Vec3 spawn;
        final Vec3 stack;
        final int colour;
        final int skipTicks;

        Dragon(String name, int x, int y, int z, int colour) {
            this.name = name;
            this.spawn = new Vec3(x, y, z);
            this.stack = switch (name) {
                case "Red" -> new Vec3(28.5, 17.5, 58.5);
                case "Green" -> new Vec3(27.5, 17.5, 90.5);
                case "Blue" -> new Vec3(84.5, 17.5, 97.5);
                default -> new Vec3(x + 0.5, y + 3.5, z + 0.5);
            };
            this.colour = colour;
            this.skipTicks = switch (name) {
                case "Red" -> 50;
                case "Orange" -> 62;
                case "Green" -> 52;
                case "Blue" -> 47;
                case "Purple" -> 38;
                default -> 0;
            };
        }
    }

    private static final List<Dragon> DEFAULT_PRIORITY = List.of(
        Dragon.RED, Dragon.ORANGE, Dragon.BLUE, Dragon.PURPLE, Dragon.GREEN);
    private static final Map<UUID, Dragon> identities = new HashMap<>();
    private static final Map<UUID, Integer> spawnOrder = new HashMap<>();
    private static final Map<Dragon, Spawn> spawning = new ConcurrentHashMap<>();
    private static final Map<Dragon, DragonRun> runs = new EnumMap<>(Dragon.class);
    private static Dragon lastDragon;
    private static int lastHits;
    private static long lastResultUntil;
    private static Object levelKey;
    private static int nextSpawn = 1;
    private static boolean initialized;

    private M7Dragons() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConstellationClient.instance().packets().register(M7Dragons::onPacket);
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || (!cfg.m7DragonMarkers && !cfg.m7DragonStackAimer && !cfg.m7DragonHitCounter)) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        if (!ConstellationClient.dungeon().floor().endsWith("7") || !ConstellationClient.dungeon().inBoss()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (levelKey != mc.level) reset(mc.level);

        drawStackAim(ctx, mc);

        List<EnderDragon> alive = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EnderDragon dragon) || dragon.getHealth() <= 0) continue;
            alive.add(dragon);
            identities.computeIfAbsent(dragon.getUUID(), ignored -> nearest(dragon.position()));
            spawnOrder.computeIfAbsent(dragon.getUUID(), ignored -> nextSpawn++);
        }
        updateRuns(alive);
        if (!cfg.m7DragonMarkers) return;
        if (alive.isEmpty()) return;

        EnderDragon priority = priority(alive);
        for (EnderDragon dragon : alive) {
            Dragon kind = identities.get(dragon.getUUID());
            boolean isPriority = dragon == priority;
            int colour = isPriority ? 0xFF55FF55 : kind.colour;
            Vec3 labelPos = dragon.position().add(0, dragon.getBbHeight() + 1, 0);
            ctx.outline(dragon.getBoundingBox().inflate(0.3), colour, false);
            ctx.label(labelPos, (isPriority ? "PRIORITY - " : "") + kind.name + " #" + spawnOrder.get(dragon.getUUID()),
                colour, false);
            // ported from devonian (GPL-3.0): features/dungeons/m7/DragonHealth.kt
            // formatting thresholds cross-checked with Odin (BSD-3-Clause): features/impl/boss/WitherDragons.kt
            float health = dragon.getHealth();
            ctx.label(labelPos.add(0, -0.65, 0), formatHealth(health), healthColour(health), true);
            DragonRun run = runs.get(kind);
            if (cfg.m7DragonHitCounter && run != null && run.uuid.equals(dragon.getUUID()))
                ctx.label(labelPos.add(0, -1.3, 0), "Arrows " + run.hits, kind.colour, true);
        }
    }

    // spawn signature and five-second window ported from devonian (GPL-3.0):
    // features/dungeons/m7/M7Events.kt and features/dungeons/m7/DragonStackAimer.kt
    private static void onPacket(Object raw) {
        if (raw instanceof ClientboundSoundPacket sound) {
            trackArrow(sound);
            return;
        }
        if (!(raw instanceof ClientboundLevelParticlesPacket packet) || !stackActive()) return;
        if (packet.getParticle().getType() != ParticleTypes.FLAME || packet.getCount() != 20
            || packet.getXDist() != 2f || packet.getYDist() != 3f || packet.getZDist() != 2f
            || packet.getMaxSpeed() != 0f || !packet.isOverrideLimiter() || !packet.alwaysShow()) return;
        double px = packet.getX(), pz = packet.getZ();
        if (px % 1 != 0 || pz % 1 != 0) return;
        int y = (int) packet.getY();
        if (y != 19 && y != 27) return;
        Dragon dragon = Arrays.stream(Dragon.values())
            .filter(value -> (int) value.spawn.x == (int) px && (int) value.spawn.z == (int) pz)
            .findFirst().orElse(null);
        if (dragon == null) return;
        long now = System.currentTimeMillis();
        Spawn previous = spawning.get(dragon);
        if (previous == null || now >= previous.spawnAt) spawning.put(dragon, new Spawn(now + 5000, y == 27));
    }

    private static void drawStackAim(WorldRenderer.Ctx ctx, Minecraft mc) {
        if (!stackActive() || mc.player == null) { spawning.clear(); return; }
        long now = System.currentTimeMillis();
        spawning.entrySet().removeIf(entry -> now - entry.getValue().spawnAt > 1500);
        for (var entry : spawning.entrySet()) {
            Spawn spawn = entry.getValue();
            if (now > spawn.spawnAt + 1500) continue;
            Vec3 target = entry.getKey().stack.add(0, spawn.high ? 8 : 0, 0);
            Aim aim = calculateLead(mc.player.getEyePosition(), target);
            if (aim == null) continue;
            double size = Math.max(0.45, Math.sqrt(mc.player.getEyePosition().distanceTo(target) / 50.0));
            AABB marker = new AABB(aim.position.x - size / 2, aim.position.y - size / 2, aim.position.z - size / 2,
                aim.position.x + size / 2, aim.position.y + size / 2, aim.position.z + size / 2);
            ctx.highlight(marker, 0xFF00FFFF, true);
            ctx.line(mc.player.getEyePosition(), aim.position, 0xFF00FFFF, true);
            ctx.label(aim.position.add(0, size + 0.25, 0), entry.getKey().name + " STACK", 0xFF00FFFF, true);
        }
    }

    // ported from NoammAddons (CC0-1.0): features/impl/floor7/dragons/WitherDragons.kt
    private static Aim calculateLead(Vec3 eye, Vec3 target) {
        double distanceSq = target.distanceToSqr(eye);
        double arrowDistance = 0, speed = 3, yVelocity = 0, drop = 0;
        for (int tick = 1; tick <= 160; tick++) {
            arrowDistance += speed;
            speed *= 0.99;
            drop += yVelocity;
            yVelocity -= 0.05;
            yVelocity *= 0.99;
            if (arrowDistance * arrowDistance >= distanceSq)
                return new Aim(target.subtract(0, drop, 0), tick);
        }
        return null;
    }

    public static String stackHudText() {
        if (!stackActive() || !ConstellationClient.cfg().orion.m7DragonStackHud) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || spawning.isEmpty()) return null;
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        for (var entry : spawning.entrySet()) {
            Vec3 target = entry.getKey().stack.add(0, entry.getValue().high ? 8 : 0, 0);
            Aim aim = calculateLead(mc.player.getEyePosition(), target);
            if (aim == null) continue;
            long ping = ConstellationClient.cfg().orion.m7DragonStackPing ? latency(mc) : 0;
            best = Math.min(best, entry.getValue().spawnAt - now - aim.ticks * 50L - ping);
        }
        if (best == Long.MAX_VALUE || best < -1000) return null;
        return best <= 0 ? "§bNOW" : (best <= 1500 ? "§c" : best <= 3000 ? "§e" : "§a") + best + "ms";
    }

    // local arrow-hit sound and per-dragon skip windows ported from NoammAddons (CC0-1.0):
    // features/impl/floor7/dragons/DragonCheck.kt and WitherDragonEnum.kt
    private static void trackArrow(ClientboundSoundPacket packet) {
        OrionConfig cfg = ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.m7DragonHitCounter || !dragonPhase()
            || packet.getSound().value() != SoundEvents.ARROW_HIT_PLAYER) return;
        DragonRun run = priorityRun();
        if (run == null || System.currentTimeMillis() - run.startedAt > run.dragon.skipTicks * 50L) return;
        run.hits++;
    }

    public static String hitHudText() {
        OrionConfig cfg = ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.m7DragonHitCounter || !cfg.m7DragonHitHud || !dragonPhase()) return null;
        DragonRun run = priorityRun();
        if (run != null) {
            long window = Math.max(0, run.dragon.skipTicks * 50L - (System.currentTimeMillis() - run.startedAt));
            return "§" + colourCode(run.dragon) + run.dragon.name + " §f" + run.hits + " arrows §7" + window + "ms";
        }
        if (lastDragon != null && System.currentTimeMillis() < lastResultUntil)
            return "§" + colourCode(lastDragon) + lastDragon.name + " §f" + lastHits + " arrows";
        return null;
    }

    private static void updateRuns(List<EnderDragon> alive) {
        long now = System.currentTimeMillis();
        Set<UUID> current = new HashSet<>();
        for (EnderDragon entity : alive) {
            Dragon dragon = identities.get(entity.getUUID());
            current.add(entity.getUUID());
            DragonRun old = runs.get(dragon);
            if (old == null || !old.uuid.equals(entity.getUUID())) runs.put(dragon, new DragonRun(dragon, entity.getUUID(), now));
        }
        for (Dragon dragon : Dragon.values()) {
            DragonRun run = runs.get(dragon);
            if (run != null && !current.contains(run.uuid) && now - run.startedAt > 500) finish(run);
        }
    }

    private static void finish(DragonRun run) {
        if (!runs.remove(run.dragon, run)) return;
        lastDragon = run.dragon;
        lastHits = run.hits;
        lastResultUntil = System.currentTimeMillis() + 5000;
        OrionConfig cfg = ConstellationClient.cfg().orion;
        Minecraft mc = Minecraft.getInstance();
        double seconds = (System.currentTimeMillis() - run.startedAt) / 1000.0;
        if (cfg.m7DragonHitReport && mc.player != null)
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§" + colourCode(run.dragon)
                + run.dragon.name + " dragon §8> §f" + run.hits + " arrows in "
                + String.format(Locale.ROOT, "%.2fs", seconds)));
        if (cfg.m7DragonHitPartyMessage)
            PartyMessages.send("dragon-hits", Map.of("dragon", run.dragon.name, "hits", run.hits,
                "time", String.format(Locale.ROOT, "%.2f", seconds)));
    }

    private static DragonRun priorityRun() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        List<EnderDragon> alive = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering())
            if (entity instanceof EnderDragon dragon && dragon.getHealth() > 0) alive.add(dragon);
        EnderDragon entity = priority(alive);
        return entity == null ? null : runs.get(identities.get(entity.getUUID()));
    }

    private static EnderDragon priority(List<EnderDragon> alive) {
        return alive.stream().min(Comparator.comparingInt(dragon ->
            DEFAULT_PRIORITY.indexOf(identities.getOrDefault(dragon.getUUID(), nearest(dragon.position()))))).orElse(null);
    }

    private static boolean dragonPhase() {
        OrionConfig cfg = ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().orion;
        return cfg != null && cfg.enabled && ConstellationClient.loc().inDungeons()
            && ConstellationClient.dungeon().floor().endsWith("7")
            && "Wither King".equals(ConstellationClient.dungeon().bossPhase());
    }

    private static char colourCode(Dragon dragon) {
        return switch (dragon) { case RED -> 'c'; case ORANGE -> '6'; case GREEN -> 'a'; case BLUE -> 'b'; case PURPLE -> '5'; };
    }

    private static int latency(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) return 0;
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    private static boolean stackActive() {
        OrionConfig cfg = ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().orion;
        return cfg != null && cfg.enabled && cfg.m7DragonStackAimer && ConstellationClient.loc().inDungeons()
            && ConstellationClient.dungeon().floor().endsWith("7")
            && "Wither King".equals(ConstellationClient.dungeon().bossPhase());
    }

    private static int healthColour(float health) {
        if (health >= 750_000_000) return 0xFF55FF55;
        if (health >= 500_000_000) return 0xFFFFFF55;
        if (health >= 250_000_000) return 0xFFFFAA00;
        return 0xFFFF5555;
    }

    private static String formatHealth(float health) {
        if (health >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fb", health / 1_000_000_000.0);
        if (health >= 1_000_000) return String.format(Locale.ROOT, "%.1fm", health / 1_000_000.0);
        if (health >= 1_000) return String.format(Locale.ROOT, "%.1fk", health / 1_000.0);
        return String.valueOf(Math.round(health));
    }

    private static Dragon nearest(Vec3 position) {
        Dragon nearest = Dragon.RED;
        double distance = Double.MAX_VALUE;
        for (Dragon dragon : Dragon.values()) {
            double next = dragon.spawn.distanceToSqr(position);
            if (next < distance) {
                nearest = dragon;
                distance = next;
            }
        }
        return nearest;
    }

    private static void reset(Object level) {
        levelKey = level;
        identities.clear();
        spawnOrder.clear();
        spawning.clear();
        runs.clear();
        lastDragon = null;
        lastHits = 0;
        lastResultUntil = 0;
        nextSpawn = 1;
    }

    private record Spawn(long spawnAt, boolean high) {}
    private record Aim(Vec3 position, int ticks) {}
    private static final class DragonRun {
        final Dragon dragon;
        final UUID uuid;
        final long startedAt;
        int hits;
        DragonRun(Dragon dragon, UUID uuid, long startedAt) { this.dragon = dragon; this.uuid = uuid; this.startedAt = startedAt; }
    }
}
