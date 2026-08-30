package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.ItemValueCalculator;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.LyraConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/misc/AuctionHousePriceComparison.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/AuctionsHighlighter.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/auctionhouse/AuctionHouseCopyUnderbidPrice.kt
// ported from MarketGuard (GPL-3.0-only): auction/AuctionInventory.java
// ported from MarketGuard (GPL-3.0-only): auction/AuctionPricingResolver.java
// ported from MarketGuard (GPL-3.0-only): auction/AuctionProtectionChecks.java
public final class LyraAuctionHelper {
    private static final Pattern PRICE = Pattern.compile("(?:Buy it now|Starting bid|Top bid|Price|Cost): ?([0-9,.]+) coins", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_PRICE = Pattern.compile("Item price: ?([0-9,.]+) coins", Pattern.CASE_INSENSITIVE);
    private static LyraConfig cfg;
    private static AbstractContainerScreen<?> copiedScreen;
    private static String copiedFingerprint = "";
    private static String riskKey = "";
    private static int riskClicks;
    private static boolean protectionBypass;

    private LyraAuctionHelper() {}

    public static void init(LyraConfig config) {
        cfg = config;
        normalize();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!active() || slot == null || slot.getItem().isEmpty() || playerSlot(slot)) return;
        String title = title(screen);
        if (title.equals("Create BIN Auction") && slot.index == 13) copySuggestedPrice(screen, slot.getItem());
        if (title.equals("Manage Auctions")) {
            List<String> lore = lore(slot.getItem());
            if (cfg.auctionSoldAlert && lore.contains("Status: Sold!")) fill(graphics, slot, cfg.auctionSoldColor);
            else if (cfg.auctionSoldAlert && lore.contains("Status: Expired!")) fill(graphics, slot, cfg.auctionExpiredColor);
            else compareHighlight(graphics, slot, slot.getItem(), listingPrice(slot.getItem()), true);
            return;
        }
        if (cfg.auctionPriceCompare && cfg.auctionPriceHighlight && auctionBrowser(title))
            compareHighlight(graphics, slot, slot.getItem(), listingPrice(slot.getItem()), false);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, ItemStack stack, List<Component> original) {
        if (!active() || !cfg.auctionPriceCompare || !cfg.auctionPriceTooltip || original == null || stack == null || stack.isEmpty()) return original;
        String title = title(screen);
        if (!auctionBrowser(title) && !title.equals("Manage Auctions") && !title.equals("Create BIN Auction")) return original;
        double listed = listingPrice(stack);
        ItemValueCalculator.Result estimate = ItemValueCalculator.estimate(stack);
        if (estimate.total() <= 0 || listed <= 0) return original;
        double percent = listed / estimate.total() * 100.0;
        List<Component> result = new ArrayList<>(original);
        result.add(Component.literal(String.format(Locale.ROOT, "Estimated Value: §6%,.0f coins%s", estimate.total(), estimate.complete() ? "" : " §7(partial)")));
        result.add(Component.literal(String.format(Locale.ROOT, "Auction Price: %s%.1f%% §7of estimate", percentColor(percent), percent)));
        if (!estimate.components().isEmpty())
            result.add(Component.literal(String.format(Locale.ROOT, "Included Upgrades: §6%,.0f coins", estimate.additions())));
        return result;
    }

    public static boolean shouldBlockClick(AbstractContainerScreen<?> screen, Slot clicked, int slotId) {
        protectionBypass = false;
        if (!active() || clicked == null) { resetRisk(); return false; }
        String title = title(screen);
        Risk risk = null;
        if (cfg.auctionProtectListings && title.equals("Create BIN Auction") && slotId == 29 && name(clicked.getItem()).equals("Create BIN Auction"))
            risk = listingRisk(screen);
        else if (cfg.auctionProtectPurchases && title.equals("BIN Auction View") && slotId == 31 && name(clicked.getItem()).equals("Buy Item Right Now"))
            risk = purchaseRisk(screen);
        else { resetRisk(); return false; }
        if (risk == null) { resetRisk(); return false; }

        String key = System.identityHashCode(screen) + "|" + title + '|' + risk.itemId + '|' + Math.round(risk.price) + '|' + Math.round(risk.reference) + '|' + risk.kind;
        if (!key.equals(riskKey)) { riskKey = key; riskClicks = 0; }
        riskClicks++;
        if (riskClicks >= cfg.auctionOverrideClicks) {
            local("§eAuction warning overridden on click " + riskClicks + ".");
            protectionBypass = true;
            resetRisk();
            return false;
        }
        int remaining = cfg.auctionOverrideClicks - riskClicks;
        String relation = risk.kind.equals("underbid") ? "below" : "above";
        local(String.format(Locale.ROOT, "§cBlocked auction %s: §f%,.0f §cis %.1f%% %s estimate §f%,.0f§c. Click %d more time%s to override.",
            risk.kind, risk.price, Math.abs(100.0 - risk.price / risk.reference * 100.0), relation, risk.reference, remaining, remaining == 1 ? "" : "s"));
        return true;
    }

    public static boolean consumeProtectionBypass() {
        boolean value = protectionBypass;
        protectionBypass = false;
        return value;
    }

    private static Risk listingRisk(AbstractContainerScreen<?> screen) {
        ItemStack item = stack(screen, 13), priceItem = stack(screen, 31);
        double price = parsePrice(name(priceItem));
        if (price <= 0) price = listingPrice(priceItem);
        Reference reference = reference(item);
        if (price <= 0 || reference.value <= 0) return null;
        double difference = reference.value - price;
        if (difference < cfg.auctionMinimumDifference || price >= reference.value * cfg.auctionUnderbidPercent / 100.0) return null;
        return new Risk("underbid", reference.id, price, reference.value);
    }

    private static Risk purchaseRisk(AbstractContainerScreen<?> screen) {
        ItemStack item = stack(screen, 13), buy = stack(screen, 31);
        double price = listingPrice(buy);
        if (price <= 0) price = listingPrice(item);
        Reference reference = reference(item);
        if (price <= 0 || reference.value <= 0 || !reference.complete && !cfg.auctionUseIncompleteEstimate) return null;
        double difference = price - reference.value;
        if (difference < cfg.auctionMinimumDifference || price <= reference.value * cfg.auctionOverbidPercent / 100.0) return null;
        return new Risk("overbid", reference.id, price, reference.value);
    }

    private static Reference reference(ItemStack stack) {
        String id = LyraTooltips.marketId(stack);
        if (id.isBlank()) return new Reference("", 0, false);
        double base = PriceProvider.sellValue(id);
        if (base <= 0) PriceProvider.warm(id);
        ItemValueCalculator.Result estimate = ItemValueCalculator.estimate(stack);
        double value = base;
        if (estimate.complete() || cfg.auctionUseIncompleteEstimate) value = Math.max(value, estimate.total());
        return new Reference(id, value, estimate.complete());
    }

    private static void copySuggestedPrice(AbstractContainerScreen<?> screen, ItemStack stack) {
        if (!cfg.auctionAutoCopyPrice) return;
        ItemValueCalculator.Result estimate = ItemValueCalculator.estimate(stack);
        if (estimate.total() <= 1 || cfg.auctionCopyOnlyCompleteEstimate && !estimate.complete()) return;
        String fingerprint = LyraTooltips.marketId(stack) + '|' + stack.getComponentsPatch() + '|' + Math.round(estimate.total());
        if (screen == copiedScreen && fingerprint.equals(copiedFingerprint)) return;
        copiedScreen = screen;
        copiedFingerprint = fingerprint;
        long suggested = Math.max(1, (long) Math.floor(estimate.total()) - 1);
        Minecraft.getInstance().keyboardHandler.setClipboard(Long.toString(suggested));
        local("§aCopied estimated BIN price §f" + String.format(Locale.ROOT, "%,d", suggested) + "§a" + (estimate.complete() ? "." : " §7(partial estimate)."));
    }

    private static void compareHighlight(GuiGraphicsExtractor graphics, Slot slot, ItemStack stack, double listed, boolean manage) {
        if (listed <= 0) return;
        ItemValueCalculator.Result estimate = ItemValueCalculator.estimate(stack);
        if (estimate.total() <= 0 || !estimate.complete() && !cfg.auctionUseIncompleteEstimate) return;
        double percent = listed / estimate.total() * 100.0;
        int color = 0;
        if (percent <= cfg.auctionGoodPercent && cfg.auctionHighlightUnderbid)
            color = manage ? cfg.auctionUnderbidColor : percent <= cfg.auctionVeryGoodPercent ? cfg.auctionVeryGoodColor : cfg.auctionGoodColor;
        else if (percent >= cfg.auctionBadPercent && cfg.auctionHighlightOverpriced)
            color = percent >= cfg.auctionVeryBadPercent ? cfg.auctionVeryBadColor : cfg.auctionBadColor;
        if (color != 0) fill(graphics, slot, color);
    }

    private static void fill(GuiGraphicsExtractor graphics, Slot slot, int color) { graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color); }
    private static boolean auctionBrowser(String title) { return title.startsWith("Auctions") || title.equals("Auction Browser"); }
    private static String title(AbstractContainerScreen<?> screen) { return plain(screen.getTitle()).strip(); }
    private static boolean playerSlot(Slot slot) { Minecraft mc = Minecraft.getInstance(); return mc.player != null && slot.container == mc.player.getInventory(); }
    private static ItemStack stack(AbstractContainerScreen<?> screen, int index) { return index >= 0 && index < screen.getMenu().slots.size() ? screen.getMenu().getSlot(index).getItem() : ItemStack.EMPTY; }
    private static String name(ItemStack stack) { return stack == null || stack.isEmpty() ? "" : plain(stack.getHoverName()).strip(); }
    private static String plain(Component component) { String value = ChatFormatting.stripFormatting(component.getString()); return value == null ? component.getString() : value; }

    private static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();
        List<String> lines = new ArrayList<>();
        for (Component line : lore.lines()) lines.add(plain(line).strip());
        return lines;
    }

    private static double listingPrice(ItemStack stack) {
        double namePrice = parsePrice(name(stack));
        if (namePrice > 0) return namePrice;
        for (String line : lore(stack)) { double price = parsePrice(line); if (price > 0) return price; }
        return 0;
    }

    private static double parsePrice(String line) {
        Matcher matcher = ITEM_PRICE.matcher(line);
        if (!matcher.find()) matcher = PRICE.matcher(line);
        if (!matcher.find(0)) return 0;
        try { return Double.parseDouble(matcher.group(1).replace(",", "")); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static String percentColor(double percent) {
        if (percent <= cfg.auctionVeryGoodPercent) return "§b";
        if (percent <= cfg.auctionGoodPercent) return "§a";
        if (percent >= cfg.auctionVeryBadPercent) return "§c";
        if (percent >= cfg.auctionBadPercent) return "§6";
        return "§e";
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("auctionhelper")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("threshold")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("kind", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("percent", IntegerArgumentType.integer(1, 1000))
                        .executes(context -> threshold(StringArgumentType.getString(context, "kind"), IntegerArgumentType.getInteger(context, "percent"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("override")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("clicks", IntegerArgumentType.integer(2, 5))
                    .executes(context -> override(IntegerArgumentType.getInteger(context, "clicks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("§eAuction helper " + on(cfg.auctionHelper) + ", comparison " + on(cfg.auctionPriceCompare) + ", clipboard " + on(cfg.auctionAutoCopyPrice) + ".");
        local("§7Listing protection " + on(cfg.auctionProtectListings) + " at " + cfg.auctionUnderbidPercent + "%, purchase protection " + on(cfg.auctionProtectPurchases) + " at " + cfg.auctionOverbidPercent + "%, override " + cfg.auctionOverrideClicks + " clicks.");
        return 1;
    }

    private static int threshold(String kind, int percent) {
        if (kind.equalsIgnoreCase("underbid")) cfg.auctionUnderbidPercent = percent;
        else if (kind.equalsIgnoreCase("overbid")) cfg.auctionOverbidPercent = percent;
        else { local("§cThreshold must be underbid or overbid."); return 0; }
        save(); local("§aAuction threshold updated."); return 1;
    }

    private static int override(int clicks) { cfg.auctionOverrideClicks = clicks; save(); local("§aAuction override updated."); return 1; }
    private static int option(String name, String state) {
        Boolean value = parseState(state);
        if (value == null) { local("§cState must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.auctionHelper = value;
            case "compare" -> cfg.auctionPriceCompare = value;
            case "tooltip" -> cfg.auctionPriceTooltip = value;
            case "highlight" -> cfg.auctionPriceHighlight = value;
            case "copy" -> cfg.auctionAutoCopyPrice = value;
            case "completecopy" -> cfg.auctionCopyOnlyCompleteEstimate = value;
            case "listing" -> cfg.auctionProtectListings = value;
            case "purchase" -> cfg.auctionProtectPurchases = value;
            case "partial" -> cfg.auctionUseIncompleteEstimate = value;
            case "sold" -> cfg.auctionSoldAlert = value;
            default -> { local("§cOption must be enabled, compare, tooltip, highlight, copy, completecopy, listing, purchase, partial, or sold."); return 0; }
        }
        save(); local("§aAuction-helper option updated."); return 1;
    }

    private static Boolean parseState(String state) { return switch (state.toLowerCase(Locale.ROOT)) { case "on", "true", "yes" -> true; case "off", "false", "no" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "§aON" : "§cOFF"; }
    private static void normalize() {
        if (cfg == null) return;
        cfg.auctionOverrideClicks = Math.clamp(cfg.auctionOverrideClicks, 2, 5);
        cfg.auctionGoodPercent = Math.clamp(cfg.auctionGoodPercent, 1, 1000);
        cfg.auctionVeryGoodPercent = Math.clamp(cfg.auctionVeryGoodPercent, 1, cfg.auctionGoodPercent);
        cfg.auctionBadPercent = Math.clamp(cfg.auctionBadPercent, 1, 1000);
        cfg.auctionVeryBadPercent = Math.clamp(cfg.auctionVeryBadPercent, cfg.auctionBadPercent, 1000);
        cfg.auctionUnderbidPercent = Math.clamp(cfg.auctionUnderbidPercent, 1, 100);
        cfg.auctionOverbidPercent = Math.clamp(cfg.auctionOverbidPercent, 100, 1000);
        cfg.auctionMinimumDifference = Math.max(0, cfg.auctionMinimumDifference);
    }
    private static void save() { normalize(); ConstellationClient.saveConfig(); }
    private static boolean active() { return cfg != null && cfg.enabled && cfg.auctionHelper && ConstellationClient.loc().onHypixel(); }
    private static void local(String message) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f" + message)); }
    private static void resetRisk() { riskKey = ""; riskClicks = 0; }
    private static void clear() { copiedScreen = null; copiedFingerprint = ""; protectionBypass = false; resetRisk(); }

    private record Risk(String kind, String itemId, double price, double reference) {}
    private record Reference(String id, double value, boolean complete) {}
}
