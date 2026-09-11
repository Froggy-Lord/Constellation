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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// pipeline and price presentation ported from Skyblocker (LGPL-3.0-or-later):
// skyblock/item/tooltip/TooltipManager.java and adders/{BazaarPriceTooltip,LBinTooltip,NpcPriceTooltip}.java
// market IDs, stack quantities, and metadata ported from NoFrills (GPL-3.0): misc/Utils.java and features/general/{PriceTooltips,InfoTooltips}.java
public final class LyraTooltips {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());
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
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static void append(ItemStack stack, List<Component> lines) {
        if (!active() || stack == null || stack.isEmpty()) return;
        CompoundTag extra = extra(stack);
        String rawId = extra.getStringOr("id", "");
        if (rawId.isBlank()) return;
        String marketId = marketId(stack, extra, rawId);
        int quantity = quantity(stack);
        if (cfg.tooltipPrices) addPrices(lines, rawId, marketId, quantity);
        if (cfg.tooltipItemInfo) addInfo(lines, stack, extra, rawId, marketId);
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
        if (cfg.tooltipObtainedDate) {
            String obtained = obtained(extra);
            if (!obtained.isBlank()) lines.add(Component.literal("Obtained: §6" + obtained));
        }
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

    private static String obtained(CompoundTag extra) {
        long millis = extra.getLongOr("timestamp", 0L);
        if (millis > 0) return DATE.format(Instant.ofEpochMilli(millis));
        String value = extra.getStringOr("timestamp", "").trim();
        if (value.isBlank()) return "";
        try { return DATE.format(Instant.ofEpochMilli(Long.parseLong(value))); }
        catch (NumberFormatException ignored) {
            try { return DATE.format(Instant.parse(value)); }
            catch (DateTimeParseException ignoredAgain) { return value; }
        }
    }

    private static String floor(int tier) { return switch (tier) { case 0 -> "E"; case 1 -> "F1"; case 2 -> "F2"; case 3 -> "F3"; case 4 -> "F4/M1"; case 5 -> "F5/M2"; case 6 -> "F6/M3"; case 7 -> "F7/M4"; case 8 -> "M5"; case 9 -> "M6"; case 10 -> "M7"; default -> "Unknown"; }; }
    private static String title(String value) { String[] parts = value.toLowerCase(Locale.ROOT).split("_"); for (int i=0;i<parts.length;i++) if (!parts[i].isBlank()) parts[i]=Character.toUpperCase(parts[i].charAt(0))+parts[i].substring(1); return String.join(" ", parts); }
    private static String plain(Component value) { String text = ChatFormatting.stripFormatting(value.getString()); return text == null ? value.getString() : text; }
    private static CompoundTag extra(ItemStack stack) { CustomData data = stack.get(DataComponents.CUSTOM_DATA); return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes"); }
    private static String coins(double value) { return String.format(Locale.ROOT, "%,." + cfg.tooltipPriceDecimals + "f", value); }
    private static boolean active() { return cfg != null && cfg.enabled && ConstellationClient.loc().onHypixel(); }

    private static int status() {
        local("§eItem tooltips: prices " + on(cfg.tooltipPrices) + ", Bazaar " + on(cfg.tooltipBazaar) + ", LBIN "
            + on(cfg.tooltipLowestBin) + ", NPC " + on(cfg.tooltipNpcPrice) + ", info " + on(cfg.tooltipItemInfo) + ".");
        local("§7Stack breakdown " + on(cfg.tooltipStackBreakdown) + ", loading line " + on(cfg.tooltipPriceLoading)
            + ", decimals " + cfg.tooltipPriceDecimals + ".");
        return 1;
    }

    private static int decimals(int value) { cfg.tooltipPriceDecimals = Math.clamp(value, 0, 2); return save("Tooltip price precision updated."); }
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
            case "date", "obtained" -> cfg.tooltipObtainedDate = value;
            case "hex", "dye" -> cfg.trueHexDisplay = value;
            case "museum" -> cfg.museumDonationStatus = value;
            case "attributes" -> cfg.tooltipAttributes = value;
            default -> { local("§cUnknown option. Use prices, bazaar, lbin, npc, loading, stack, info, id, quality, date, hex, museum, or attributes."); return 0; }
        }
        return save("Item tooltip option updated.");
    }

    private static void normalize() { if (cfg != null) cfg.tooltipPriceDecimals = Math.clamp(cfg.tooltipPriceDecimals, 0, 2); }
    private static Boolean parseState(String state) { return switch (state.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "§aon" : "§coff"; }
    private static int save(String text) { ConstellationClient.saveConfig(); local("§a" + text); return 1; }
    private static void local(String text) { Minecraft mc=Minecraft.getInstance(); if (mc.player!=null) mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f"+text)); }
}
