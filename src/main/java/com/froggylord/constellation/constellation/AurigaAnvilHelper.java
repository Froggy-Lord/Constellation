package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/AnvilHelper.java
// ported from NoFrills (GPL-3.0-only): features/solvers/AnvilHelper.java
public final class AurigaAnvilHelper {
    public enum Mode { EMPTY, SINGLE_BOOK, MATCH, MISMATCH, OTHER }
    public record State(Mode mode, String left, String right, String result, String selectedId,
                        Map<String, Integer> leftEnchants, Map<String, Integer> rightEnchants, int matches) {}

    private static AurigaConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static State state = empty();
    private static boolean mismatchLatched;

    private AurigaAnvilHelper() {}

    public static void init(AurigaConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, opened, width, height) -> {
            if (!(opened instanceof AbstractContainerScreen<?> container)
                || !plain(container.getTitle().getString()).equals("Anvil")) return;
            screen = container;
            update(container);
            ScreenEvents.afterTick(opened).register(ignored -> update(container));
            ScreenEvents.remove(opened).register(ignored -> {
                if (screen == container) {
                    screen = null;
                    state = empty();
                    mismatchLatched = false;
                }
            });
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("anvilhelper")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(c -> {
                cfg.anvilHelper = !cfg.anvilHelper;
                save();
                return status();
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static void update(AbstractContainerScreen<?> container) {
        if (!active(container) || container.getMenu().slots.size() <= 33) return;
        ItemStack left = container.getMenu().getSlot(29).getItem();
        ItemStack right = container.getMenu().getSlot(33).getItem();
        ItemStack result = container.getMenu().getSlot(13).getItem();
        Map<String, Integer> leftEnchants = enchants(left);
        Map<String, Integer> rightEnchants = enchants(right);
        boolean leftBook = left.is(Items.ENCHANTED_BOOK) && !leftEnchants.isEmpty();
        boolean rightBook = right.is(Items.ENCHANTED_BOOK) && !rightEnchants.isEmpty();
        Mode mode;
        String selected = "";
        if (left.isEmpty() && right.isEmpty()) mode = Mode.EMPTY;
        else if (leftBook && rightBook) mode = equal(leftEnchants, rightEnchants) ? Mode.MATCH : Mode.MISMATCH;
        else if (leftBook ^ rightBook) {
            mode = Mode.SINGLE_BOOK;
            selected = marketId(leftBook ? left : right, cfg.anvilMatchExactLevel);
        } else mode = Mode.OTHER;
        int matches = selected.isBlank() ? 0 : countMatches(container, selected);
        state = new State(mode, name(left), name(right), name(result), selected,
            Map.copyOf(leftEnchants), Map.copyOf(rightEnchants), matches);
        if (mode == Mode.MISMATCH && !mismatchLatched && cfg.anvilMismatchWarning) {
            mismatchLatched = true;
            local("The two enchanted books have different enchantments or levels.");
            if (cfg.anvilMismatchSound && Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), .8f, .7f);
        } else if (mode != Mode.MISMATCH) mismatchLatched = false;
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> container, Slot slot) {
        if (!active(container) || slot == null) return;
        int color = 0;
        if (cfg.anvilHighlightInputs && (slot.index == 29 || slot.index == 33) && !slot.getItem().isEmpty())
            color = state.mode() == Mode.MISMATCH ? cfg.anvilMismatchColor : cfg.anvilInputColor;
        if (cfg.anvilHighlightResult && slot.index == 13 && !slot.getItem().isEmpty())
            color = state.mode() == Mode.MISMATCH ? cfg.anvilMismatchColor : cfg.anvilResultColor;
        if (cfg.anvilFindMatchingBooks && !state.selectedId().isBlank() && isSearchSlot(slot)
            && marketId(slot.getItem(), cfg.anvilMatchExactLevel).equals(state.selectedId()))
            color = cfg.anvilMatchColor;
        if (color != 0) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    public static boolean shouldBlockClick(AbstractContainerScreen<?> container, Slot slot, int slotId) {
        if (!active(container) || !cfg.anvilBlockMismatchOutput || state.mode() != Mode.MISMATCH) return false;
        int index = slot == null ? slotId : slot.index;
        if (index != 13) return false;
        if (cfg.anvilControlBypass && controlDown()) return false;
        local("Blocked a mismatched enchanted-book result. Hold Control while clicking to bypass.");
        return true;
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> container, ItemStack stack, List<Component> original) {
        if (!active(container) || !cfg.anvilShowTooltip) return original;
        Slot hovered = ((com.froggylord.constellation.mixin.ContainerScreenAccessor) container).constellation$hoveredSlot();
        if (hovered == null || (hovered.index != 13 && hovered.index != 29 && hovered.index != 33
            && !isMatchingSlot(hovered))) return original;
        ArrayList<Component> out = new ArrayList<>(original);
        out.add(Component.literal("\u00a78----------------"));
        if (state.mode() == Mode.MISMATCH)
            out.add(Component.literal("\u00a7cBooks do not have identical enchantments."));
        else if (isMatchingSlot(hovered))
            out.add(Component.literal("\u00a7aMatches the selected anvil book."));
        else out.add(Component.literal("\u00a77Anvil state: \u00a7f" + label(state.mode())));
        if (cfg.anvilShowEnchantments) {
            if (!state.leftEnchants().isEmpty()) out.add(Component.literal("\u00a77Left: \u00a7b" + describe(state.leftEnchants())));
            if (!state.rightEnchants().isEmpty()) out.add(Component.literal("\u00a77Right: \u00a7b" + describe(state.rightEnchants())));
        }
        if (state.mode() == Mode.SINGLE_BOOK)
            out.add(Component.literal("\u00a77Matching books: \u00a7a" + state.matches()));
        return out;
    }

    public static State state() { return state; }
    public static AurigaConfig config() { return cfg; }
    public static boolean visible() { return cfg != null && cfg.anvilHud && screen != null && active(screen); }

    private static int countMatches(AbstractContainerScreen<?> container, String id) {
        int count = 0;
        for (Slot slot : container.getMenu().slots)
            if (isSearchSlot(slot) && marketId(slot.getItem(), cfg.anvilMatchExactLevel).equals(id)) count++;
        return count;
    }

    private static boolean isMatchingSlot(Slot slot) {
        return slot != null && cfg.anvilFindMatchingBooks && !state.selectedId().isBlank() && isSearchSlot(slot)
            && marketId(slot.getItem(), cfg.anvilMatchExactLevel).equals(state.selectedId());
    }

    private static boolean isSearchSlot(Slot slot) {
        if (slot == null || slot.index == 29 || slot.index == 33 || slot.getItem().isEmpty()) return false;
        return !cfg.anvilMatchPlayerInventoryOnly || slot.index >= 54;
    }

    private static Map<String, Integer> enchants(ItemStack stack) {
        CompoundTag tag = extra(stack).getCompoundOrEmpty("enchantments");
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        for (String key : tag.keySet()) {
            int level = tag.getIntOr(key, 0);
            if (level > 0) out.put(key, level);
        }
        return out;
    }

    private static boolean equal(Map<String, Integer> left, Map<String, Integer> right) {
        return !left.isEmpty() && left.equals(right);
    }

    private static String marketId(ItemStack stack, boolean level) {
        if (stack == null || stack.isEmpty()) return "";
        CompoundTag tag = extra(stack);
        String id = tag.getStringOr("id", "");
        if (!id.equals("ENCHANTED_BOOK")) return "";
        Map<String, Integer> enchants = enchants(stack);
        if (enchants.size() != 1) return level ? "" : id;
        Map.Entry<String, Integer> entry = enchants.entrySet().iterator().next();
        String enchant = entry.getKey().toUpperCase(Locale.ROOT);
        return level ? "ENCHANTMENT_" + enchant + "_" + entry.getValue() : "ENCHANTMENT_" + enchant;
    }

    private static CompoundTag extra(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return new CompoundTag();
        CompoundTag root = data.copyTag();
        CompoundTag legacy = root.getCompoundOrEmpty("ExtraAttributes");
        return legacy.isEmpty() ? root : legacy;
    }

    private static String describe(Map<String, Integer> values) {
        return values.entrySet().stream().map(e -> title(e.getKey()) + " " + e.getValue()).reduce((a, b) -> a + ", " + b).orElse("None");
    }

    private static String title(String value) {
        String[] parts = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String label(Mode mode) {
        return switch (mode) {
            case EMPTY -> "Empty";
            case SINGLE_BOOK -> "Find matching book";
            case MATCH -> "Matching books";
            case MISMATCH -> "Mismatched books";
            case OTHER -> "Item combination";
        };
    }

    private static String name(ItemStack stack) { return stack == null || stack.isEmpty() ? "Empty" : plain(stack.getHoverName().getString()); }
    private static String plain(String value) { String out = ChatFormatting.stripFormatting(value); return out == null ? value.trim() : out.trim(); }
    private static boolean active(AbstractContainerScreen<?> container) {
        return cfg != null && cfg.enabled && cfg.anvilHelper && container == screen
            && ConstellationClient.loc().onHypixel() && plain(container.getTitle().getString()).equals("Anvil");
    }
    private static boolean controlDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
            || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static int status() {
        local("Helper " + on(cfg.anvilHelper) + ", screen " + (screen == null ? "closed" : "open")
            + ", state " + label(state.mode()) + ", matches " + state.matches() + ".");
        return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = bool(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.anvilHelper = value;
            case "hud" -> cfg.anvilHud = value;
            case "warning" -> cfg.anvilMismatchWarning = value;
            case "block" -> cfg.anvilBlockMismatchOutput = value;
            case "bypass" -> cfg.anvilControlBypass = value;
            case "sound" -> cfg.anvilMismatchSound = value;
            case "inputs" -> cfg.anvilHighlightInputs = value;
            case "result" -> cfg.anvilHighlightResult = value;
            case "find", "matches" -> cfg.anvilFindMatchingBooks = value;
            case "playeronly" -> cfg.anvilMatchPlayerInventoryOnly = value;
            case "exactlevel" -> cfg.anvilMatchExactLevel = value;
            case "tooltip" -> cfg.anvilShowTooltip = value;
            case "enchants" -> cfg.anvilShowEnchantments = value;
            default -> { local("Unknown Anvil Helper option."); return 0; }
        }
        save();
        return status();
    }

    private static Boolean bool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a75[Anvil Helper] \u00a7f" + text));
    }
    private static void save() { ConstellationClient.saveConfig(); }
    private static State empty() { return new State(Mode.EMPTY, "Empty", "Empty", "Empty", "", Map.of(), Map.of(), 0); }
}
