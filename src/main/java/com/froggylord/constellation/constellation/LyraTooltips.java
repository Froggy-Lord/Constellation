package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.AuctionApi;
import com.froggylord.constellation.api.BazaarApi;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.LyraConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// pipeline and price presentation ported from Skyblocker (LGPL-3.0-or-later):
// skyblock/item/tooltip/TooltipManager.java and adders/{BazaarPriceTooltip,LBinTooltip,NpcPriceTooltip}.java
// market IDs, stack quantities, and metadata ported from NoFrills (GPL-3.0): misc/Utils.java and features/general/{PriceTooltips,InfoTooltips}.java
public final class LyraTooltips {
    private static final ZoneId SKYBLOCK_ZONE = ZoneId.of("America/Toronto");
    private static final DateTimeFormatter LEGACY_TWELVE_HOUR = DateTimeFormatter.ofPattern("M/d/yy h:mm a", Locale.US);
    private static final DateTimeFormatter LEGACY_TWENTY_FOUR_HOUR = DateTimeFormatter.ofPattern("d/M/yy HH:mm", Locale.US);
    private static LyraConfig cfg;
    private static boolean initialized;

    private LyraTooltips() {}

    public static void init(LyraConfig config) {
        cfg = config;
        normalize();
        if (initialized) return;
        initialized = true;
        ItemTooltipCallback.EVENT.register((stack, context, flags, lines) -> append(stack, lines));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("itemtooltips")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("decimals")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("value", IntegerArgumentType.integer(0, 2))
                    .executes(c -> decimals(IntegerArgumentType.getInteger(c, "value")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ageprecision")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("value", IntegerArgumentType.integer(1, 4))
                    .executes(c -> agePrecision(IntegerArgumentType.getInteger(c, "value")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dateformat")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("value", StringArgumentType.word())
                    .executes(c -> dateFormat(StringArgumentType.getString(c, "value")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("timezone")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("value", StringArgumentType.word())
                    .executes(c -> timeZone(StringArgumentType.getString(c, "value")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("customdate")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("pattern", StringArgumentType.greedyString())
                    .executes(c -> customDate(StringArgumentType.getString(c, "pattern")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static void append(ItemStack stack, List<Component> lines) {
        if (cfg == null || !cfg.enabled || stack == null || stack.isEmpty()) return;
        boolean hypixel = ConstellationClient.loc().onHypixel();
        boolean local = Minecraft.getInstance().hasSingleplayerServer() && cfg.tooltipCreationLocalWorlds;
        if (!hypixel && !local) return;
        CompoundTag extra = extra(stack);
        String rawId = extra.getStringOr("id", "");
        if (rawId.isBlank()) return;
        if (!hypixel) {
            addCreationTime(lines, extra);
            return;
        }
        String marketId = marketId(stack, extra, rawId);
        int quantity = quantity(stack);
        if (cfg.tooltipPrices) addPrices(lines, rawId, marketId, quantity);
        if (cfg.tooltipItemInfo) addInfo(lines, stack, extra, rawId, marketId);
        addCreationTime(lines, extra);
    }

    private static void addPrices(List<Component> lines, String rawId, String marketId, int quantity) {
        BazaarApi.ensureFresh();
        double[] bazaar = BazaarApi.get(marketId);
        boolean marketKnown = bazaar != null;
        if (cfg.tooltipBazaar && bazaar != null) {
            marketKnown = true;
            if (bazaar[0] > 0) lines.add(priceLine("Bazaar Buy", bazaar[0], quantity));
            if (bazaar[1] > 0) lines.add(priceLine("Bazaar Sell", bazaar[1], quantity));
        }
        if (cfg.tooltipLowestBin && bazaar == null) {
            Double lbin = AuctionApi.getLbin(marketId);
            if (lbin != null) {
                marketKnown = true;
                if (lbin > 0) lines.add(priceLine("Lowest BIN", lbin, quantity));
            } else {
                AuctionApi.prefetch(marketId);
            }
        }
        if (cfg.tooltipNpcPrice) {
            double npc = PriceProvider.npcValue(rawId);
            if (npc > 0) lines.add(priceLine("NPC Sell", npc, quantity));
        }
        if (!marketKnown && cfg.tooltipPriceLoading && cfg.tooltipLowestBin) {
            if (AuctionApi.isFetching(marketId)) lines.add(Component.literal("Price Data: §7Loading..."));
            else if (AuctionApi.isCoolingDown(marketId)) lines.add(Component.literal("Price Data: §cTemporarily unavailable"));
        }
    }

    private static void addInfo(List<Component> lines, ItemStack stack, CompoundTag extra, String rawId, String marketId) {
        if (cfg.tooltipSkyblockId) lines.add(Component.literal("Item ID: §6" + marketId));
        if (cfg.tooltipItemQuality && extra.contains("baseStatBoostPercentage")) {
            int quality = extra.getIntOr("baseStatBoostPercentage", 0);
            int tier = extra.getIntOr("item_tier", -1);
            String color = quality == 50 ? "§c§l" : "§b";
            lines.add(Component.literal("Item Quality: " + color + quality + "/50"));
            if (tier >= 0) lines.add(Component.literal("Floor Tier: " + color + tier + " §7(" + floor(tier) + ")"));
        }
        if (cfg.tooltipReforge) {
            String modifier = extra.getStringOr("modifier", "");
            if (!modifier.isBlank()) lines.add(Component.literal("Reforge: §6" + title(modifier)));
        }
        if (cfg.tooltipHotPotato) {
            int potato = extra.getIntOr("hot_potato_count", 0);
            if (potato > 0) lines.add(Component.literal("Hot Potato Books: §6" + potato));
        }
        if (cfg.tooltipStars) {
            int stars = extra.getIntOr("upgrade_level", 0);
            if (stars > 0) lines.add(Component.literal("Upgrade Stars: §6" + stars));
        }
        if (cfg.tooltipRecomb && extra.getIntOr("rarity_upgrades", 0) > 0)
            lines.add(Component.literal("Recombobulated: §aYes"));
        if (cfg.tooltipAttributes) addAttributes(lines, extra.getCompoundOrEmpty("attributes"));
        if (cfg.trueHexDisplay) {
            DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
            if (dye != null) lines.add(Component.literal(String.format(Locale.ROOT, "Dye Color: §6#%06X", dye.rgb())));
        }
        if (cfg.museumDonationStatus && extra.contains("donated_museum"))
            lines.add(Component.literal("Museum: " + (extra.getByteOr("donated_museum", (byte) 0) != 0 ? "§aDonated" : "§cNot Donated")));
    }

    private static void addAttributes(List<Component> lines, CompoundTag attributes) {
        if (attributes.isEmpty()) return;
        List<String> values = new ArrayList<>();
        for (String key : attributes.keySet()) {
            int level = attributes.getIntOr(key, 0);
            if (level > 0) values.add(title(key) + " " + level);
        }
        if (!values.isEmpty()) lines.add(Component.literal("Attributes: §6" + String.join("§7, §6", values)));
    }

    private static Component priceLine(String label, double each, int quantity) {
        double total = each * Math.max(1, quantity);
        String value = coins(total);
        if (cfg.tooltipStackBreakdown && quantity > 1) value += " §8(" + quantity + "x " + coins(each) + ")";
        return Component.literal(label + ": §6" + value);
    }

    static int quantity(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) for (Component component : lore.lines()) {
            String line = plain(component).trim();
            Integer parsed = countAfter(line, "Stored:", "/");
            if (parsed == null) parsed = countAfter(line, "Compost Available:", "");
            if (parsed == null) parsed = countAfter(line, "Owned:", " ");
            if (parsed != null) return Math.max(1, parsed);
        }
        return Math.max(1, stack.getCount());
    }

    private static Integer countAfter(String line, String prefix, String endToken) {
        if (!line.startsWith(prefix)) return null;
        String value = line.substring(prefix.length()).trim();
        if (!endToken.isEmpty()) {
            int end = value.indexOf(endToken);
            if (end >= 0) value = value.substring(0, end).trim();
        }
        value = value.replace(",", "").replaceAll("[^0-9].*$", "");
        try { return value.isBlank() ? null : Integer.parseInt(value); } catch (NumberFormatException ignored) { return null; }
    }

    private static String marketId(ItemStack stack, CompoundTag extra, String id) {
        if (extra.getIntOr("baseStatBoostPercentage", 0) == 50) return id + "_MAX_BOOST_TIER_" + extra.getIntOr("item_tier", 0);
        if (id.equals("ENCHANTED_BOOK")) {
            CompoundTag enchants = extra.getCompoundOrEmpty("enchantments");
            if (enchants.keySet().size() == 1) {
                String enchant = enchants.keySet().iterator().next();
                return "ENCHANTMENT_" + enchant.toUpperCase(Locale.ROOT) + "_" + enchants.getIntOr(enchant, 0);
            }
        }
        if (id.equals("RUNE") || id.equals("UNIQUE_RUNE")) {
            CompoundTag runes = extra.getCompoundOrEmpty("runes");
            if (!runes.keySet().isEmpty()) {
                String rune = runes.keySet().iterator().next();
                return rune.toUpperCase(Locale.ROOT) + "_" + runes.getIntOr(rune, 0) + "_RUNE";
            }
        }
        if (id.equals("PET")) {
            try {
                JsonObject pet = JsonParser.parseString(extra.getStringOr("petInfo", "")).getAsJsonObject();
                return pet.get("type").getAsString() + "_PET_" + pet.get("tier").getAsString();
            } catch (Exception ignored) { return "UNKNOWN_PET"; }
        }
        return id;
    }

    public static String marketId(ItemStack stack) {
        CompoundTag extra = extra(stack);
        String id = extra.getStringOr("id", "");
        return id.isBlank() ? "" : marketId(stack, extra, id);
    }

    // ported from Devonian (GPL-3.0): features/misc/tooltip/ItemAge.kt
    private static void addCreationTime(List<Component> lines, CompoundTag extra) {
        if (!cfg.tooltipCreationTimestamp && !cfg.tooltipItemAge) return;
        if (!ConstellationClient.loc().onHypixel() && !(Minecraft.getInstance().hasSingleplayerServer() && cfg.tooltipCreationLocalWorlds)) return;
        ZonedDateTime created = creationTime(extra);
        if (created == null) return;
        var window = Minecraft.getInstance().getWindow();
        boolean shift = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
            || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (cfg.tooltipCreationTimestamp && (!cfg.tooltipCreationTimestampOnShift || shift))
            lines.add(Component.literal("Created: §8" + formatCreationTime(created)));
        if (cfg.tooltipItemAge && (!cfg.tooltipItemAgeOnShift || shift))
            lines.add(Component.literal("Item age: §8" + formatAge(created.toInstant(), Instant.now())));
    }

    static ZonedDateTime creationTime(CompoundTag extra) {
        long millis = extra.getLongOr("timestamp", 0L);
        if (validTimestamp(millis)) return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), displayZone());
        String value = extra.getStringOr("timestamp", "").trim();
        if (value.isBlank()) return null;
        try {
            long parsed = Long.parseLong(value);
            return validTimestamp(parsed) ? ZonedDateTime.ofInstant(Instant.ofEpochMilli(parsed), displayZone()) : null;
        }
        catch (NumberFormatException ignored) {
            try { return validInstant(Instant.parse(value)); }
            catch (DateTimeParseException ignoredAgain) {
                try {
                    DateTimeFormatter parser = value.endsWith("M") ? LEGACY_TWELVE_HOUR : LEGACY_TWENTY_FOUR_HOUR;
                    return validInstant(ZonedDateTime.of(LocalDateTime.parse(value, parser), SKYBLOCK_ZONE).toInstant());
                } catch (DateTimeParseException ignoredLegacy) { return null; }
            }
        }
    }

    private static String formatCreationTime(ZonedDateTime created) {
        String pattern = switch (cfg.tooltipCreationDateFormat) {
            case "EUROPEAN" -> cfg.tooltipCreationIncludeTime ? "d MMM yyyy HH:mm" : "d MMM yyyy";
            case "ISO" -> cfg.tooltipCreationIncludeTime ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd";
            case "CUSTOM" -> cfg.tooltipCreationCustomPattern;
            default -> cfg.tooltipCreationIncludeTime ? "MMM d, yyyy h:mm a" : "MMM d, yyyy";
        };
        String value;
        try { value = created.withZoneSameInstant(displayZone()).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)); }
        catch (IllegalArgumentException ignored) { value = created.withZoneSameInstant(displayZone()).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)); }
        return cfg.tooltipCreationIncludeZone ? value + " " + created.withZoneSameInstant(displayZone()).format(DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH)) : value;
    }

    static String formatAge(Instant created, Instant now) {
        long seconds = java.time.Duration.between(created, now).getSeconds();
        boolean future = seconds < 0;
        long remaining = seconds == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(seconds);
        if (remaining < 60 && !cfg.tooltipAgeShowSeconds) return future ? "in <1 minute" : "<1 minute";
        long[] units = {31_536_000L, 2_592_000L, 86_400L, 3_600L, 60L, 1L};
        String[] full = {"year", "month", "day", "hour", "minute", "second"};
        String[] compact = {"y", "mo", "d", "h", "m", "s"};
        List<String> parts = new ArrayList<>();
        int precision = Math.clamp(cfg.tooltipAgePrecision, 1, 4);
        for (int i = 0; i < units.length && parts.size() < precision; i++) {
            if (i == units.length - 1 && !cfg.tooltipAgeShowSeconds) continue;
            long amount = remaining / units[i];
            if (amount <= 0) continue;
            remaining %= units[i];
            parts.add(cfg.tooltipAgeCompact ? amount + compact[i] : amount + " " + full[i] + (amount == 1 ? "" : "s"));
        }
        String value = parts.isEmpty() ? "<1 minute" : String.join(cfg.tooltipAgeCompact ? " " : ", ", parts);
        return future ? "in " + value : value;
    }

    private static ZoneId displayZone() {
        if (cfg == null) return ZoneId.systemDefault();
        return switch (cfg.tooltipCreationTimeZone) {
            case "UTC" -> ZoneId.of("UTC");
            case "SKYBLOCK" -> SKYBLOCK_ZONE;
            default -> ZoneId.systemDefault();
        };
    }
    private static boolean validTimestamp(long millis) {
        return millis > 0 && millis <= System.currentTimeMillis() + 366L * 86_400_000L;
    }
    private static ZonedDateTime validInstant(Instant instant) {
        try { return validTimestamp(instant.toEpochMilli()) ? ZonedDateTime.ofInstant(instant, displayZone()) : null; }
        catch (ArithmeticException ignored) { return null; }
    }

    private static String floor(int tier) { return switch (tier) { case 0 -> "E"; case 1 -> "F1"; case 2 -> "F2"; case 3 -> "F3"; case 4 -> "F4/M1"; case 5 -> "F5/M2"; case 6 -> "F6/M3"; case 7 -> "F7/M4"; case 8 -> "M5"; case 9 -> "M6"; case 10 -> "M7"; default -> "Unknown"; }; }
    private static String title(String value) { String[] parts = value.toLowerCase(Locale.ROOT).split("_"); for (int i=0;i<parts.length;i++) if (!parts[i].isBlank()) parts[i]=Character.toUpperCase(parts[i].charAt(0))+parts[i].substring(1); return String.join(" ", parts); }
    private static String plain(Component value) { String text = ChatFormatting.stripFormatting(value.getString()); return text == null ? value.getString() : text; }
    private static CompoundTag extra(ItemStack stack) { CustomData data = stack.get(DataComponents.CUSTOM_DATA); return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes"); }
    private static String coins(double value) { return String.format(Locale.ROOT, "%,." + cfg.tooltipPriceDecimals + "f", value); }
    private static int status() {
        local("§eItem tooltips: prices " + on(cfg.tooltipPrices) + ", Bazaar " + on(cfg.tooltipBazaar) + ", LBIN "
            + on(cfg.tooltipLowestBin) + ", NPC " + on(cfg.tooltipNpcPrice) + ", info " + on(cfg.tooltipItemInfo) + ".");
        local("§7Stack breakdown " + on(cfg.tooltipStackBreakdown) + ", loading line " + on(cfg.tooltipPriceLoading)
            + ", decimals " + cfg.tooltipPriceDecimals + ".");
        local("§7Created " + mode(cfg.tooltipCreationTimestamp, cfg.tooltipCreationTimestampOnShift) + ", age "
            + mode(cfg.tooltipItemAge, cfg.tooltipItemAgeOnShift) + ", format §f" + cfg.tooltipCreationDateFormat
            + "§7, zone §f" + cfg.tooltipCreationTimeZone + "§7, precision §f" + cfg.tooltipAgePrecision + "§7.");
        return 1;
    }

    private static int decimals(int value) { cfg.tooltipPriceDecimals = Math.clamp(value, 0, 2); return save("Tooltip price precision updated."); }
    private static int agePrecision(int value) { cfg.tooltipAgePrecision = Math.clamp(value, 1, 4); return save("Item age precision updated."); }
    private static int dateFormat(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!List.of("AMERICAN", "EUROPEAN", "ISO", "CUSTOM").contains(normalized)) { local("§cFormat must be american, european, iso, or custom."); return 0; }
        cfg.tooltipCreationDateFormat = normalized;
        return save("Creation date format updated.");
    }
    private static int timeZone(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!List.of("LOCAL", "SKYBLOCK", "UTC").contains(normalized)) { local("§cTime zone must be local, skyblock, or utc."); return 0; }
        cfg.tooltipCreationTimeZone = normalized;
        return save("Creation time zone updated.");
    }
    private static int customDate(String pattern) {
        try { DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH); }
        catch (IllegalArgumentException e) { local("§cInvalid Java date pattern."); return 0; }
        cfg.tooltipCreationCustomPattern = pattern;
        cfg.tooltipCreationDateFormat = "CUSTOM";
        return save("Custom creation date pattern updated.");
    }
    private static int option(String name, String state) {
        Boolean value = parseState(state);
        if (value == null) { local("§cState must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "prices" -> cfg.tooltipPrices = value;
            case "bazaar" -> cfg.tooltipBazaar = value;
            case "lbin", "auction" -> cfg.tooltipLowestBin = value;
            case "npc" -> cfg.tooltipNpcPrice = value;
            case "loading" -> cfg.tooltipPriceLoading = value;
            case "stack", "breakdown" -> cfg.tooltipStackBreakdown = value;
            case "info", "metadata" -> cfg.tooltipItemInfo = value;
            case "id" -> cfg.tooltipSkyblockId = value;
            case "quality" -> cfg.tooltipItemQuality = value;
            case "date", "obtained", "created", "timestamp" -> cfg.tooltipCreationTimestamp = value;
            case "age" -> cfg.tooltipItemAge = value;
            case "createdshift" -> cfg.tooltipCreationTimestampOnShift = value;
            case "ageshift" -> cfg.tooltipItemAgeOnShift = value;
            case "time" -> cfg.tooltipCreationIncludeTime = value;
            case "zone" -> cfg.tooltipCreationIncludeZone = value;
            case "compactage" -> cfg.tooltipAgeCompact = value;
            case "seconds" -> cfg.tooltipAgeShowSeconds = value;
            case "local" -> cfg.tooltipCreationLocalWorlds = value;
            case "hex", "dye" -> cfg.trueHexDisplay = value;
            case "museum" -> cfg.museumDonationStatus = value;
            case "attributes" -> cfg.tooltipAttributes = value;
            default -> { local("§cUnknown option. Use prices, bazaar, lbin, npc, loading, stack, info, id, quality, date, created, age, createdshift, ageshift, time, zone, compactage, seconds, local, hex, museum, or attributes."); return 0; }
        }
        return save("Item tooltip option updated.");
    }

    private static void normalize() {
        if (cfg == null) return;
        cfg.tooltipPriceDecimals = Math.clamp(cfg.tooltipPriceDecimals, 0, 2);
        cfg.tooltipAgePrecision = Math.clamp(cfg.tooltipAgePrecision, 1, 4);
        cfg.tooltipCreationDateFormat = enumValue(cfg.tooltipCreationDateFormat, List.of("AMERICAN", "EUROPEAN", "ISO", "CUSTOM"), "AMERICAN");
        cfg.tooltipCreationTimeZone = enumValue(cfg.tooltipCreationTimeZone, List.of("LOCAL", "SKYBLOCK", "UTC"), "LOCAL");
        try { DateTimeFormatter.ofPattern(cfg.tooltipCreationCustomPattern, Locale.ENGLISH); }
        catch (IllegalArgumentException | NullPointerException ignored) { cfg.tooltipCreationCustomPattern = "MMM d, yyyy h:mm a"; }
    }
    private static String enumValue(String value, List<String> allowed, String fallback) { String normalized=value==null?fallback:value.toUpperCase(Locale.ROOT); return allowed.contains(normalized)?normalized:fallback; }
    private static Boolean parseState(String state) { return switch (state.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "§aon" : "§coff"; }
    private static String mode(boolean enabled, boolean shift) { return enabled ? (shift ? "§eShift" : "§aAlways") : "§cOff"; }
    private static int save(String text) { ConstellationClient.saveConfig(); local("§a" + text); return 1; }
    private static void local(String text) { Minecraft mc=Minecraft.getInstance(); if (mc.player!=null) mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f"+text)); }
}
