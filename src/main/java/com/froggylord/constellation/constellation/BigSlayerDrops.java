package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ported from Athen (BSD-3-Clause): modules/impl/slayer/BigSlayerDrops.kt
// item ids, rune textures and enchant keys ported from Athen (BSD-3-Clause): api/slayers/enums/drop/impl/*Drops.kt
public final class BigSlayerDrops {
    private record Death(Vec3 position, SlayerState.Type type, String owner, long expiresAtNanos) {}
    private record Cached(ItemStack stack, SlayerStatistics.Drop drop) {}

    private static final List<Death> DEATHS = new ArrayList<>();
    private static final Map<Integer, Cached> CACHE = new HashMap<>();
    private static final Map<String, SlayerStatistics.Drop> IDS = new HashMap<>();
    private static final Map<String, SlayerStatistics.Drop> TEXTURES = new HashMap<>();
    private static final Map<String, SlayerStatistics.Drop> ENCHANTS = new HashMap<>();
    private static PerseusConfig cfg;
    private static ClientLevel lastLevel;
    private static boolean initialized;

    // the enum name is the skyblock id for every ITEM_ID entry in Athen's tables
    private static final String ITEM_DATA = """
        REV|REVENANT_FLESH|Revenant flesh
        REV|FOUL_FLESH|Foul flesh
        REV|PESTILENCE_RUNE|Pestilence I
        REV|UNDEAD_CATALYST|Undead catalyst
        REV|ENCHANTMENT_SMITE|Enchanted Book (Smite VI)
        REV|BEHEADED_HORROR|Beheaded horror
        REV|REVENANT_CATALYST|Revenant catalyst
        REV|SNAKE_RUNE|Snake rune I
        REV|FESTERING_MAGGOT|Festering maggot
        REV|REVENANT_VISCERA|Revenant viscera
        REV|SCYTHE_BLADE|Scythe blade
        REV|SEVERED_HAND|Severed hand
        REV|SHARD_OF_THE_SHREDDED|Shredded sinew
        REV|WARDEN_HEART|Warden heart
        REV|DYE_MATCHA|Matcha dye
        TARA|TARANTULA_WEB|Tarantula web
        TARA|TOXIC_ARROW_POISON|Toxic arrow poison
        TARA|BITE_RUNE|Bite rune I
        TARA|DARKNESS_WITHIN_RUNE|Darkness within rune I
        TARA|SPIDER_CATALYST|Spider catalyst
        TARA|TARANTULA_SILK|Tarantula silk
        TARA|ENCHANTMENT_BANE_OF_ARTHROPODS|Enchanted Book (Bane of Arthropods VI)
        TARA|TARANTULA_CATALYST|Tarantula catalyst
        TARA|FLY_SWATTER|Fly swatter
        TARA|VIAL_OF_VENOM|Vial of venom
        TARA|TARANTULA_TALISMAN|Tarantula talisman
        TARA|DIGESTED_MOSQUITO|Digested mosquito
        TARA|SHRIVELED_WASP|Shriveled wasp
        TARA|ENSNARED_SNAIL|Ensnared snail
        TARA|PRIMORDIAL_EYE|Primordial eye
        TARA|DYE_BRICK_RED|Brick red dye
        SVEN|WOLF_TOOTH|Wolf tooth
        SVEN|HAMSTER_WHEEL|Hamster wheel
        SVEN|SPIRIT_RUNE|Spirit rune I
        SVEN|ENCHANTMENT_CRITICAL|Enchanted Book (Critical VI)
        SVEN|FURBALL|Furball
        SVEN|RED_CLAW_EGG|Red claw egg
        SVEN|COUTURE_RUNE|Couture rune I
        SVEN|GRIZZLY_BAIT|Grizzly salmon
        SVEN|OVERFLUX_CAPACITOR|Overflux capacitor
        SVEN|DYE_CELESTE|Celeste dye
        VOID|NULL_SPHERE|Null sphere
        VOID|TWILIGHT_ARROW_POISON|Twilight arrow poison
        VOID|ENDERSNAKE_RUNE|Endersnake rune I
        VOID|SUMMONING_EYE|Summoning eye
        VOID|ENCHANTMENT_MANA_STEAL|Enchanted Book (Mana Steal I)
        VOID|TRANSMISSION_TUNER|Transmission tuner
        VOID|NULL_ATOM|Null atom
        VOID|HAZMAT_ENDERMAN|Hazmat enderman
        VOID|POCKET_ESPRESSO_MACHINE|Pocket espresso machine
        VOID|ENCHANTMENT_SMARTY_PANTS|Enchanted Book (Smarty Pants I)
        VOID|END_RUNE|End rune I
        VOID|HANDY_BLOOD_CHALICE|Handy blood chalice
        VOID|SINFUL_DICE|Sinful dice
        VOID|EXCEEDINGLY_RARE_ENDER_ARTIFACT_UPGRADE|Exceedingly rare ender artifact upgrade
        VOID|PET_SKIN_ENDERMAN_SLAYER|Void conqueror enderman skin
        VOID|ETHERWARP_MERGER|Etherwarp merger
        VOID|JUDGEMENT_CORE|Judgement core
        VOID|ENCHANT_RUNE|Enchant rune I
        VOID|ENDSTONE_IDOL|Endstone idol
        VOID|DYE_BYZANTIUM|Byzantium dye
        BLAZE|DERELICT_ASHE|Derelict ashe
        BLAZE|ENCHANTED_BLAZE_POWDER|Enchanted blaze powder
        BLAZE|LAVATEARS_RUNE|Lavatears rune I
        BLAZE|WISPS_ICE_FLAVORED_WATER|Wisp's Ice-Flavored Water I Splash Potion
        BLAZE|ARROW_BUNDLE_MAGMA|Bundle of magma arrows
        BLAZE|MANA_DISINTEGRATOR|Mana disintegrator
        BLAZE|SCORCHED_BOOKS|Scorched books
        BLAZE|KELVIN_INVERTER|Kelvin inverter
        BLAZE|BLAZE_ROD_DISTILLATE|Blaze rod distillate
        BLAZE|GLOWSTONE_DUST_DISTILLATE|Glowstone distillate
        BLAZE|MAGMA_CREAM_DISTILLATE|Magma cream distillate
        BLAZE|NETHER_STALK_DISTILLATE|Nether wart distillate
        BLAZE|CRUDE_GABAGOOL_DISTILLATE|Gabagool distillate
        BLAZE|SCORCHED_POWER_CRYSTAL|Scorched power crystal
        BLAZE|ARCHFIEND_DICE|Archfiend dice
        BLAZE|ENCHANTMENT_FIRE_ASPECT|Enchanted Book (Fire Aspect VI)
        BLAZE|FIERY_BURST_RUNE|Fiery burst rune I
        BLAZE|FLAWED_OPAL_GEM|Flawed opal gemstone
        BLAZE|ENCHANTMENT_ULTIMATE_REITERATE|Enchanted Book (Duplex I)
        BLAZE|HIGH_CLASS_ARCHFIEND_DICE|High class archfiend dice
        BLAZE|WILSONS_ENGINEERING_PLANS|Wilson's engineering plans
        BLAZE|SUBZERO_INVERTER|Subzero inverter
        BLAZE|FLAME_DYE|Flame dye
        VAMP|COVEN_SEAL|Coven seal
        VAMP|ENCHANTED_BOOK_BUNDLE_QUANTUM|Bundle of quantum book
        VAMP|SOULTWIST_RUNE|Soultwist rune
        VAMP|BUBBA_BLISTER|Bubba blister
        VAMP|CHOCOLATE_CHIP|Chocolate chip
        VAMP|GUARDIAN_LUCKY_BLOCK|Guardian lucky block
        VAMP|MCGRUBBER_BURGER|McGrubber burger
        VAMP|UNFANGED_VAMPIRE_PART|Unfanged vampire part
        VAMP|ENCHANTMENT_ULTIMATE_THE_ONE|Bundle of The One book
        VAMP|DYE_SANGRIA|Sangria dye
        """;

    static {
        for (String row : ITEM_DATA.lines().toList()) {
            String[] parts = row.strip().split("\\|", 3);
            if (parts.length != 3) continue;
            SlayerState.Type type = SlayerState.Type.valueOf(parts[0]);
            SlayerStatistics.Drop drop = SlayerStatistics.findDrop(type, parts[2]);
            if (drop != null) IDS.put(parts[1], drop);
        }
        texture("9f73f7f1d38786eccbd7bf63cf6a83ae23fc77b73d4d0695cec86b5f041ee3ab", SlayerState.Type.BLAZE, "Lavatears rune I");
        texture("1d7ff55eebcdf71619f34ad3f29913445fa34d06bf955cf0d168602cd0669e27", SlayerState.Type.BLAZE, "Fiery burst rune I");
        texture("e15d95ee0411d8b3e6f411eb89e2020f681e81820d32a404fcd7051f832f64ec", SlayerState.Type.REV, "Pestilence I");
        texture("a7144027d47b9b505decd68c768fc10c4270e47e444cbb12eb7c345117e5c790", SlayerState.Type.REV, "Snake rune I");
        texture("f3c524633eb0810e24075e6148ceeab8a486ae9068d3f3d6d046b40c6d52039a", SlayerState.Type.SVEN, "Spirit rune I");
        texture("3b73961d48f4d32c2455c8f579b7e76b3fe8ffd8552d8c3e5723d8afc4c086d1", SlayerState.Type.SVEN, "Couture rune I");
        texture("47a65a2061208f09336541cfd5361f1157de502b4a86f74de31164f920264079", SlayerState.Type.TARA, "Bite rune I");
        texture("ccc05ebee07e99f3aa0c20f47d6f9392de15d362430f92763bdd5b6a128b2063", SlayerState.Type.TARA, "Darkness within rune I");
        texture("1ececaa48738207ac9bd7e549a8e8c076d9c2f962480532d83ea9b0dc9bad15f", SlayerState.Type.VAMP, "Soultwist rune");
        texture("81776355adc437b91c144f7691d54822c31ef6a66ca4b044f34de60b74b017e7", SlayerState.Type.VOID, "Endersnake rune I");
        texture("18a4bb3e2cffeb2e09ed44beb0a41b606f3a154f1d5077a5bebec24d1688e3ae", SlayerState.Type.VOID, "End rune I");
        texture("b46c424777d438fc42f4ac31c42f5294ecc046c438479627076747da6f456eaa", SlayerState.Type.VOID, "Enchant rune I");
        enchant("fire_aspect:3", SlayerState.Type.BLAZE, "Enchanted Book (Fire Aspect VI)");
        enchant("duplex:1", SlayerState.Type.BLAZE, "Enchanted Book (Duplex I)");
        enchant("smite:6", SlayerState.Type.REV, "Enchanted Book (Smite VI)");
        enchant("critical:6", SlayerState.Type.SVEN, "Enchanted Book (Critical VI)");
        enchant("bane_of_arthrpods:6", SlayerState.Type.TARA, "Enchanted Book (Bane of Arthropods VI)");
        enchant("bane_of_arthropods:6", SlayerState.Type.TARA, "Enchanted Book (Bane of Arthropods VI)");
        enchant("mana_steal:1", SlayerState.Type.VOID, "Enchanted Book (Mana Steal I)");
        enchant("smarty_pants:1", SlayerState.Type.VOID, "Enchanted Book (Smarty Pants I)");
    }

    private BigSlayerDrops() {}

    public static void init(PerseusConfig config) {
        cfg = config;
        normalize();
        if (initialized) return;
        initialized = true;
        SlayerState.listen(new SlayerState.Listener() {
            @Override public void onSpawn(SlayerState.Boss boss) {}
            @Override public void onDeath(SlayerState.Boss boss, double seconds, int clientTicks) {
                if (!enabled() || !ensureContext() || !typeEnabled(boss.type()) || cfg.bigSlayerDropsOnlyMine && !owned(boss)) return;
                long duration = Math.clamp(cfg.bigSlayerDropsDurationSeconds, 5, 60) * 1_000_000_000L;
                DEATHS.add(new Death(boss.entity().position(), boss.type(), boss.owner(), System.nanoTime() + duration));
            }
            @Override public void onReset() { clear(); }
        });
    }

    public static float scale(ItemEntity entity) {
        if (!enabled() || !ensureContext() || entity == null || entity.isRemoved()) return 1.0f;
        cleanup();
        SlayerStatistics.Drop drop = resolveCached(entity);
        if (drop == null || !typeEnabled(drop.type()) || !dropEnabled(drop)) return 1.0f;
        double range = Math.clamp(cfg.bigSlayerDropsRangeMultiplier, .5, 5.0);
        Vec3 position = entity.position();
        for (Death death : DEATHS) {
            if (cfg.bigSlayerDropsMatchDeathType && death.type() != drop.type()) continue;
            if (Math.abs(death.position().x - position.x) > 5 * range) continue;
            if (Math.abs(death.position().y - position.y) > 3 * range) continue;
            if (Math.abs(death.position().z - position.z) > 5 * range) continue;
            return Math.clamp(cfg.bigSlayerDropsScale, 1.0f, 10.0f);
        }
        return 1.0f;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bigslayerdrops")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scale")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Double>argument("value", DoubleArgumentType.doubleArg(1, 10))
                    .executes(c -> scaleCommand(DoubleArgumentType.getDouble(c, "value")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Double>argument("multiplier", DoubleArgumentType.doubleArg(.5, 5))
                    .executes(c -> rangeCommand(DoubleArgumentType.getDouble(c, "multiplier")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(5, 60))
                    .executes(c -> durationCommand(IntegerArgumentType.getInteger(c, "seconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("type")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> typeCommand(StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "state"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("drop")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("value", StringArgumentType.greedyString())
                        .executes(c -> dropCommand(StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("all")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> allCommand(StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "state")))))));
    }

    private static SlayerStatistics.Drop resolveCached(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        Cached cached = CACHE.get(entity.getId());
        if (cached != null && cached.stack() == stack) return cached.drop();
        SlayerStatistics.Drop drop = resolve(stack);
        if (drop == null) CACHE.remove(entity.getId());
        else CACHE.put(entity.getId(), new Cached(stack, drop));
        return drop;
    }

    private static SlayerStatistics.Drop resolve(ItemStack stack) {
        CompoundTag extra = extra(stack);
        String id = extra.getStringOr("id", "").toUpperCase(Locale.ROOT);
        if (id.equals("RUNE")) {
            String texture = texture(stack);
            if (!texture.isEmpty()) return TEXTURES.get(hash(texture));
        }
        if (id.equals("ENCHANTED_BOOK")) {
            SlayerStatistics.Drop drop = enchant(extra.getCompoundOrEmpty("enchantments"));
            if (drop == null) drop = enchant(extra.getCompoundOrEmpty("ultimate_enchantments"));
            if (drop != null) return drop;
        }
        return IDS.get(id);
    }

    private static SlayerStatistics.Drop enchant(CompoundTag values) {
        for (String key : values.keySet()) {
            int level = values.getIntOr(key, 0);
            SlayerStatistics.Drop drop = ENCHANTS.get(key.toLowerCase(Locale.ROOT) + ":" + level);
            if (drop != null) return drop;
        }
        return null;
    }

    private static CompoundTag extra(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes");
    }

    private static String texture(ItemStack stack) {
        var profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return "";
        for (Property property : profile.partialProfile().properties().get("textures")) if (property.value() != null) return property.value();
        return "";
    }

    private static void cleanup() {
        long now = System.nanoTime();
        DEATHS.removeIf(death -> death.expiresAtNanos() <= now);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) CACHE.clear();
        else CACHE.entrySet().removeIf(entry -> !(mc.level.getEntity(entry.getKey()) instanceof ItemEntity));
    }

    private static void clear() { DEATHS.clear(); CACHE.clear(); }
    private static boolean ensureContext() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) { clear(); lastLevel = level; }
        if (level == null || !ConstellationClient.loc().onHypixel()) { clear(); return false; }
        return true;
    }
    private static boolean enabled() { return cfg != null && cfg.enabled && cfg.bigSlayerDrops; }
    private static boolean owned(SlayerState.Boss boss) { Minecraft mc = Minecraft.getInstance(); return mc.player != null && boss.owner().equalsIgnoreCase(mc.player.getName().getString()); }
    private static boolean dropEnabled(SlayerStatistics.Drop drop) { return cfg.bigSlayerDropFilters.getOrDefault(filterKey(drop), true); }
    private static String filterKey(SlayerStatistics.Drop drop) { return drop.type().name().toLowerCase(Locale.ROOT) + ":" + normal(drop.name()); }
    private static boolean typeEnabled(SlayerState.Type type) { return switch (type) { case REV -> cfg.bigSlayerDropsRevenant; case TARA -> cfg.bigSlayerDropsTarantula; case SVEN -> cfg.bigSlayerDropsSven; case VOID -> cfg.bigSlayerDropsVoidgloom; case BLAZE -> cfg.bigSlayerDropsInferno; case VAMP -> cfg.bigSlayerDropsRiftstalker; }; }

    private static void normalize() {
        cfg.bigSlayerDropsScale = Math.clamp(cfg.bigSlayerDropsScale, 1, 10);
        cfg.bigSlayerDropsRangeMultiplier = Math.clamp(cfg.bigSlayerDropsRangeMultiplier, .5, 5);
        cfg.bigSlayerDropsDurationSeconds = Math.clamp(cfg.bigSlayerDropsDurationSeconds, 5, 60);
        Map<String, Boolean> safe = new LinkedHashMap<>();
        if (cfg.bigSlayerDropFilters != null) for (SlayerStatistics.Drop drop : SlayerStatistics.drops()) {
            Boolean value = cfg.bigSlayerDropFilters.get(filterKey(drop));
            if (value != null) safe.put(filterKey(drop), value);
        }
        cfg.bigSlayerDropFilters = safe;
    }

    private static int status() {
        local("§eBig Slayer Drops: " + (cfg.bigSlayerDrops ? "on" : "off") + ", scale " + cfg.bigSlayerDropsScale + ", range x" + cfg.bigSlayerDropsRangeMultiplier + ", " + cfg.bigSlayerDropsDurationSeconds + "s.");
        for (SlayerState.Type type : SlayerState.Type.values()) {
            long enabled = SlayerStatistics.drops().stream().filter(drop -> drop.type() == type && dropEnabled(drop)).count();
            long total = SlayerStatistics.drops().stream().filter(drop -> drop.type() == type).count();
            local(" §8- §f" + label(type) + ": " + (typeEnabled(type) ? "§aon" : "§coff") + " §7(" + enabled + "/" + total + " drops)");
        }
        return 1;
    }

    private static int scaleCommand(double value) { cfg.bigSlayerDropsScale = (float) value; save("Scale set to " + value + "."); return 1; }
    private static int rangeCommand(double value) { cfg.bigSlayerDropsRangeMultiplier = value; save("Range multiplier set to " + value + "."); return 1; }
    private static int durationCommand(int value) { cfg.bigSlayerDropsDurationSeconds = value; save("Duration set to " + value + " seconds."); return 1; }
    private static int typeCommand(String input, String state) { SlayerState.Type type = SlayerState.parseType(input); Boolean value = state(state); if (type == null || value == null) { local("§cUse /bigslayerdrops type <type> <on|off>."); return 0; } setType(type,value); save(label(type) + " scaling " + (value ? "enabled." : "disabled.")); return 1; }
    private static int allCommand(String input, String state) { SlayerState.Type type = SlayerState.parseType(input); Boolean value = state(state); if (type == null || value == null) { local("§cUse /bigslayerdrops all <type> <on|off>."); return 0; } for (SlayerStatistics.Drop drop : SlayerStatistics.drops()) if (drop.type() == type) cfg.bigSlayerDropFilters.put(filterKey(drop), value); save("All " + label(type) + " drops " + (value ? "enabled." : "disabled.")); return 1; }
    private static int dropCommand(String input, String value) {
        SlayerState.Type type = SlayerState.parseType(input);
        if (type == null) { local("§cUnknown Slayer type."); return 0; }
        int split = value.lastIndexOf(' ');
        if (split < 1) { local("§cUse /bigslayerdrops drop <type> <drop name> <on|off>."); return 0; }
        Boolean state = state(value.substring(split + 1));
        SlayerStatistics.Drop drop = SlayerStatistics.findDrop(type, value.substring(0, split));
        if (state == null || drop == null) { local("§cUnknown drop or state. Use /slayerdrops list <type>."); return 0; }
        cfg.bigSlayerDropFilters.put(filterKey(drop), state); save(drop.name() + " scaling " + (state ? "enabled." : "disabled.")); return 1;
    }

    private static void setType(SlayerState.Type type, boolean value) { switch (type) { case REV -> cfg.bigSlayerDropsRevenant=value; case TARA -> cfg.bigSlayerDropsTarantula=value; case SVEN -> cfg.bigSlayerDropsSven=value; case VOID -> cfg.bigSlayerDropsVoidgloom=value; case BLAZE -> cfg.bigSlayerDropsInferno=value; case VAMP -> cfg.bigSlayerDropsRiftstalker=value; } }
    private static Boolean state(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "on","true","yes","1","enable","enabled" -> true; case "off","false","no","0","disable","disabled" -> false; default -> null; }; }
    private static void texture(String hash, SlayerState.Type type, String name) { SlayerStatistics.Drop drop = SlayerStatistics.findDrop(type,name); if (drop != null) TEXTURES.put(hash,drop); }
    private static void enchant(String key, SlayerState.Type type, String name) { SlayerStatistics.Drop drop = SlayerStatistics.findDrop(type,name); if (drop != null) ENCHANTS.put(key,drop); }
    private static String hash(String value) { try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); return java.util.HexFormat.of().formatHex(bytes); } catch (Exception ignored) { return ""; } }
    private static String normal(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""); }
    private static String label(SlayerState.Type type) { return switch (type) { case REV -> "Revenant"; case TARA -> "Tarantula"; case SVEN -> "Sven"; case VOID -> "Voidgloom"; case BLAZE -> "Inferno"; case VAMP -> "Riftstalker"; }; }
    private static void save(String text) { ConstellationClient.saveConfig(); local("§a" + text); }
    private static void local(String text) { Minecraft mc=Minecraft.getInstance(); if (mc.player!=null) mc.player.sendSystemMessage(Component.literal("§5Slayer §8> §f"+text)); }
}
