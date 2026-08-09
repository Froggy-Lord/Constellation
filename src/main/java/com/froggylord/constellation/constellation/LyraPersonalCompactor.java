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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/PersonalCompactorOverlay.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/inventory/PersonalCompactorConfig.kt
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/tooltip/CompactorDeletorPreview.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/tooltip/CompactorPreviewTooltipComponent.java
public final class LyraPersonalCompactor {
    private static final Pattern ITEM = Pattern.compile("PERSONAL_(COMPACTOR|DELETOR)_(4000|5000|6000|7000)");
    private static LyraConfig cfg;

    private LyraPersonalCompactor() {}

    public static void init(LyraConfig config) { cfg = config; normalize(); }

    public static void drawSlot(GuiGraphicsExtractor graphics, Slot slot) {
        if (!scope() || !cfg.personalCompactorShowToggle || slot == null || slot.getItem().isEmpty()) return;
        Data data = data(slot.getItem());
        if (data == null || !typeEnabled(data.type)) return;
        String marker = data.enabled ? "E" : "D";
        int color = data.enabled ? 0xFF55FF55 : 0xFFFF5555;
        int occupied = LyraSlotText.occupiedCorners(slot.getItem());
        boolean top = (occupied & 4) == 0 || (occupied & 1) != 0;
        int markerY = top ? slot.y : slot.y + 8;
        graphics.fill(slot.x, markerY, slot.x + 7, markerY + 8, 0xC0101018);
        graphics.text(Minecraft.getInstance().font, marker, slot.x + 1, markerY, color, true);
    }

    public static void renderPreview(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!active() || !visible() || screen == null) return;
        Slot hovered = ((ContainerScreenAccessor) screen).constellation$hoveredSlot();
        if (hovered == null || hovered.getItem().isEmpty()) return;
        LyraRecipeRepository.init();
        Data data = data(hovered.getItem());
        if (data == null || !typeEnabled(data.type)) return;
        int columns = data.tier == 4000 ? 3 : data.tier == 5000 ? 3 : data.tier == 6000 ? 7 : 7;
        int rows = data.tier == 7000 ? 2 : 1;
        float requested = Math.clamp(cfg.personalCompactorScalePercent, 50, 200) / 100f;
        int panelWidth = Math.max(176, 12 + columns * 18);
        int nameLines = cfg.personalCompactorSlotNames ? rows : 0;
        int panelHeight = 28 + rows * 18 + nameLines * 10;
        float available = Math.min((screen.width - 8f) / panelWidth, (screen.height - 8f) / panelHeight);
        float scale = Math.max(.35f, Math.min(requested, available));
        int scaledWidth = Math.round(panelWidth * scale), scaledHeight = Math.round(panelHeight * scale);
        int x = mouseX < screen.width / 2 ? screen.width - scaledWidth - 4 : 4;
        int y = Math.clamp(mouseY - scaledHeight / 2, 4, Math.max(4, screen.height - scaledHeight - 4));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.fill(0, 0, panelWidth, panelHeight, cfg.personalCompactorBackground);
        graphics.fill(0, 0, panelWidth, 1, data.type.equals("COMPACTOR") ? 0xFF55AAFF : 0xFFFF5555);
        Font font = Minecraft.getInstance().font;
        graphics.text(font, data.type.equals("COMPACTOR") ? "Personal Compactor" : "Personal Deletor", 6, 5, 0xFFFFFFFF, true);
        if (cfg.personalCompactorShowStatus)
            graphics.text(font, data.enabled ? "Enabled" : "Disabled", panelWidth - 6 - font.width(data.enabled ? "Enabled" : "Disabled"), 5,
                data.enabled ? 0xFF55FF55 : 0xFFFF5555, true);
        int totalSlots = slots(data.tier);
        for (int index = 0; index < totalSlots; index++) {
            int slotX = 6 + index % columns * 18, slotY = 17 + index / columns * 18;
            ItemStack item = data.items.get(index);
            if (cfg.personalCompactorShowEmptySlots) {
                graphics.fill(slotX, slotY, slotX + 17, slotY + 17, item.isEmpty() ? 0x88404048 : 0x88505060);
                graphics.outline(slotX, slotY, 17, 17, 0xAA777788);
            }
            if (!item.isEmpty()) {
                graphics.item(item, slotX, slotY);
                graphics.itemDecorations(font, item, slotX, slotY);
            }
        }
        if (cfg.personalCompactorSlotNames) {
            for (int row = 0; row < rows; row++) {
                List<String> names = new ArrayList<>();
                for (int column = 0; column < columns; column++) {
                    int index = row * columns + column;
                    if (index >= totalSlots) break;
                    ItemStack item = data.items.get(index);
                    if (!item.isEmpty()) names.add(item.getHoverName().getString());
                }
                String text = names.isEmpty() ? "Empty" : String.join(", ", names);
                graphics.text(font, fit(font, text, panelWidth - 12), 6, 19 + rows * 18 + row * 10, 0xFFAAAAAA, false);
            }
        }
        graphics.pose().popMatrix();
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("personalcompactor")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mode")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                    .executes(context -> mode(StringArgumentType.getString(context, "mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("key")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("keycode", IntegerArgumentType.integer(-1, 512))
                    .executes(context -> key(IntegerArgumentType.getInteger(context, "keycode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scale")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("percent", IntegerArgumentType.integer(50, 200))
                    .executes(context -> scale(IntegerArgumentType.getInteger(context, "percent")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                    .executes(context -> color(StringArgumentType.getString(context, "argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "state")))))));
    }

    private static Data data(ItemStack stack) {
        CompoundTag extra = extra(stack);
        Matcher matcher = ITEM.matcher(extra.getStringOr("id", ""));
        if (!matcher.matches()) return null;
        String type = matcher.group(1);
        int tier = Integer.parseInt(matcher.group(2)), count = slots(tier);
        String prefix = type.equals("COMPACTOR") ? "personal_compact_" : "personal_deletor_";
        List<ItemStack> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String id = extra.getStringOr(prefix + index, "");
            items.add(id.isBlank() ? ItemStack.EMPTY : LyraRecipeRepository.stack(id, 1));
        }
        return new Data(type, tier, extra.getByteOr("PERSONAL_DELETOR_ACTIVE", (byte) 0) == 1, List.copyOf(items));
    }

    private static boolean scope() {
        if (cfg == null || !cfg.enabled) return false;
        return ConstellationClient.loc().onHypixel() || cfg.personalCompactorLocalWorlds && Minecraft.getInstance().hasSingleplayerServer();
    }
    private static boolean active() { return scope() && cfg.personalCompactorPreview; }
    private static boolean typeEnabled(String type) { return type.equals("COMPACTOR") ? cfg.personalCompactorCompactors : cfg.personalCompactorDeletors; }
    private static boolean visible() {
        boolean held = cfg.personalCompactorKey >= 0 && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), cfg.personalCompactorKey);
        return switch (cfg.personalCompactorVisibility.toUpperCase(Locale.ROOT)) {
            case "ALWAYS" -> true;
            case "KEYBIND", "KEY" -> held;
            default -> !held;
        };
    }
    private static int slots(int tier) { return switch (tier) { case 4000 -> 1; case 5000 -> 3; case 6000 -> 7; default -> 12; }; }
    private static CompoundTag extra(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return new CompoundTag();
        CompoundTag root = custom.copyTag(), extra = root.getCompoundOrEmpty("ExtraAttributes");
        return extra.isEmpty() ? root : extra;
    }
    private static String fit(Font font, String text, int width) { return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "..."; }
    private static void normalize() {
        String mode = cfg.personalCompactorVisibility == null ? "EXCEPT_KEYBIND" : cfg.personalCompactorVisibility.toUpperCase(Locale.ROOT);
        if (!mode.equals("ALWAYS") && !mode.equals("KEYBIND") && !mode.equals("EXCEPT_KEYBIND")) mode = "EXCEPT_KEYBIND";
        cfg.personalCompactorVisibility = mode;
        cfg.personalCompactorScalePercent = Math.clamp(cfg.personalCompactorScalePercent, 50, 200);
        cfg.personalCompactorKey = Math.clamp(cfg.personalCompactorKey, -1, 512);
    }
    private static int status() { local("Preview " + on(cfg.personalCompactorPreview) + ", mode " + cfg.personalCompactorVisibility + ", key " + cfg.personalCompactorKey + ", scale " + cfg.personalCompactorScalePercent + "%. Toggle marker " + on(cfg.personalCompactorShowToggle) + "."); return 1; }
    private static int mode(String input) { String value = input.toUpperCase(Locale.ROOT); if (value.equals("KEY")) value = "KEYBIND"; if (value.equals("EXCEPT")) value = "EXCEPT_KEYBIND"; if (!value.equals("ALWAYS") && !value.equals("KEYBIND") && !value.equals("EXCEPT_KEYBIND")) { local("Mode must be always, keybind, or except_keybind."); return 0; } cfg.personalCompactorVisibility = value; save(); return status(); }
    private static int key(int value) { cfg.personalCompactorKey = value; save(); return status(); }
    private static int scale(int value) { cfg.personalCompactorScalePercent = value; save(); return status(); }
    private static int color(String input) { Integer value = parseColor(input); if (value == null) { local("Color must be an eight-digit ARGB hex value."); return 0; } cfg.personalCompactorBackground = value; save(); return status(); }
    private static int option(String name, String state) { Boolean value = parseState(state); if (value == null) { local("State must be on or off."); return 0; } switch (name.toLowerCase(Locale.ROOT)) { case "enabled" -> cfg.personalCompactorPreview = value; case "toggle", "marker" -> cfg.personalCompactorShowToggle = value; case "status" -> cfg.personalCompactorShowStatus = value; case "empty" -> cfg.personalCompactorShowEmptySlots = value; case "compactors" -> cfg.personalCompactorCompactors = value; case "deletors" -> cfg.personalCompactorDeletors = value; case "names" -> cfg.personalCompactorSlotNames = value; case "local" -> cfg.personalCompactorLocalWorlds = value; default -> { local("Unknown Personal Compactor option."); return 0; } } save(); return status(); }
    private static Boolean parseState(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "on", "true", "yes" -> true; case "off", "false", "no" -> false; default -> null; }; }
    private static Integer parseColor(String value) { String clean = value.startsWith("#") ? value.substring(1) : value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value; if (!clean.matches("[0-9a-fA-F]{8}")) return null; try { return (int) Long.parseLong(clean, 16); } catch (NumberFormatException ignored) { return null; } }
    private static String on(boolean value) { return value ? "ON" : "OFF"; }
    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String message) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Personal Compactor] " + message)); }
    private record Data(String type, int tier, boolean enabled, List<ItemStack> items) {}
}
