package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Athen (BSD-3-Clause): api/slayers/SlayerAPI.kt
// ported from Athen (BSD-3-Clause): api/slayers/data/SlayerInfo.kt
// entity fallback ported from Skyblocker (LGPL-3.0-or-later): skyblock/slayers/SlayerManager.java
public final class SlayerState {
    public enum Type {
        REV("REV", 5, "Revenant Horror", "Atoned Horror"),
        TARA("TARA", 5, "Tarantula Broodfather", "Conjoined Brood"),
        SVEN("SVEN", 4, "Sven Packmaster"),
        VOID("VOID", 4, "Voidgloom Seraph"),
        BLAZE("BLAZE", 4, "Inferno Demonlord"),
        VAMP("VAMP", 5, "Riftstalker Bloodfiend", "Bloodfiend");

        private final String shortName;
        private final int maxTier;
        private final List<String> names;

        Type(String shortName, int maxTier, String... names) {
            this.shortName = shortName;
            this.maxTier = maxTier;
            this.names = List.of(names);
        }

        public String shortName() { return shortName; }
        public int maxTier() { return maxTier; }

        private boolean matches(Entity entity) {
            return switch (this) {
                case REV -> entity instanceof Zombie;
                case TARA -> entity instanceof Spider;
                case SVEN -> entity instanceof Wolf;
                case VOID -> entity instanceof EnderMan;
                case BLAZE -> entity instanceof Blaze;
                case VAMP -> entity instanceof Player player && player.getUUID().version() != 4;
            };
        }

        private static Type fromName(String value) {
            for (Type type : values()) for (String name : type.names) if (value.contains(name)) return type;
            return null;
        }
    }

    public record Boss(Entity entity, Type type, int tier, String owner, String variant, int nameStandId, int ownerStandId,
                       long spawnedAtNanos, int spawnedAtTick, long spawnedAtServerTick) {
        public double secondsAlive() { return Math.max(0, (System.nanoTime() - spawnedAtNanos) / 1_000_000_000.0); }
    }

    public interface Listener {
        void onSpawn(Boss boss);
        void onDeath(Boss boss, double seconds, int clientTicks);
        default void onOwnerReset(String owner) {}
        default void onReset() {}
    }

    private static final Pattern BOSS_NAME = Pattern.compile(
        "(Revenant Horror|Atoned Horror|Tarantula Broodfather|Conjoined Brood|Sven Packmaster|Voidgloom Seraph|Inferno Demonlord|Riftstalker Bloodfiend|Bloodfiend)(?:\\s+([MDCLXVI]{1,7}))?\\b");
    private static final Pattern OWNER = Pattern.compile("(?:Spawned by|Owner):\\s*(?:\\[[^]]*]\\s*)?(\\w{1,16})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARRIER_DEATH = Pattern.compile("^☠ (\\w{1,16}) was killed by .+\\.$");
    private static final Map<Integer, Boss> BOSSES = new LinkedHashMap<>();
    private static final Set<Integer> INVALIDATED = new java.util.HashSet<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean initialized;
    private static long lastTick = Long.MIN_VALUE;
    private static long serverTicks;

    private SlayerState() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            String value = ChatFormatting.stripFormatting(message.getString());
            String text = value == null ? message.getString().trim() : value.trim();
            if (text.equals("SLAYER QUEST FAILED!") || text.equals("Your Slayer Quest has been cancelled!")) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) clearOwner(mc.player.getName().getString());
            }
            Matcher death = CARRIER_DEATH.matcher(text);
            if (death.matches()) clearOwner(death.group(1));
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void listen(Listener listener) {
        if (!LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
            for (Boss boss : BOSSES.values()) listener.onSpawn(boss);
        }
    }

    public static List<Boss> bosses() { return List.copyOf(BOSSES.values()); }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ConstellationClient.loc().onHypixel()) {
            if (!BOSSES.isEmpty()) reset();
            return;
        }
        long tick = mc.level.getGameTime();
        if (tick == lastTick) return;
        lastTick = tick;
        INVALIDATED.removeIf(id -> mc.level.getEntity(id) == null);

        for (Boss boss : new ArrayList<>(BOSSES.values())) {
            Entity entity = boss.entity();
            if (entity.isRemoved() || mc.level.getEntity(entity.getId()) != entity) {
                if (entity instanceof LivingEntity living && living.isDeadOrDying()) completeDeath(boss);
                else BOSSES.remove(entity.getId());
            } else if (entity instanceof LivingEntity living && living.isDeadOrDying()) {
                completeDeath(boss);
            }
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName()) continue;
            String name = ChatFormatting.stripFormatting(stand.getName().getString());
            if (name == null) continue;
            Matcher matcher = BOSS_NAME.matcher(name);
            if (!matcher.find()) continue;
            Type type = Type.fromName(matcher.group(1));
            int tier = specialTier(matcher.group(1), matcher.group(2));
            if (type == null || tier < 1 || tier > type.maxTier()) continue;
            Entity bossEntity = resolveEntity(stand, type);
            if (bossEntity == null || stand.getId() != bossEntity.getId() + 1) continue;
            OwnerLink owner = resolveOwner(bossEntity);
            if (!(bossEntity instanceof LivingEntity) || bossEntity instanceof ArmorStand || owner == null
                || BOSSES.containsKey(bossEntity.getId()) || INVALIDATED.contains(bossEntity.getId()) || !bossEntity.isAlive()) continue;
            Boss boss = new Boss(bossEntity, type, tier, owner.name(), matcher.group(1), stand.getId(), owner.standId(),
                System.nanoTime(), bossEntity.tickCount, serverTicks);
            BOSSES.put(bossEntity.getId(), boss);
            for (Listener listener : LISTENERS) listener.onSpawn(boss);
        }
    }

    private static Entity resolveEntity(ArmorStand stand, Type type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity exact = mc.level.getEntity(stand.getId() - 1);
        if (type.matches(exact) && exact.distanceToSqr(stand) <= 9) return exact;
        AABB box = stand.getBoundingBox().inflate(1.0, 2.0, 1.0);
        return mc.level.getEntitiesOfClass(LivingEntity.class, box, type::matches)
            .stream().min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(stand))).orElse(null);
    }

    private record OwnerLink(String name, int standId) {}

    private static OwnerLink resolveOwner(Entity boss) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || boss == null) return null;
        Entity exact = mc.level.getEntity(boss.getId() + 3);
        String owner = owner(exact);
        return owner == null ? null : new OwnerLink(owner, exact.getId());
    }

    private static String owner(Entity entity) {
        if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName()) return null;
        String name = ChatFormatting.stripFormatting(stand.getName().getString());
        if (name == null) return null;
        Matcher matcher = OWNER.matcher(name);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int specialTier(String name, String roman) {
        if (name.equals("Atoned Horror") || name.equals("Conjoined Brood")) return 5;
        if (name.contains("Bloodfiend") && roman == null) return 5;
        return roman(roman);
    }

    private static int roman(String value) {
        if (value == null || value.isBlank()) return 1;
        int total = 0, previous = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            int current = switch (value.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000; default -> 0; };
            total += current < previous ? -current : current;
            previous = current;
        }
        return total;
    }

    private static void clearOwner(String owner) {
        BOSSES.entrySet().removeIf(entry -> {
            if (!entry.getValue().owner().equalsIgnoreCase(owner)) return false;
            INVALIDATED.add(entry.getKey());
            return true;
        });
        for (Listener listener : LISTENERS) listener.onOwnerReset(owner);
    }

    public static void onDeathEvent(Entity entity) {
        Boss boss = BOSSES.get(entity.getId());
        if (boss != null) completeDeath(boss);
    }

    public static void onServerTick() { serverTicks++; }
    public static long serverTicks() { return serverTicks; }

    private static void completeDeath(Boss boss) {
        if (BOSSES.remove(boss.entity().getId()) == null) return;
        INVALIDATED.add(boss.entity().getId());
        int age = Math.max(0, boss.entity().tickCount - boss.spawnedAtTick());
        for (Listener listener : LISTENERS) listener.onDeath(boss, boss.secondsAlive(), age);
    }

    public static void reset() {
        BOSSES.clear();
        INVALIDATED.clear();
        lastTick = Long.MIN_VALUE;
        serverTicks = 0;
        for (Listener listener : LISTENERS) listener.onReset();
    }

    public static Type parseType(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "rev", "revenant", "zombie" -> Type.REV;
            case "tara", "tarantula", "spider" -> Type.TARA;
            case "sven", "wolf" -> Type.SVEN;
            case "void", "voidgloom", "eman", "enderman" -> Type.VOID;
            case "blaze", "inferno" -> Type.BLAZE;
            case "vamp", "vampire", "bloodfiend" -> Type.VAMP;
            default -> null;
        };
    }
}
