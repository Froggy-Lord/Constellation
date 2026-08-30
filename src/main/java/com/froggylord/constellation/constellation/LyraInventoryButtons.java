package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.froggylord.constellation.ui.InventoryButtonEditorScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// ported from SkyOcean (MIT): features/inventory/buttons/{InvButtons,InvButton}.kt and config/features/inventory/Buttons.kt
// ported from Cryptkit (GPL-3.0-only): gui/QuickButtons.java and mixin/InventoryClickMixin.java
public final class LyraInventoryButtons {
    private static final int BUTTONS_PER_ROW = 7;
    private static final List<LyraConfig.InventoryButtonEntry> DEFAULTS = List.of(
        entry("minecraft:diamond_sword", "Skills", "Your Skills", "Skills"),
        entry("minecraft:painting", "Collections", "Collections", "Collections"),
        entry("minecraft:bone", "Pets", "(?:\\(\\d+/\\d+\\) )?Pets(?: \\(\\d+/\\d+\\))?", "Pets"),
        entry("minecraft:leather_chestplate", "Wardrobe", "(?:Wardrobe )?\\(\\d+/\\d+\\)(?: Armor Sets)?", "Wardrobe"),
        entry("minecraft:bundle", "Sacks", "Sack of Sacks", "Sacks"),
        entry("minecraft:ender_eye", "Accessories", "Accessory Bag(?: \\(\\d+/\\d+\\))?", "Accessories"),
        entry("minecraft:ender_chest", "Storage", "Storage", "Storage"),
        entry("minecraft:grass_block", "warp island", "a^", "Island"),
        entry("minecraft:nether_star", "warp hub", "a^", "Hub"),
        entry("minecraft:skeleton_skull", "warp dh", "a^", "Dungeon Hub"),
        entry("minecraft:cookie", "ChocolateFactory", "Chocolate Factory", "Chocolate Factory"),
        entry("minecraft:emerald", "Bazaar", "(?:Special )?Bazaar", "Bazaar"),
        entry("minecraft:gold_ingot", "Auction", "(?:Co-op )?Auction House", "Auction House"),
        entry("minecraft:crafting_table", "CraftingTable", "Craft Item", "Crafting Table")
    );

    private static LyraConfig cfg;
    private static int hoveredIndex = -1;
    private static long hoveredSinceNanos;

    private LyraInventoryButtons() {}

    public static void init(LyraConfig config) {
        cfg = config;
        normalize();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !show(container)) return;
            ScreenEvents.afterExtract(container).register((ignored, graphics, mouseX, mouseY, delta) -> draw(container, graphics, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(container).register((ignored, event) -> !click(container, event));
            ScreenEvents.remove(container).register(ignored -> clearHover());
        });
    }

    private static boolean show(AbstractContainerScreen<?> screen) {
        if (cfg == null || !cfg.enabled || !cfg.inventoryButtons || !ConstellationClient.loc().onHypixel()) return false;
        if (cfg.inventoryButtonsOnlyPlayerInventory && !(screen instanceof InventoryScreen)) return false;
        return !cfg.inventoryButtonsHideInCreative || !(screen instanceof CreativeModeInventoryScreen);
    }

    private static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!show(screen)) return;
        int nowHovered = -1;
        String title = screen.getTitle().getString().strip();
        for (int i = 0; i < entries().size(); i++) {
            LyraConfig.InventoryButtonEntry button = entries().get(i);
            if (!button.enabled || !rowVisible(i)) continue;
            Rect rect = rect(screen, i);
            boolean hover = interactiveContains(rect, i, mouseX, mouseY);
            boolean selected = cfg.inventoryButtonsHighlightCurrent && titleMatches(button, title);
            if (hover) nowHovered = i;
            int shift = cfg.inventoryButtonsHoverAnimation && (hover || selected) ? (i < BUTTONS_PER_ROW ? -4 : 4) : 0;
            int y = rect.y + shift;
            int color = selected ? cfg.inventoryButtonsHighlightColor : hover ? cfg.inventoryButtonsHoverColor : cfg.inventoryButtonsColor;
            graphics.fill(rect.x, y, rect.x + rect.w, y + rect.h, color);
            int border = selected ? 0xFFFFAAFF : hover ? 0xFFB07CFF : 0xFF4A4058;
            graphics.fill(rect.x, y, rect.x + rect.w, y + 1, border);
            graphics.fill(rect.x, y + rect.h - 1, rect.x + rect.w, y + rect.h, 0xFF080810);
            graphics.fill(rect.x, y, rect.x + 1, y + rect.h, border);
            graphics.fill(rect.x + rect.w - 1, y, rect.x + rect.w, y + rect.h, border);
            ItemStack icon = icon(button.icon);
            graphics.item(icon, rect.x + (rect.w - 16) / 2, y + (rect.h - 16) / 2);
        }
        updateHover(nowHovered);
        if (cfg.inventoryButtonsShowTooltips && nowHovered >= 0 && elapsedHoverMs() >= cfg.inventoryButtonsTooltipDelayMs)
            tooltip(graphics, entries().get(nowHovered), mouseX, mouseY);
    }

    private static boolean click(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        if (!show(screen)) return false;
        int index = hit(screen, (int) event.x(), (int) event.y());
        if (index < 0) return false;
        if (event.button() == 1) {
            Minecraft.getInstance().setScreenAndShow(new InventoryButtonEditorScreen(screen, index));
            return true;
        }
        if (event.button() != 0) return true;
        execute(entries().get(index).command);
        return true;
    }

    private static void execute(String raw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        String command = sanitize(raw);
        if (command.isBlank()) { local("§cThis inventory button has no command."); return; }
        mc.player.connection.sendCommand(command);
        if (cfg.inventoryButtonsCloseAfterCommand) mc.setScreenAndShow(null);
    }

    private static Rect rect(AbstractContainerScreen<?> screen, int index) {
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        int size = Math.clamp(cfg.inventoryButtonsSize, 18, 32);
        int gap = Math.clamp(cfg.inventoryButtonsGap, -4, 12);
        int total = BUTTONS_PER_ROW * size + (BUTTONS_PER_ROW - 1) * gap;
        int startX = accessor.constellation$left() + (accessor.constellation$imageWidth() - total) / 2;
        int row = index / BUTTONS_PER_ROW, column = index % BUTTONS_PER_ROW;
        int x = startX + column * (size + gap);
        int offset = Math.clamp(cfg.inventoryButtonsOffset, 0, 24);
        int y = row == 0 ? accessor.constellation$top() - size + offset : accessor.constellation$top() + accessor.constellation$imageHeight() - offset;
        return new Rect(x, y, size, size);
    }

    private static int hit(AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
        for (int i = 0; i < entries().size(); i++) {
            if (!entries().get(i).enabled || !rowVisible(i)) continue;
            Rect rect = rect(screen, i);
            if (interactiveContains(rect, i, mouseX, mouseY)) return i;
        }
        return -1;
    }

    private static boolean rowVisible(int index) { return index < BUTTONS_PER_ROW ? cfg.inventoryButtonsTop : cfg.inventoryButtonsBottom; }

    private static boolean interactiveContains(Rect rect, int index, int mouseX, int mouseY) {
        if (rect.contains(mouseX, mouseY)) return true;
        if (!cfg.inventoryButtonsHoverAnimation) return false;
        int shift = index < BUTTONS_PER_ROW ? -4 : 4;
        return new Rect(rect.x, rect.y + shift, rect.w, rect.h).contains(mouseX, mouseY);
    }

    private static void tooltip(GuiGraphicsExtractor graphics, LyraConfig.InventoryButtonEntry button, int mouseX, int mouseY) {
        String text = button.tooltip.isBlank() ? sanitize(button.command) : button.tooltip;
        if (text.isBlank()) text = "Unconfigured button";
        int width = Minecraft.getInstance().font.width(text);
        int x = Math.min(mouseX + 9, Minecraft.getInstance().getWindow().getGuiScaledWidth() - width - 8);
        int y = Math.max(4, mouseY - 15);
        graphics.fill(x - 3, y - 3, x + width + 3, y + 11, 0xF0101018);
        graphics.text(Minecraft.getInstance().font, text, x, y, 0xFFFFFFFF, true);
    }

    private static void updateHover(int index) {
        if (index == hoveredIndex) return;
        hoveredIndex = index;
        hoveredSinceNanos = System.nanoTime();
    }

    private static long elapsedHoverMs() { return hoveredIndex < 0 ? 0 : (System.nanoTime() - hoveredSinceNanos) / 1_000_000L; }
    private static void clearHover() { hoveredIndex = -1; hoveredSinceNanos = 0; }

    private static boolean titleMatches(LyraConfig.InventoryButtonEntry button, String title) {
        if (button.title.isBlank()) return false;
        try { return Pattern.compile(button.title).matcher(title).matches(); }
        catch (PatternSyntaxException ignored) { return title.equalsIgnoreCase(button.title); }
    }

    private static ItemStack icon(String raw) {
        Identifier id = Identifier.tryParse(raw == null ? "" : raw.toLowerCase(Locale.ROOT));
        if (id == null) return new ItemStack(Items.BARRIER);
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER));
    }

    public static ItemStack iconStack(String raw) { return icon(raw); }

    public static List<LyraConfig.InventoryButtonEntry> entries() { normalize(); return cfg.inventoryButtonEntries; }

    public static void save() { normalize(); ConstellationClient.saveConfig(); }

    public static void reset(int index) {
        if (index < 0 || index >= DEFAULTS.size()) return;
        cfg.inventoryButtonEntries.set(index, copy(DEFAULTS.get(index)));
        save();
    }

    public static void resetAll() {
        cfg.inventoryButtonEntries = new ArrayList<>();
        for (LyraConfig.InventoryButtonEntry value : DEFAULTS) cfg.inventoryButtonEntries.add(copy(value));
        save();
    }

    private static void normalize() {
        if (cfg == null) return;
        cfg.inventoryButtonsSize = Math.clamp(cfg.inventoryButtonsSize, 18, 32);
        cfg.inventoryButtonsGap = Math.clamp(cfg.inventoryButtonsGap, -4, 12);
        cfg.inventoryButtonsOffset = Math.clamp(cfg.inventoryButtonsOffset, 0, 24);
        cfg.inventoryButtonsTooltipDelayMs = Math.clamp(cfg.inventoryButtonsTooltipDelayMs, 0, 2000);
        if (cfg.inventoryButtonEntries == null) cfg.inventoryButtonEntries = new ArrayList<>();
        while (cfg.inventoryButtonEntries.size() < DEFAULTS.size()) cfg.inventoryButtonEntries.add(copy(DEFAULTS.get(cfg.inventoryButtonEntries.size())));
        if (cfg.inventoryButtonEntries.size() > DEFAULTS.size()) cfg.inventoryButtonEntries = new ArrayList<>(cfg.inventoryButtonEntries.subList(0, DEFAULTS.size()));
        for (int i = 0; i < cfg.inventoryButtonEntries.size(); i++) {
            LyraConfig.InventoryButtonEntry value = cfg.inventoryButtonEntries.get(i);
            if (value == null) { cfg.inventoryButtonEntries.set(i, copy(DEFAULTS.get(i))); continue; }
            value.icon = clean(value.icon, 80);
            value.command = sanitize(value.command);
            value.title = clean(value.title, 120);
            value.tooltip = clean(value.tooltip, 120);
        }
    }

    private static String sanitize(String value) {
        String result = clean(value, 256);
        while (result.startsWith("/")) result = result.substring(1);
        return result.strip();
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String result = value.replace('\n', ' ').replace('\r', ' ').strip();
        return result.length() > max ? result.substring(0, max) : result;
    }

    private static LyraConfig.InventoryButtonEntry entry(String icon, String command, String title, String tooltip) {
        return new LyraConfig.InventoryButtonEntry(icon, command, title, tooltip);
    }

    private static LyraConfig.InventoryButtonEntry copy(LyraConfig.InventoryButtonEntry value) {
        LyraConfig.InventoryButtonEntry copy = new LyraConfig.InventoryButtonEntry(value.icon, value.command, value.title, value.tooltip);
        copy.enabled = value.enabled;
        return copy;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var value = RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("value", StringArgumentType.greedyString())
            .executes(context -> set(IntegerArgumentType.getInteger(context, "index"), StringArgumentType.getString(context, "field"), StringArgumentType.getString(context, "value")));
        var field = RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("field", StringArgumentType.word()).then(value);
        var index = RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("index", IntegerArgumentType.integer(1, 14)).then(field);
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("inventorybuttons")
            .executes(context -> edit(-1))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("edit").executes(context -> edit(-1)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetall").executes(context -> resetCommand(-1)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("index", IntegerArgumentType.integer(1, 14))
                    .executes(context -> resetCommand(IntegerArgumentType.getInteger(context, "index") - 1))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("set").then(index))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("size")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("pixels", IntegerArgumentType.integer(18, 32))
                    .executes(context -> size(IntegerArgumentType.getInteger(context, "pixels")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int edit(int index) { Minecraft mc = Minecraft.getInstance(); mc.setScreenAndShow(new InventoryButtonEditorScreen(mc.gui.screen(), index)); return 1; }
    private static int status() { local("§eInventory buttons " + on(cfg.inventoryButtons) + ", top " + on(cfg.inventoryButtonsTop) + ", bottom " + on(cfg.inventoryButtonsBottom) + ", tooltips " + on(cfg.inventoryButtonsShowTooltips) + ", size §f" + cfg.inventoryButtonsSize + "§e."); return 1; }
    private static int size(int value) { cfg.inventoryButtonsSize = value; save(); local("§aInventory-button size updated."); return 1; }
    private static int resetCommand(int index) { if (index < 0) resetAll(); else reset(index); local("§aInventory button defaults restored."); return 1; }

    private static int set(int oneBased, String field, String value) {
        LyraConfig.InventoryButtonEntry button = entries().get(oneBased - 1);
        switch (field.toLowerCase(Locale.ROOT)) {
            case "icon", "item" -> button.icon = value;
            case "command", "cmd" -> button.command = value;
            case "title", "screen" -> button.title = value;
            case "tooltip", "name" -> button.tooltip = value;
            default -> { local("§cField must be icon, command, title, or tooltip."); return 0; }
        }
        save(); local("§aInventory button " + oneBased + " updated."); return 1;
    }

    private static int option(String name, String state) {
        Boolean value = parseState(state);
        if (value == null) { local("§cState must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled", "buttons" -> cfg.inventoryButtons = value;
            case "top" -> cfg.inventoryButtonsTop = value;
            case "bottom" -> cfg.inventoryButtonsBottom = value;
            case "tooltips" -> cfg.inventoryButtonsShowTooltips = value;
            case "highlight" -> cfg.inventoryButtonsHighlightCurrent = value;
            case "animation", "hover" -> cfg.inventoryButtonsHoverAnimation = value;
            case "close" -> cfg.inventoryButtonsCloseAfterCommand = value;
            case "inventory", "inventoryonly" -> cfg.inventoryButtonsOnlyPlayerInventory = value;
            case "creative", "hidecreative" -> cfg.inventoryButtonsHideInCreative = value;
            default -> { local("§cOption must be enabled, top, bottom, tooltips, highlight, animation, close, inventory, or creative."); return 0; }
        }
        save(); local("§aInventory-button option updated."); return 1;
    }

    private static Boolean parseState(String state) { return switch (state.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "§aon" : "§coff"; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f" + text)); }
    private record Rect(int x, int y, int w, int h) { boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; } }
}
