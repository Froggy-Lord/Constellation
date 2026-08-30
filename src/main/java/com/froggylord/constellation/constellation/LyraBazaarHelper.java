package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/bazaar/BazaarQuickQuantities.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/bazaar/BazaarHelper.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/bazaar/BazaarOrderTracker.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/bazaar/ReorderHelper.java
public final class LyraBazaarHelper {
    private static final Pattern ORDERS_TITLE = Pattern.compile("(?:Co-op|Your) Bazaar Orders");
    private static final Pattern FILLED = Pattern.compile("Filled: .*?([\\d.]+)%.*");
    private static final Pattern UNIT_PRICE = Pattern.compile("Price per unit: ([0-9,.]+) coins");
    private static final Pattern ORDER_AMOUNT = Pattern.compile("(?:Order|Offer) amount: ([0-9,]+)x");
    private static final Pattern LADDER = Pattern.compile("- ([0-9,.]+) coins? each \\| ([0-9,]+)x (?:in|from) [0-9,]+ (?:order|offer)s?");
    private static final Pattern BUY_MISSING = Pattern.compile("([0-9,]+)x missing items\\.");
    private static final Pattern SELL_ITEMS = Pattern.compile("([0-9,]+)x items\\.");
    private static final Map<Integer, Order> ORDERS = new HashMap<>();
    private static LyraConfig cfg;
    private static long orderSnapshotAt;
    private static boolean reorderCopied;
    private static AbstractContainerScreen<?> observedOrdersScreen;
    private static String orderProfile = "";

    private LyraBazaarHelper() {}

    public static void init(LyraConfig config) {
        cfg = config;
        normalize();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.bazaarOrderHelper && ConstellationClient.loc().onHypixel();
    }

    public static List<Button> quantityButtons(AbstractSignEditScreen screen, String[] messages) {
        // ported from Skyblocker (LGPL-3.0-or-later): mixins/AbstractSignEditScreenMixin.java init
        if (!active() || !cfg.bazaarQuickQuantities || !isBazaarQuantitySign(messages)) return List.of();
        List<String> values = new ArrayList<>();
        values.add(Integer.toString(cfg.bazaarQuickQuantity1));
        values.add(Integer.toString(cfg.bazaarQuickQuantity2));
        values.add(Integer.toString(cfg.bazaarQuickQuantity3));
        if (cfg.bazaarQuickClipboard) {
            String clipboard = numericClipboard();
            if (clipboard != null && !values.contains(clipboard)) values.add(clipboard);
        }
        int buttonWidth = 54;
        int x = Math.clamp(screen.width / 2 + 50, 4, Math.max(4, screen.width - buttonWidth - 4));
        int y = Math.clamp(screen.height / 2 - 55, 20, Math.max(20, screen.height - values.size() * 22 - 24));
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            Component label = i == 3 ? Component.literal("C: " + value).withStyle(ChatFormatting.AQUA) : Component.literal(value);
            buttons.add(Button.builder(label, button -> selectQuantity(screen, messages, value))
                .bounds(x, y + i * 22, buttonWidth, 20).build());
        }
        return buttons;
    }

    private static boolean isBazaarQuantitySign(String[] messages) {
        if (messages == null || messages.length < 4) return false;
        boolean input = messages[1].equals("^^^^^^^^^^^^^^^") || messages[1].equals("^^^^^^") || messages[1].equals("^^Flipping^^");
        boolean search = messages[2].endsWith("your") || messages[2].endsWith("query") || messages[2].equals("Enter name");
        return input && !search && messages[3].equals("to order");
    }

    private static String numericClipboard() {
        String value = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (value == null || value.isBlank() || value.length() > 10 || !value.chars().allMatch(Character::isDigit)) return null;
        try { int parsed = Integer.parseInt(value); return parsed > 0 ? Integer.toString(parsed) : null; }
        catch (NumberFormatException ignored) { return null; }
    }

    private static void selectQuantity(AbstractSignEditScreen screen, String[] messages, String value) {
        messages[0] = value;
        if (cfg.bazaarQuickCloseOnUse) screen.onClose();
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        // ported from Skyblocker (LGPL-3.0-or-later): skyblock/bazaar/BazaarHelper.java getText
        if (!scope() || slot == null) return;
        String title = screen.getTitle().getString().strip();
        if (!ORDERS_TITLE.matcher(title).matches()) { reorderCopied = false; return; }
        if (observedOrdersScreen != screen) {
            ORDERS.clear();
            observedOrdersScreen = screen;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || slot.container == mc.player.getInventory()) return;
        if (slot.getItem().isEmpty()) { ORDERS.remove(slot.index); return; }
        if (!validOrderSlot(slot)) return;
        processOrder(slot);
        if (!cfg.bazaarOrderStatus) return;
        Status status = status(slot.getItem());
        if (status == null) return;
        String marker = switch (status) { case FULL -> "F"; case PARTIAL -> "%"; case EXPIRED, EXPIRING -> "!"; };
        int color = switch (status) { case FULL -> cfg.bazaarFilledColor; case PARTIAL -> cfg.bazaarPartialColor; case EXPIRED -> cfg.bazaarExpiredColor; case EXPIRING -> cfg.bazaarExpiringColor; };
        graphics.text(Minecraft.getInstance().font, marker, slot.x + 1, slot.y + 1, color, true);
    }

    private static boolean validOrderSlot(Slot slot) {
        int size = slot.container.getContainerSize();
        if (slot.index < 10 || slot.index > size - 11) return false;
        int column = slot.index % 9;
        return column != 0 && column != 8;
    }

    private static Status status(ItemStack stack) {
        List<String> lore = lore(stack);
        if (cfg.bazaarOrderExpiredMarker && lore.stream().anyMatch(line -> line.equals("Expired!"))) return Status.EXPIRED;
        if (cfg.bazaarOrderExpiringMarker && lore.stream().anyMatch(line -> line.startsWith("Expires in"))) return Status.EXPIRING;
        if (!cfg.bazaarOrderFilledMarker || lore.isEmpty() || !lore.getLast().equals("Click to claim!")) return null;
        Matcher matcher = first(lore, FILLED);
        if (matcher == null) return null;
        try { return Double.parseDouble(matcher.group(1)) >= 100 ? Status.FULL : Status.PARTIAL; }
        catch (NumberFormatException ignored) { return null; }
    }

    private static void processOrder(Slot slot) {
        // ported from Skyblocker (LGPL-3.0-or-later): skyblock/bazaar/BazaarOrderTracker.java processOrder
        if (!cfg.bazaarOrderTracker) { ORDERS.clear(); return; }
        ORDERS.remove(slot.index);
        ItemStack stack = slot.getItem();
        List<String> lore = lore(stack);
        Matcher price = first(lore, UNIT_PRICE), amount = first(lore, ORDER_AMOUNT);
        String id = LyraTooltips.marketId(stack);
        if (price == null || amount == null || id.isBlank()) return;
        BigDecimal parsedPrice = decimal(price.group(1));
        Long parsedAmount = integer(amount.group(1));
        if (parsedPrice == null || parsedAmount == null || parsedAmount <= 0) return;
        ORDERS.put(slot.index, new Order(id, parsedPrice, parsedAmount, slot.index < 18));
        orderSnapshotAt = System.currentTimeMillis();
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, ItemStack stack, List<Component> original) {
        // ported from Skyblocker (LGPL-3.0-or-later): skyblock/bazaar/BazaarOrderTracker.java addOrderMarker
        if (!scope() || !cfg.bazaarOrderTracker || original == null || ORDERS.isEmpty()) return original;
        if (System.currentTimeMillis() - orderSnapshotAt > 30 * 60_000L) { ORDERS.clear(); return original; }
        String name = plain(stack.getHoverName());
        boolean sell;
        if (stack.is(Items.FILLED_MAP) && name.equals("Create Buy Order")) sell = false;
        else if (stack.is(Items.MAP) && name.equals("Create Sell Offer")) sell = true;
        else return original;
        if (screen.getMenu().slots.size() <= 13) return original;
        String id = LyraTooltips.marketId(screen.getMenu().getSlot(13).getItem());
        if (id.isBlank()) return original;
        List<Order> matching = ORDERS.values().stream().filter(order -> order.sell == sell && order.itemId.equals(id))
            .sorted(Comparator.comparing(Order::unitPrice)).toList();
        if (!sell) matching = matching.reversed();
        if (matching.isEmpty()) return original;
        List<Component> lines = new ArrayList<>(original);
        int orderIndex = 0;
        for (int lineIndex = 0; lineIndex < lines.size() && orderIndex < matching.size(); lineIndex++) {
            Matcher ladder = LADDER.matcher(plain(lines.get(lineIndex)));
            if (!ladder.matches()) continue;
            BigDecimal price = decimal(ladder.group(1));
            if (price == null) continue;
            while (orderIndex < matching.size() && (sell ? matching.get(orderIndex).unitPrice.compareTo(price) < 0 : matching.get(orderIndex).unitPrice.compareTo(price) > 0)) orderIndex++;
            int count = 0;
            long amount = 0;
            while (orderIndex < matching.size() && matching.get(orderIndex).unitPrice.compareTo(price) == 0) {
                count++; amount += matching.get(orderIndex).amount; orderIndex++;
            }
            if (count == 0) continue;
            String detail = "Your" + (cfg.bazaarOrderTrackerShowAmount ? " " + amount + " items" : "")
                + (cfg.bazaarOrderTrackerShowAmount && cfg.bazaarOrderTrackerShowCount ? " in" : "")
                + (cfg.bazaarOrderTrackerShowCount ? " " + count + (count == 1 ? " order" : " orders") : "");
            lines.add(++lineIndex, Component.literal("  - " + detail).withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }

    public static void onSlotClick(AbstractContainerScreen<?> screen, Slot slot, int slotId) {
        // ported from Skyblocker (LGPL-3.0-or-later): skyblock/bazaar/ReorderHelper.java onClickSlot
        if (!scope() || !cfg.bazaarReorderClipboard || slot == null || !screen.getTitle().getString().strip().equals("Order options")) return;
        if (slotId != 11 && slotId != 13 || !slot.getItem().is(Items.DYED_TERRACOTTA.green())) return;
        if (!InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) { reorderCopied = false; return; }
        Pattern pattern = slotId == 13 ? SELL_ITEMS : BUY_MISSING;
        Matcher matcher = first(lore(slot.getItem()), pattern);
        if (matcher == null) return;
        String amount = matcher.group(1).replace(",", "");
        if (reorderCopied) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(amount);
        reorderCopied = true;
        local("§aCopied reorder quantity §f" + amount + "§a.");
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bazaarhelper")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("preset")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("slot", IntegerArgumentType.integer(1, 3))
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("quantity", IntegerArgumentType.integer(1))
                        .executes(context -> preset(IntegerArgumentType.getInteger(context, "slot"), IntegerArgumentType.getInteger(context, "quantity"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() { local("§eBazaar helper " + on(cfg.bazaarOrderHelper) + ", quantities " + on(cfg.bazaarQuickQuantities) + " §f" + cfg.bazaarQuickQuantity1 + "/" + cfg.bazaarQuickQuantity2 + "/" + cfg.bazaarQuickQuantity3 + "§e, status " + on(cfg.bazaarOrderStatus) + ", tracker " + on(cfg.bazaarOrderTracker) + ", reorder copy " + on(cfg.bazaarReorderClipboard) + "."); return 1; }
    private static int preset(int slot, int quantity) { if (slot == 1) cfg.bazaarQuickQuantity1 = quantity; else if (slot == 2) cfg.bazaarQuickQuantity2 = quantity; else cfg.bazaarQuickQuantity3 = quantity; save(); local("§aBazaar quantity preset updated."); return 1; }
    private static int option(String name, String state) {
        Boolean enabled = parseState(state); if (enabled == null) { local("§cState must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.bazaarOrderHelper = enabled;
            case "quantities", "quick" -> cfg.bazaarQuickQuantities = enabled;
            case "clipboard" -> cfg.bazaarQuickClipboard = enabled;
            case "close" -> cfg.bazaarQuickCloseOnUse = enabled;
            case "status" -> cfg.bazaarOrderStatus = enabled;
            case "filled" -> cfg.bazaarOrderFilledMarker = enabled;
            case "expired" -> cfg.bazaarOrderExpiredMarker = enabled;
            case "expiring" -> cfg.bazaarOrderExpiringMarker = enabled;
            case "tracker" -> cfg.bazaarOrderTracker = enabled;
            case "amount" -> cfg.bazaarOrderTrackerShowAmount = enabled;
            case "count" -> cfg.bazaarOrderTrackerShowCount = enabled;
            case "reorder" -> cfg.bazaarReorderClipboard = enabled;
            default -> { local("§cOption must be enabled, quantities, clipboard, close, status, filled, expired, expiring, tracker, amount, count, or reorder."); return 0; }
        }
        save(); local("§aBazaar-helper option updated."); return 1;
    }

    private static void normalize() { if (cfg == null) return; cfg.bazaarQuickQuantity1 = Math.max(1, cfg.bazaarQuickQuantity1); cfg.bazaarQuickQuantity2 = Math.max(1, cfg.bazaarQuickQuantity2); cfg.bazaarQuickQuantity3 = Math.max(1, cfg.bazaarQuickQuantity3); }
    private static void save() { normalize(); ConstellationClient.saveConfig(); }
    private static boolean scope() {
        if (!active()) { clear(); return false; }
        String profile = LyraStorageValue.currentProfileKey();
        if (profile.isBlank()) { clear(); return false; }
        if (!profile.equals(orderProfile)) { clear(); orderProfile = profile; }
        return true;
    }
    private static void clear() { ORDERS.clear(); orderSnapshotAt = 0; reorderCopied = false; observedOrdersScreen = null; orderProfile = ""; }
    private static List<String> lore(ItemStack stack) { ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE); if (lore == null) return List.of(); return lore.lines().stream().map(LyraBazaarHelper::plain).toList(); }
    private static Matcher first(List<String> lines, Pattern pattern) { for (String line : lines) { Matcher matcher = pattern.matcher(line); if (matcher.matches()) return matcher; } return null; }
    private static BigDecimal decimal(String raw) { try { return new BigDecimal(raw.replace(",", "")); } catch (NumberFormatException ignored) { return null; } }
    private static Long integer(String raw) { try { return Long.parseLong(raw.replace(",", "")); } catch (NumberFormatException ignored) { return null; } }
    private static String plain(Component component) { String text = ChatFormatting.stripFormatting(component.getString()); return text == null ? component.getString() : text; }
    private static Boolean parseState(String state) { return switch (state.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean enabled) { return enabled ? "§aon" : "§coff"; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f" + text)); }

    private enum Status { FULL, PARTIAL, EXPIRED, EXPIRING }
    private record Order(String itemId, BigDecimal unitPrice, long amount, boolean sell) {}
}
