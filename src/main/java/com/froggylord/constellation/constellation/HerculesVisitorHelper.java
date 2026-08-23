package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.BazaarApi;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.HerculesConfig;
import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/visitor/VisitorApi.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/visitor/GardenVisitorTooltip.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/visitor/GardenVisitorShoppingList.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/visitor/VisitorRewardWarning.kt
// ported from Devonian (GPL-3.0-only): features/garden/VisitorProfitDisplay.kt
public final class HerculesVisitorHelper {
    private static final Pattern AMOUNT_AFTER = Pattern.compile("^(.+?)\\s+x([0-9,]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_BEFORE = Pattern.compile("^([0-9,]+)x\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COPPER = Pattern.compile("\\+([0-9,.kKmMbB]+) Copper");
    private static final Pattern GARDEN_XP = Pattern.compile("\\+([0-9,.kKmMbB]+) Garden Experience");
    private static final Pattern OFFERS = Pattern.compile("Offers Accepted: ([0-9,]+)");
    private static final Pattern STORED = Pattern.compile("Stored: ([0-9,]+)/");
    private static final Map<String, String> ITEM_IDS = itemIds();
    private static final Map<String, String> REWARD_IDS = rewardIds();
    private static final Map<String, Visitor> VISITORS = new LinkedHashMap<>();
    private static final Map<String, Integer> SACKS = new LinkedHashMap<>();
    private static HerculesConfig cfg;
    private static String profile = "";
    private static final java.util.Set<String> NOTIFIED = new LinkedHashSet<>();
    private static String resolvedFingerprint = "";

    private HerculesVisitorHelper() {}

    public static Integer observedSackCount(String itemId) { return SACKS.get(itemId); }

    public static void init(HerculesConfig config) {
        cfg = config;
        normalize();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!scope() || slot == null || slot.getItem().isEmpty()) return;
        observeSack(screen, slot);
        Visitor visitor = visitor(screen);
        if (visitor == null) return;
        if (slot.index == 29 || slot.index == 33) {
            Reason reason = blockReason(visitor);
            if (reason == null) return;
            boolean blocked = reason.refuse ? slot.index == 33 : slot.index == 29;
            if (blocked && !bypassHeld()) {
                if (cfg.visitorBlockedSlotHighlight) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, cfg.visitorBlockedColor);
                if (cfg.visitorOptionOutline) border(graphics, slot, cfg.visitorBadOutlineColor);
            } else if (cfg.visitorOptionOutline) border(graphics, slot, cfg.visitorGoodOutlineColor);
        }
    }

    public static boolean shouldBlockClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, ContainerInput input) {
        if (!scope() || slot == null || slotId != 29 && slotId != 33) return false;
        Visitor visitor = visitor(screen);
        if (visitor == null) return false;
        boolean refuse = slotId == 33 && name(slot.getItem()).equals("Refuse Offer");
        boolean accept = slotId == 29 && name(slot.getItem()).equals("Accept Offer");
        if (!refuse && !accept) return false;
        Reason reason = blockReason(visitor);
        if (reason != null && reason.refuse == refuse && !bypassHeld()) {
            local("§cBlocked " + (refuse ? "refusing" : "accepting") + " §f" + visitor.name + "§c: " + reason.text + ". Hold the configured bypass key to continue.");
            return true;
        }
        if (input != ContainerInput.QUICK_MOVE && (refuse || accept && lore(slot.getItem()).stream().anyMatch(line -> line.contains("Click to give!")))) {
            VISITORS.remove(visitor.name);
            resolvedFingerprint = visitor.fingerprint;
        }
        return false;
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, ItemStack stack, List<Component> original) {
        if (!scope() || !cfg.visitorTooltip || original == null || stack == null || stack.isEmpty()) return original;
        String action = name(stack);
        if (!action.equals("Accept Offer") && !action.equals("Refuse Offer")) return original;
        Visitor visitor = visitor(screen);
        if (visitor == null) return original;
        List<Component> out = new ArrayList<>(original);
        if (cfg.visitorTooltipItemPrices) for (Need need : visitor.needs)
            out.add(Component.literal("Required " + need.name + ": §6" + coins(need.price) + " §7(" + availability(need) + "§7)"));
        if (cfg.visitorTooltipCopperPrice && visitor.copper > 0 && visitor.economyComplete)
            out.add(Component.literal("Cost per Copper: §6" + coins(visitor.requiredCost / visitor.copper)));
        if (cfg.visitorTooltipGardenExperiencePrice && visitor.gardenXp > 0 && visitor.economyComplete)
            out.add(Component.literal("Cost per Garden XP: §6" + coins(visitor.requiredCost / visitor.gardenXp)));
        if (cfg.visitorTooltipProfit && visitor.economyComplete)
            out.add(Component.literal("Estimated Profit: " + (visitor.profit() >= 0 ? "§a" : "§c") + signedCoins(visitor.profit())));
        Reason reason = blockReason(visitor);
        boolean blockedAction = reason != null && (reason.refuse && action.equals("Refuse Offer") || !reason.refuse && action.equals("Accept Offer"));
        if (blockedAction && !bypassHeld()) {
            out.add(Component.empty());
            out.add(Component.literal("§cBlocked: " + reason.text));
            out.add(Component.literal("§7Hold the configured bypass key to continue."));
        }
        return out;
    }

    public static List<DisplayRow> displayRows() {
        if (!scope() || !cfg.visitorShoppingList || !displayArea() || VISITORS.isEmpty()) return List.of();
        Map<String, NeedTotal> totals = new LinkedHashMap<>();
        for (Visitor visitor : VISITORS.values()) for (Need need : visitor.needs) {
            NeedTotal old = totals.get(need.id);
            totals.put(need.id, new NeedTotal(need.id, need.name, (old == null ? 0 : old.amount) + need.amount,
                (old == null ? 0 : old.price) + need.price));
        }
        List<DisplayRow> rows = new ArrayList<>();
        double totalPrice = 0;
        for (NeedTotal total : totals.values()) {
            int inventory = inventory(total.id), sacks = SACKS.getOrDefault(total.id, -1);
            int available = inventory + Math.max(0, sacks);
            StringBuilder value = new StringBuilder("x" + format(total.amount));
            if (cfg.visitorShowPrice && total.price > 0) value.append("  ").append(compact(total.price));
            if (cfg.visitorShowInventoryCount || cfg.visitorShowSackCount) {
                value.append("  ").append(available >= total.amount ? "§a" : "§e");
                if (cfg.visitorShowInventoryCount) value.append("I:").append(format(inventory));
                if (cfg.visitorShowInventoryCount && cfg.visitorShowSackCount) value.append(' ');
                if (cfg.visitorShowSackCount) value.append(sacks < 0 ? "S:?" : "S:" + format(sacks));
            }
            rows.add(new DisplayRow(total.name, value.toString(), available >= total.amount ? 0xFF55FF55 : 0xFFFFFF55));
            totalPrice += total.price;
        }
        if (cfg.visitorShowTotalPrice && totalPrice > 0) rows.add(new DisplayRow("Total", compact(totalPrice), 0xFFFFAA00));
        if (cfg.visitorShowVisitors) for (Visitor visitor : VISITORS.values()) {
            String value = visitor.rareRewards.isEmpty() ? visitor.economyComplete ? signedCompact(visitor.profit()) : "Loaded"
                : "§d" + String.join(", ", visitor.rareRewards);
            rows.add(new DisplayRow(visitor.name, value, visitor.rareRewards.isEmpty() ? 0xFFFFFFFF : 0xFFFF55FF));
        }
        return List.copyOf(rows);
    }

    private static Visitor visitor(AbstractContainerScreen<?> screen) {
        if (ConstellationClient.loc().area() != com.froggylord.constellation.core.LocationManager.SkyblockArea.GARDEN) return null;
        if (screen.getMenu().slots.size() <= 33) return null;
        ItemStack info = screen.getMenu().getSlot(13).getItem(), accept = screen.getMenu().getSlot(29).getItem();
        if (!name(accept).equals("Accept Offer")) return null;
        List<String> infoLore = lore(info);
        if (infoLore.size() != 4 || infoLore.stream().noneMatch(line -> line.startsWith("Offers Accepted:"))) return null;
        String visitorName = name(info);
        if (visitorName.isBlank()) return null;
        String fingerprint = visitorName + '|' + lore(accept);
        if (fingerprint.equals(resolvedFingerprint)) return null;
        resolvedFingerprint = "";
        Visitor existing = VISITORS.get(visitorName);
        if (existing != null && existing.fingerprint.equals(fingerprint)
            && System.currentTimeMillis() - existing.parsedAt < cfg.visitorPriceRefreshSeconds * 1_000L) return existing;
        Visitor parsed = parseVisitor(visitorName, fingerprint, infoLore, lore(accept));
        VISITORS.put(visitorName, parsed);
        while (VISITORS.size() > 5) VISITORS.remove(VISITORS.keySet().iterator().next());
        notifyRewards(parsed);
        return parsed;
    }

    private static Visitor parseVisitor(String name, String fingerprint, List<String> infoLore, List<String> offerLore) {
        BazaarApi.ensureFresh();
        List<Need> needs = new ArrayList<>();
        List<String> rewards = new ArrayList<>(), rare = new ArrayList<>();
        boolean rewardSection = false, complete = true;
        int copper = 0, gardenXp = 0, offers = -1;
        double required = 0, rewardValue = 0;
        for (String line : infoLore) {
            Matcher matcher = OFFERS.matcher(line);
            if (matcher.find()) offers = parseInt(matcher.group(1), -1);
        }
        for (String line : offerLore) {
            if (line.equals("Rewards:")) { rewardSection = true; continue; }
            Matcher copperMatcher = COPPER.matcher(line);
            if (copperMatcher.find()) { copper = (int) parseCompact(copperMatcher.group(1)); continue; }
            Matcher xpMatcher = GARDEN_XP.matcher(line);
            if (xpMatcher.find()) { gardenXp = (int) parseCompact(xpMatcher.group(1)); continue; }
            ParsedAmount amount = amount(line);
            if (amount != null) {
                String id = idFor(amount.name);
                if (id.isBlank()) { complete = false; continue; }
                double price = rewardSection ? PriceProvider.sellValue(id) : PriceProvider.purchaseValue(id);
                if (price <= 0) { PriceProvider.warm(id); complete = false; }
                double total = price * amount.amount;
                if (rewardSection) { rewards.add(id); rewardValue += total; }
                else { needs.add(new Need(id, amount.name, amount.amount, total)); required += total; }
                continue;
            }
            if (!rewardSection || line.isBlank() || line.endsWith(":")) continue;
            String rewardName = line.replace("+", "").replace("\u2764", "").trim();
            String id = REWARD_IDS.get(normalizeName(rewardName));
            if (id == null) continue;
            rewards.add(id);
            if (cfg.visitorWarnRewards.stream().anyMatch(value -> value.equalsIgnoreCase(id))) rare.add(rewardName);
            double price = PriceProvider.sellValue(id);
            if (price > 0) rewardValue += price; else { PriceProvider.warm(id); complete = false; }
        }
        double copperValue = PriceProvider.sellValue("ENCHANTMENT_GREEN_THUMB_1");
        if (copper > 0 && copperValue <= 0) { PriceProvider.warm("ENCHANTMENT_GREEN_THUMB_1"); complete = false; }
        double totalReward = rewardValue + (copperValue > 0 ? copper * copperValue / 1500.0 : 0);
        return new Visitor(name, fingerprint, List.copyOf(needs), List.copyOf(rewards), List.copyOf(rare), offers,
            copper, gardenXp, required, totalReward, complete, System.currentTimeMillis());
    }

    private static Reason blockReason(Visitor visitor) {
        if (cfg.visitorPreventRareRefuse && !visitor.rareRewards.isEmpty()) return new Reason(true, "rare reward " + String.join(", ", visitor.rareRewards));
        if (cfg.visitorPreventNewRefuse && visitor.offersAccepted == 0 && !bingoProfile()) return new Reason(true, "this visitor has never been accepted");
        if (!visitor.economyComplete || visitor.copper <= 0) return null;
        double perCopper = visitor.requiredCost / visitor.copper;
        double loss = -visitor.profit();
        if (cfg.visitorPreventCheapCopperRefuse && perCopper <= cfg.visitorCoinsPerCopper) return new Reason(true, "cheap copper at " + coins(perCopper) + " each");
        if (cfg.visitorPreventExpensiveCopperAccept && perCopper > cfg.visitorCoinsPerCopper) return new Reason(false, "expensive copper at " + coins(perCopper) + " each");
        if (cfg.visitorPreventLowLossRefuse && loss <= cfg.visitorAcceptableLoss) return new Reason(true, "loss is only " + coins(loss));
        if (cfg.visitorPreventHighLossAccept && loss > cfg.visitorAcceptableLoss) return new Reason(false, "loss is " + coins(loss));
        return null;
    }

    private static void notifyRewards(Visitor visitor) {
        if (!cfg.visitorRewardChat || visitor.rareRewards.isEmpty()) return;
        String key = visitor.fingerprint + '|' + visitor.rareRewards;
        if (!NOTIFIED.add(key)) return;
        while (NOTIFIED.size() > 20) NOTIFIED.remove(NOTIFIED.iterator().next());
        local("§dVisitor reward found for §f" + visitor.name + "§d: §f" + String.join(", ", visitor.rareRewards) + "§d.");
    }

    private static void observeSack(AbstractContainerScreen<?> screen, Slot slot) {
        if (!title(screen).toLowerCase(Locale.ROOT).contains("sack")) return;
        String id = LyraTooltips.marketId(slot.getItem());
        if (id.isBlank()) return;
        for (String line : lore(slot.getItem())) {
            Matcher matcher = STORED.matcher(line);
            if (matcher.find()) { SACKS.put(id, parseInt(matcher.group(1), 0)); return; }
        }
    }

    private static int inventory(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int amount = 0;
        for (ItemStack stack : mc.player.getInventory()) if (LyraTooltips.marketId(stack).equals(id)) amount += stack.getCount();
        return amount;
    }

    private static String availability(Need need) {
        int inventory = inventory(need.id), sacks = SACKS.getOrDefault(need.id, -1);
        return "§a" + inventory + " inventory, " + (sacks < 0 ? "§7sacks unknown" : "§a" + sacks + " sacks");
    }

    private static ParsedAmount amount(String line) {
        String value = line.replace("\u2764", "").trim();
        Matcher after = AMOUNT_AFTER.matcher(value);
        if (after.matches()) return new ParsedAmount(after.group(1).trim(), parseInt(after.group(2), 0));
        Matcher before = AMOUNT_BEFORE.matcher(value);
        if (before.matches()) return new ParsedAmount(before.group(2).trim(), parseInt(before.group(1), 0));
        return null;
    }

    private static String idFor(String name) {
        String normalized = normalizeName(name);
        String mapped = ITEM_IDS.get(normalized);
        if (mapped != null) return mapped;
        mapped = REWARD_IDS.get(normalized);
        if (mapped != null) return mapped;
        return normalized.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static Map<String, String> itemIds() {
        Map<String, String> map = new LinkedHashMap<>();
        put(map, "Wheat", "WHEAT"); put(map, "Enchanted Bread", "ENCHANTED_BREAD"); put(map, "Hay Bale", "HAY_BLOCK"); put(map, "Enchanted Hay Bale", "ENCHANTED_HAY_BLOCK"); put(map, "Tightly-Tied Hay Bale", "TIGHTLY_TIED_HAY_BALE");
        put(map, "Carrot", "CARROT_ITEM"); put(map, "Enchanted Carrot", "ENCHANTED_CARROT"); put(map, "Enchanted Golden Carrot", "ENCHANTED_GOLDEN_CARROT");
        put(map, "Potato", "POTATO_ITEM"); put(map, "Enchanted Potato", "ENCHANTED_POTATO"); put(map, "Enchanted Baked Potato", "ENCHANTED_BAKED_POTATO");
        put(map, "Pumpkin", "PUMPKIN"); put(map, "Enchanted Pumpkin", "ENCHANTED_PUMPKIN"); put(map, "Polished Pumpkin", "POLISHED_PUMPKIN");
        put(map, "Melon", "MELON"); put(map, "Enchanted Melon", "ENCHANTED_MELON"); put(map, "Enchanted Melon Block", "ENCHANTED_MELON_BLOCK");
        put(map, "Seeds", "SEEDS"); put(map, "Enchanted Seeds", "ENCHANTED_SEEDS"); put(map, "Box of Seeds", "BOX_OF_SEEDS");
        put(map, "Mushroom", "BROWN_MUSHROOM"); put(map, "Red Mushroom", "RED_MUSHROOM"); put(map, "Brown Mushroom", "BROWN_MUSHROOM"); put(map, "Enchanted Red Mushroom", "ENCHANTED_RED_MUSHROOM"); put(map, "Enchanted Brown Mushroom", "ENCHANTED_BROWN_MUSHROOM"); put(map, "Enchanted Red Mushroom Block", "ENCHANTED_HUGE_MUSHROOM_2"); put(map, "Enchanted Brown Mushroom Block", "ENCHANTED_HUGE_MUSHROOM_1");
        put(map, "Cocoa Beans", "INK_SACK:3"); put(map, "Enchanted Cocoa Beans", "ENCHANTED_COCOA"); put(map, "Enchanted Cookie", "ENCHANTED_COOKIE");
        put(map, "Cactus", "CACTUS"); put(map, "Enchanted Cactus Green", "ENCHANTED_CACTUS_GREEN"); put(map, "Enchanted Cactus", "ENCHANTED_CACTUS");
        put(map, "Sugar Cane", "SUGAR_CANE"); put(map, "Enchanted Sugar", "ENCHANTED_SUGAR"); put(map, "Enchanted Sugar Cane", "ENCHANTED_SUGAR_CANE");
        put(map, "Nether Wart", "NETHER_STALK"); put(map, "Enchanted Nether Wart", "ENCHANTED_NETHER_STALK"); put(map, "Mutant Nether Wart", "MUTANT_NETHER_STALK");
        return Map.copyOf(map);
    }

    private static Map<String, String> rewardIds() {
        Map<String, String> map = new LinkedHashMap<>();
        put(map,"Flowering Bouquet","FLOWERING_BOUQUET"); put(map,"Overgrown Grass","OVERGROWN_GRASS"); put(map,"Green Bandana","GREEN_BANDANA"); put(map,"Dedication IV","ENCHANTMENT_DEDICATION_4"); put(map,"Music Rune I","MUSIC_1_RUNE"); put(map,"Space Helmet","DCTR_SPACE_HELM"); put(map,"Cultivating I","ENCHANTMENT_CULTIVATING_1"); put(map,"Replenish I","ENCHANTMENT_REPLENISH_1"); put(map,"Delicate V","ENCHANTMENT_DELICATE_5"); put(map,"Copper Dye","DYE_COPPER"); put(map,"Jungle Key","JUNGLE_KEY"); put(map,"Fruit Bowl","FRUIT_BOWL"); put(map,"Harvest Harbinger V","POTION_HARVEST_HARBINGER;5"); put(map,"Hypercharge Chip","HYPERCHARGE_GARDEN_CHIP"); put(map,"Quickdraw Chip","QUICKDRAW_GARDEN_CHIP"); put(map,"Farming Exp Boost","PET_ITEM_FARMING_SKILL_BOOST_EPIC"); put(map,"Unfulfilled Jerryseed","UNFULFILLED_JERRYSEED"); put(map,"Voter's Badge","VOTER_BADGE"); put(map,"VIP Voter's Badge","VOTER_BADGE_VIP"); put(map,"Elite Voter's Badge","VOTER_BADGE_ELITE"); put(map,"Supreme Voter's Badge","VOTER_BADGE_SUPREME"); put(map,"Wild Strawberry Dye","DYE_WILD_STRAWBERRY"); put(map,"Velvet Top Hat","VELVET_TOP_HAT"); put(map,"Cashmere Jacket","CASHMERE_JACKET"); put(map,"Satin Trousers","SATIN_TROUSERS"); put(map,"Oxford Shoes","OXFORD_SHOES"); put(map,"Carnival Ticket","CARNIVAL_TICKET"); put(map,"Visitors' Gratitude","VISITORS_GRATITUDE"); put(map,"Farming Contest Display","FARMING_CONTEST_DISPLAY"); put(map,"Astronaut Minion Skin","ASTRONAUT_PERSONALITY"); put(map,"Fast Food Barn Skin","FAST_FOOD_BARN_SKIN"); put(map,"Jelly Garden Greenhouse Skin","JELLY_GREENHOUSE_SKIN");
        return Map.copyOf(map);
    }

    private static void put(Map<String, String> map, String name, String id) { map.put(normalizeName(name), id); }
    private static String normalizeName(String name) { return name.replace("\u25C6", "").replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT); }
    private static String title(AbstractContainerScreen<?> screen) { return plain(screen.getTitle()).strip(); }
    private static String name(ItemStack stack) { return stack == null || stack.isEmpty() ? "" : plain(stack.getHoverName()).strip(); }
    private static String plain(Component component) { String value = ChatFormatting.stripFormatting(component.getString()); return value == null ? component.getString() : value; }
    private static List<String> lore(ItemStack stack) { ItemLore lore = stack.get(DataComponents.LORE); if (lore == null) return List.of(); List<String> out = new ArrayList<>(); for (Component line : lore.lines()) out.add(plain(line).strip()); return out; }
    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value.replace(",", "")); } catch (NumberFormatException ignored) { return fallback; } }
    private static double parseCompact(String value) { try { String clean=value.replace(",","").toLowerCase(Locale.ROOT); double multiplier=clean.endsWith("k")?1e3:clean.endsWith("m")?1e6:clean.endsWith("b")?1e9:1; if(multiplier>1)clean=clean.substring(0,clean.length()-1); return Double.parseDouble(clean)*multiplier; } catch(Exception ignored){return 0;} }
    private static String format(long value) { return String.format(Locale.ROOT, "%,d", value); }
    private static String coins(double value) { return String.format(Locale.ROOT, "%,.0f coins", value); }
    private static String signedCoins(double value) { return (value >= 0 ? "+" : "-") + coins(Math.abs(value)); }
    private static String compact(double value) { double a=Math.abs(value); String s=a>=1e9?String.format(Locale.ROOT,"%.2fB",a/1e9):a>=1e6?String.format(Locale.ROOT,"%.2fM",a/1e6):a>=1e3?String.format(Locale.ROOT,"%.1fk",a/1e3):String.format(Locale.ROOT,"%.0f",a); return s; }
    private static String signedCompact(double value) { return (value >= 0 ? "§a+" : "§c-") + compact(value); }
    private static boolean bypassHeld() { return cfg.visitorAllowHoldBypass && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), cfg.visitorBypassKey); }
    private static void border(GuiGraphicsExtractor g, Slot s, int c) { g.fill(s.x,s.y,s.x+16,s.y+1,c); g.fill(s.x,s.y+15,s.x+16,s.y+16,c); g.fill(s.x,s.y,s.x+1,s.y+16,c); g.fill(s.x+15,s.y,s.x+16,s.y+16,c); }

    private static boolean scope() {
        if (cfg == null || !cfg.enabled || !cfg.visitorHelper || !ConstellationClient.loc().onHypixel()) return false;
        String current = LyraStorageValue.currentProfileKey();
        if (!current.equals(profile)) { VISITORS.clear(); SACKS.clear(); NOTIFIED.clear(); resolvedFingerprint = ""; profile = current; }
        return true;
    }
    private static boolean bingoProfile() { return ConstellationClient.loc().getSidebarLines().stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("bingo")); }
    private static boolean displayArea() { var area=ConstellationClient.loc().area(); return cfg.visitorShowInGarden && area==com.froggylord.constellation.core.LocationManager.SkyblockArea.GARDEN || cfg.visitorShowInHub && area==com.froggylord.constellation.core.LocationManager.SkyblockArea.HUB; }
    private static void normalize() { if(cfg==null)return; cfg.visitorCoinsPerCopper=Math.max(1,cfg.visitorCoinsPerCopper); cfg.visitorAcceptableLoss=Math.max(0,cfg.visitorAcceptableLoss); cfg.visitorPriceRefreshSeconds=Math.clamp(cfg.visitorPriceRefreshSeconds,2,300); if(cfg.visitorWarnRewards==null)cfg.visitorWarnRewards=new ArrayList<>(); cfg.visitorWarnRewards.removeIf(value->value==null||value.isBlank()); }
    private static void clear() { VISITORS.clear(); SACKS.clear(); NOTIFIED.clear(); profile=""; resolvedFingerprint=""; }
    private static void local(String text) { Minecraft mc=Minecraft.getInstance(); if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2Hercules §8> §f"+text)); }
    private static void save() { normalize(); ConstellationClient.saveConfig(); }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("visitors")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(context -> { clear(); local("§aVisitor cache cleared."); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("copper")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("coins",IntegerArgumentType.integer(1))
                    .executes(context->{cfg.visitorCoinsPerCopper=IntegerArgumentType.getInteger(context,"coins");save();local("§aCopper threshold updated.");return 1;})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("loss")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("coins",IntegerArgumentType.integer(0))
                    .executes(context->{cfg.visitorAcceptableLoss=IntegerArgumentType.getInteger(context,"coins");save();local("§aLoss threshold updated.");return 1;})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("refresh")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(2,300))
                    .executes(context->{cfg.visitorPriceRefreshSeconds=IntegerArgumentType.getInteger(context,"seconds");save();local("§aVisitor price refresh updated.");return 1;})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bypasskey")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("keycode",IntegerArgumentType.integer(-1,512))
                    .executes(context->{cfg.visitorBypassKey=IntegerArgumentType.getInteger(context,"keycode");save();local("§aVisitor bypass key updated.");return 1;})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word())
                        .executes(context->color(StringArgumentType.getString(context,"target"),StringArgumentType.getString(context,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reward")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("action",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("id",StringArgumentType.word())
                        .executes(context->reward(StringArgumentType.getString(context,"action"),StringArgumentType.getString(context,"id"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(context->option(StringArgumentType.getString(context,"name"),StringArgumentType.getString(context,"state")))))));
    }

    private static int status(){local("§eVisitor helper " + on(cfg.visitorHelper) + ", shopping " + on(cfg.visitorShoppingList) + ", rewards " + on(cfg.visitorRewardChat) + ", tracked §f" + VISITORS.size() + "§e visitors and §f" + SACKS.size() + "§e sack items.");local("§7Rare refuse " + on(cfg.visitorPreventRareRefuse) + ", new refuse " + on(cfg.visitorPreventNewRefuse) + ", copper " + cfg.visitorCoinsPerCopper + ", loss " + cfg.visitorAcceptableLoss + ".");return 1;}
    private static int reward(String action,String id){String value=id.toUpperCase(Locale.ROOT);if(action.equalsIgnoreCase("add")){if(!cfg.visitorWarnRewards.contains(value))cfg.visitorWarnRewards.add(value);}else if(action.equalsIgnoreCase("remove"))cfg.visitorWarnRewards.removeIf(v->v.equalsIgnoreCase(value));else{local("§cReward action must be add or remove.");return 0;}save();local("§aReward warning list updated.");return 1;}
    private static int color(String target,String input){Integer value=parseColor(input);if(value==null){local("§cColor must be an eight-digit ARGB hex value.");return 0;}switch(target.toLowerCase(Locale.ROOT)){case"blocked"->cfg.visitorBlockedColor=value;case"good"->cfg.visitorGoodOutlineColor=value;case"bad"->cfg.visitorBadOutlineColor=value;default->{local("§cColor target must be blocked, good, or bad.");return 0;}}save();local("§aVisitor color updated.");return 1;}
    private static int option(String name,String state){Boolean value=parseState(state);if(value==null){local("§cState must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.visitorHelper=value;case"shopping"->cfg.visitorShoppingList=value;case"garden"->cfg.visitorShowInGarden=value;case"hub"->cfg.visitorShowInHub=value;case"price"->cfg.visitorShowPrice=value;case"total"->cfg.visitorShowTotalPrice=value;case"sacks"->cfg.visitorShowSackCount=value;case"inventory"->cfg.visitorShowInventoryCount=value;case"visitors"->cfg.visitorShowVisitors=value;case"tooltip"->cfg.visitorTooltip=value;case"itemprices"->cfg.visitorTooltipItemPrices=value;case"copperprice"->cfg.visitorTooltipCopperPrice=value;case"xpprice"->cfg.visitorTooltipGardenExperiencePrice=value;case"profit"->cfg.visitorTooltipProfit=value;case"rewardchat"->cfg.visitorRewardChat=value;case"rarerefuse"->cfg.visitorPreventRareRefuse=value;case"newrefuse"->cfg.visitorPreventNewRefuse=value;case"cheapcopper"->cfg.visitorPreventCheapCopperRefuse=value;case"expensivecopper"->cfg.visitorPreventExpensiveCopperAccept=value;case"lowloss"->cfg.visitorPreventLowLossRefuse=value;case"highloss"->cfg.visitorPreventHighLossAccept=value;case"highlight"->cfg.visitorBlockedSlotHighlight=value;case"outline"->cfg.visitorOptionOutline=value;case"bypass"->cfg.visitorAllowHoldBypass=value;default->{local("§cUnknown visitor option.");return 0;}}save();local("§aVisitor option updated.");return 1;}
    private static Boolean parseState(String state){return switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes"->true;case"off","false","no"->false;default->null;};}
    private static Integer parseColor(String value){String clean=value.startsWith("#")?value.substring(1):value.startsWith("0x")||value.startsWith("0X")?value.substring(2):value;if(clean.length()!=8||!clean.matches("[0-9a-fA-F]{8}"))return null;try{return(int)Long.parseLong(clean,16);}catch(NumberFormatException ignored){return null;}}
    private static String on(boolean value){return value?"§aON":"§cOFF";}

    public record DisplayRow(String label,String value,int color){}
    private record ParsedAmount(String name,int amount){}
    private record Need(String id,String name,int amount,double price){}
    private record NeedTotal(String id,String name,int amount,double price){}
    private record Reason(boolean refuse,String text){}
    private record Visitor(String name,String fingerprint,List<Need> needs,List<String> rewards,List<String> rareRewards,int offersAccepted,int copper,int gardenXp,double requiredCost,double totalReward,boolean economyComplete,long parsedAt){double profit(){return totalReward-requiredCost;}}
}
