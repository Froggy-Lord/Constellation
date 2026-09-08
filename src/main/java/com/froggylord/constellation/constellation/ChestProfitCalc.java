package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.BazaarApi;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.data.DungeonState;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChestProfitCalc {

    private static final List<String> CHESTS = List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock");
    private static final Pattern ESSENCE = Pattern.compile("^(Wither|Undead) Essence x([\\d,]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY = Pattern.compile(" x([\\d,]+)$");
    private static final Pattern COINS = Pattern.compile("([\\d,]+) Coins");
    private static final Pattern CROESUS_FLOOR = Pattern.compile("^(Master )?Catacombs - Floor [IV]+$");
    private static final Pattern LORE_BOOK = Pattern.compile("^Enchanted Book \\(([\\w ]+) ([IVX]+)\\)$");
    private static final Map<String, String> SPECIAL_IDS = Map.ofEntries(
        Map.entry("WITHER_SHARD", "SHARD_WITHER"), Map.entry("THORN_SHARD", "SHARD_THORN"),
        Map.entry("APEX_DRAGON_SHARD", "SHARD_APEX_DRAGON"), Map.entry("POWER_DRAGON_SHARD", "SHARD_POWER_DRAGON"),
        Map.entry("SCARF_SHARD", "SHARD_SCARF"), Map.entry("NECROMANCERS_BROOCH", "NECROMANCER_BROOCH"),
        Map.entry("WITHER_SHIELD", "WITHER_SHIELD_SCROLL"), Map.entry("IMPLOSION", "IMPLOSION_SCROLL"),
        Map.entry("SHADOW_WARP", "SHADOW_WARP_SCROLL"), Map.entry("WARPED_STONE", "AOTE_STONE"),
        Map.entry("SPIRIT_STONE", "SPIRIT_DECOY"));
    private static final Map<String, ChestData> runChests = new LinkedHashMap<>();
    private static OrionConfig cfg;
    private static long lastErrorLog;

    private record Loot(String name, String id, int amount, boolean essence, double value) {}
    private record ChestData(String name, List<Loot> loot, double cost, double profit, int unknown) {}

    private ChestProfitCalc() {}

    public static void init(OrionConfig config) {
        cfg = config;
        ConstellationClient.bus().subscribe(DungeonState.DungeonEnter.class, ignored -> reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            String title = container.getTitle().getString();
            boolean croesus = title.equals("Croesus");
            boolean croesusFloor = CROESUS_FLOOR.matcher(title).matches();
            String chest = chestName(title);
            if (!croesus && !croesusFloor && chest == null) return;
            if (!validArea()) return;
            if (croesus) reset();
            ScreenEvents.afterExtract(screen).register((scr, graphics, mx, my, delta) -> {
                if (cfg == null || !cfg.chestProfitCalc) return;
                try {
                    if (croesus) croesus(container, graphics);
                    else if (croesusFloor) croesusProfits(container, graphics);
                    else calculate(container, graphics, chest);
                } catch (Exception error) {
                    if (System.currentTimeMillis() - lastErrorLog > 10_000) {
                        lastErrorLog = System.currentTimeMillis();
                        ConstellationClient.LOGGER.warn("Could not read dungeon chest rewards", error);
                    }
                }
            });
        });
    }

    private static boolean validArea() {
        return ConstellationClient.loc().inDungeons()
            || ConstellationClient.loc().area() == SkyblockArea.DUNGEON_HUB;
    }

    private static String chestName(String title) {
        for (String chest : CHESTS)
            if (title.equals(chest) || title.equals(chest + " Chest")) return chest;
        return null;
    }

    // loot slots, essence handling and cost parsing ported from devonian (GPL-3.0):
    // features/dungeons/ChestProfit.kt
    // market-id and liquidation pricing cross-checked with NoFrills (GPL-3.0):
    // features/dungeons/DungeonChestValue.java and misc/Utils.java
    private static void calculate(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, String chestName) {
        BazaarApi.ensureFresh();
        List<Loot> loot = new ArrayList<>();
        int unknown = 0;
        int containerSlots = Math.max(0, screen.getMenu().slots.size() - 36);
        for (int slot = 9; slot <= 17 && slot < containerSlots; slot++) {
            ItemStack stack = screen.getMenu().slots.get(slot).getItem();
            String vanillaId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (stack.isEmpty() || vanillaId.equals("black_stained_glass_pane")
                || vanillaId.equals("gray_stained_glass_pane")) continue;
            String name = stack.getHoverName().getString();
            MarketItem market = marketItem(stack, name);
            if (market == null) { unknown++; continue; }
            if (market.essence() && !cfg.chestProfitUseEssence) {
                loot.add(new Loot(name, market.id(), market.amount(), true, 0));
                continue;
            }
            double unit = PriceProvider.sellValue(market.id());
            if (unit <= 0) {
                PriceProvider.warm(market.id());
                unknown++;
            }
            loot.add(new Loot(name, market.id(), market.amount(), market.essence(), unit * market.amount()));
        }

        double cost = cost(screen, containerSlots);
        double gross = loot.stream().mapToDouble(Loot::value).sum();
        ChestData data = new ChestData(chestName, List.copyOf(loot), cost, gross - cost, unknown);
        if (!loot.isEmpty() || cost > 0) runChests.put(chestName, data);
        drawCurrent(screen, graphics, data);
    }

    private record MarketItem(String id, int amount, boolean essence) {}

    private static MarketItem marketItem(ItemStack stack, String name) {
        String clean = name.replaceAll("§.", "");
        Matcher essence = ESSENCE.matcher(clean);
        if (essence.matches()) {
            int amount = parseInt(essence.group(2), 1);
            return new MarketItem("ESSENCE_" + essence.group(1).toUpperCase(Locale.ROOT), amount, true);
        }

        CompoundTag extra = extra(stack);
        String id = extra.getStringOr("id", "");
        if (id.isEmpty()) return null;
        if (id.equals("ENCHANTED_BOOK")) {
            CompoundTag enchantments = extra.getCompoundOrEmpty("enchantments");
            if (enchantments.keySet().size() != 1) return null;
            String enchantment = enchantments.keySet().iterator().next();
            int level = enchantments.getIntOr(enchantment, 0);
            if (level <= 0 || !enchantment.matches("[a-z0-9_]+")) return null;
            id = "ENCHANTMENT_" + enchantment.toUpperCase(Locale.ROOT) + "_" + level;
        }
        // ported from NoFrills (GPL-3.0): misc/Utils.java
        if (extra.getIntOr("baseStatBoostPercentage", 0) == 50) {
            id += "_MAX_BOOST_TIER_" + extra.getIntOr("item_tier", 0);
        }
        int amount = stack.getCount();
        Matcher quantity = QUANTITY.matcher(clean);
        if (quantity.find()) amount = parseInt(quantity.group(1), amount);
        return new MarketItem(id, Math.max(1, amount), false);
    }

    private static double cost(AbstractContainerScreen<?> screen, int containerSlots) {
        if (containerSlots <= 31) return 0;
        ItemStack open = screen.getMenu().slots.get(31).getItem();
        if (open.isEmpty()) return 0;
        ItemLore lore = open.get(DataComponents.LORE);
        if (lore == null) return 0;
        List<String> lines = lore.lines().stream().map(line -> line.getString().replaceAll("§.", "")).toList();
        int costIndex = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).trim().equals("Cost")) { costIndex = i; break; }
        if (costIndex < 0) return 0;
        double cost = 0;
        for (int i = costIndex + 1; i < Math.min(lines.size(), costIndex + 3); i++) {
            String line = lines.get(i).trim();
            Matcher coins = COINS.matcher(line);
            if (coins.find()) cost += parseInt(coins.group(1), 0);
            if (cfg.chestProfitSubtractKey && line.equals("Dungeon Chest Key")) {
                double key = PriceProvider.purchaseValue("DUNGEON_CHEST_KEY");
                if (key > 0) cost += key;
                else PriceProvider.warm("DUNGEON_CHEST_KEY");
            }
        }
        return cost;
    }

    private static void drawCurrent(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, ChestData data) {
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        int x = accessor.constellation$left() + accessor.constellation$imageWidth() + 8;
        int y = accessor.constellation$top() + 5;
        var font = Minecraft.getInstance().font;
        int width = 128;
        int rows = 3 + (cfg.chestProfitCompact ? 0 : data.loot().size()) + (data.unknown() > 0 && cfg.chestProfitShowUnknown ? 1 : 0);
        graphics.fill(x - 4, y - 4, x + width, y + rows * (font.lineHeight + 2) + 2, 0xD0101018);
        graphics.text(font, "§6" + data.name() + " Chest", x, y, 0xFFFFAA00, true);
        y += font.lineHeight + 2;
        if (!cfg.chestProfitCompact) {
            for (Loot item : data.loot()) {
                String value = item.value() > 0 ? money(item.value()) : "not counted";
                graphics.text(font, "§7" + trim(item.name(), 17) + " §f" + value, x, y, 0xFFFFFFFF, true);
                y += font.lineHeight + 2;
            }
        }
        graphics.text(font, "§7Cost §f" + money(data.cost()), x, y, 0xFFFFFFFF, true);
        y += font.lineHeight + 2;
        int colour = data.profit() >= 0 ? 0xFF55FF55 : 0xFFFF5555;
        String profitLabel = data.unknown() > 0 ? "Minimum profit" : "Profit";
        graphics.text(font, "§b" + profitLabel + " §f" + signedMoney(data.profit()), x, y, colour, true);
        y += font.lineHeight + 2;
        if (data.unknown() > 0 && cfg.chestProfitShowUnknown)
            graphics.text(font, "§e" + data.unknown() + " unknown price" + (data.unknown() == 1 ? "" : "s"), x, y, 0xFFFFFF55, true);
    }

    public static String hudText() {
        if (cfg == null || !cfg.chestProfitCalc || runChests.isEmpty() || !validArea()) return null;
        ChestData best = runChests.values().stream().max(Comparator.comparingDouble(ChestData::profit)).orElse(null);
        if (best == null) return null;
        return best.name() + " " + signedMoney(best.profit()) + (best.unknown() > 0 ? "+?" : "")
            + " §7| §f" + runChests.size() + " viewed";
    }

    // Croesus head-lore parsing, special item IDs and top-two highlighting ported from
    // devonian (GPL-3.0): features/dungeons/CroesusProfit.kt
    private static void croesusProfits(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        BazaarApi.ensureFresh();
        int left = ((ContainerScreenAccessor) screen).constellation$left();
        int top = ((ContainerScreenAccessor) screen).constellation$top();
        List<Map.Entry<Integer, ChestData>> parsed = new ArrayList<>();
        int containerSlots = Math.max(0, screen.getMenu().slots.size() - 36);
        for (int slotIndex = 9; slotIndex <= 18 && slotIndex < containerSlots; slotIndex++) {
            var slot = screen.getMenu().slots.get(slotIndex);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) continue;
            String name = chestName(stack.getHoverName().getString());
            if (name == null) continue;
            ChestData data = parseCroesusHead(name, stack);
            if (data == null) continue;
            parsed.add(Map.entry(slotIndex, data));
            runChests.put(name, data);
        }
        parsed.sort((a, b) -> Double.compare(b.getValue().profit(), a.getValue().profit()));
        for (int rank = 0; rank < parsed.size(); rank++) {
            var entry = parsed.get(rank);
            var slot = screen.getMenu().slots.get(entry.getKey());
            ChestData data = entry.getValue();
            if (rank == 0 && data.profit() > 0)
                graphics.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, 0x7055FF55);
            else if (rank == 1 && data.profit() > 0)
                graphics.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, 0x70FFFF55);
            String value = signedMoney(data.profit()) + (data.unknown() > 0 ? "+?" : "");
            graphics.pose().pushMatrix();
            graphics.pose().translate(left + slot.x + 8, top + slot.y + 18);
            graphics.pose().scale(.5f, .5f);
            int colour = data.profit() >= 0 ? 0xFF55FF55 : 0xFFFF5555;
            graphics.text(Minecraft.getInstance().font, value, -Minecraft.getInstance().font.width(value) / 2, 0, colour, true);
            graphics.pose().popMatrix();
        }
    }

    private static ChestData parseCroesusHead(String chestName, ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        List<String> lines = lore.lines().stream().map(line -> line.getString().replaceAll("§.", "").trim()).toList();
        if (lines.stream().anyMatch(line -> line.equals("Already opened!"))) return null;
        List<Loot> loot = new ArrayList<>();
        int unknown = 0;
        boolean contents = false;
        double cost = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.equals("Contents")) { contents = true; continue; }
            if (line.equals("Cost")) {
                contents = false;
                for (int j = i + 1; j < Math.min(lines.size(), i + 3); j++) {
                    Matcher coins = COINS.matcher(lines.get(j));
                    if (coins.find()) cost += parseInt(coins.group(1), 0);
                    if (cfg.chestProfitSubtractKey && lines.get(j).equals("Dungeon Chest Key")) {
                        double key = PriceProvider.purchaseValue("DUNGEON_CHEST_KEY");
                        if (key > 0) cost += key;
                        else PriceProvider.warm("DUNGEON_CHEST_KEY");
                    }
                }
                break;
            }
            if (!contents || line.isBlank()) continue;
            MarketItem market = marketFromLore(line);
            if (market == null) { unknown++; continue; }
            double unit = market.essence() && !cfg.chestProfitUseEssence ? 0 : PriceProvider.sellValue(market.id());
            if (unit <= 0 && !(market.essence() && !cfg.chestProfitUseEssence)) {
                PriceProvider.warm(market.id());
                unknown++;
            }
            loot.add(new Loot(line, market.id(), market.amount(), market.essence(), unit * market.amount()));
        }
        if (loot.isEmpty() && cost == 0) return null;
        double gross = loot.stream().mapToDouble(Loot::value).sum();
        return new ChestData(chestName, List.copyOf(loot), cost, gross - cost, unknown);
    }

    private static MarketItem marketFromLore(String line) {
        Matcher book = LORE_BOOK.matcher(line);
        if (book.matches()) {
            int level = roman(book.group(2));
            String name = book.group(1).replace(' ', '_').toUpperCase(Locale.ROOT);
            String regular = "ENCHANTMENT_" + name + "_" + level;
            if (PriceProvider.sellValue(regular) > 0) return new MarketItem(regular, 1, false);
            return new MarketItem("ENCHANTMENT_ULTIMATE_" + name + "_" + level, 1, false);
        }
        Matcher essence = ESSENCE.matcher(line);
        if (essence.matches()) return new MarketItem("ESSENCE_" + essence.group(1).toUpperCase(Locale.ROOT),
            parseInt(essence.group(2), 1), true);
        String id = line.toUpperCase(Locale.ROOT).replace("- ", "").replace("'", "").replace(' ', '_');
        id = SPECIAL_IDS.getOrDefault(id, id);
        return id.isBlank() ? null : new MarketItem(id, 1, false);
    }

    private static int roman(String value) {
        int total = 0, previous = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            int current = switch (value.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; default -> 0; };
            if (current < previous) total -= current; else { total += current; previous = current; }
        }
        return total;
    }

    // Croesus state ported from devonian (GPL-3.0): api/dungeon/CroesusListener.kt
    // and Odin (BSD-3-Clause): features/impl/dungeon/Croesus.kt
    private static void croesus(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        int left = ((ContainerScreenAccessor) screen).constellation$left();
        int top = ((ContainerScreenAccessor) screen).constellation$top();
        int unopened = 0, opened = 0, finished = 0;
        int chestSlots = Math.min(45, screen.getMenu().slots.size() - 36);
        for (int i = 0; i < chestSlots; i++) {
            var slot = screen.getMenu().slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            boolean hasOpened = false, hasNoChest = false, canOpen = false;
            for (var line : lore.lines()) {
                String value = line.getString();
                if (value.contains("Opened Chest: ")) hasOpened = true;
                else if (value.contains("No more chests to open!")) hasNoChest = true;
                else if (value.equals("No chests opened yet!")) canOpen = true;
            }
            int colour;
            if (hasNoChest) { finished++; colour = 0x60FF5555; }
            else if (hasOpened) { opened++; colour = 0x60FFAA00; }
            else if (canOpen) { unopened++; colour = 0x6055FF55; }
            else continue;
            graphics.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, colour);
        }
        if (unopened + opened + finished == 0) return;
        graphics.text(Minecraft.getInstance().font,
            "§aUnopened " + unopened + " §6Opened " + opened + " §cDone " + finished,
            left + 8, top - 12, 0xFFFFFFFF, true);
    }

    private static CompoundTag extra(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes");
    }

    private static int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw.replace(",", "")); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static String trim(String value, int max) {
        String clean = value.replaceAll("§.", "");
        return clean.length() <= max ? clean : clean.substring(0, max - 3) + "...";
    }

    private static String signedMoney(double value) {
        return (value >= 0 ? "+" : "-") + money(Math.abs(value));
    }

    private static String money(double value) {
        if (value < 1_000) return String.format(Locale.ROOT, "%.0f", value);
        if (value < 1_000_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        if (value < 1_000_000_000) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
        return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
    }

    private static void reset() {
        runChests.clear();
    }
}
