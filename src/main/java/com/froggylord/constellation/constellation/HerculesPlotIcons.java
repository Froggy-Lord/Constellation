package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/inventory/plots/GardenPlotIcon.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/garden/PlotIconConfig.kt
// ItemStack persistence ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/tooltip/BackpackPreview.java
public final class HerculesPlotIcons {
    private enum Mode { OFF, SET, RESET }

    private static final Set<Integer> PLOT_SLOTS = Set.of(
        2, 3, 4, 5, 6, 11, 12, 13, 14, 15, 20, 21, 23, 24,
        29, 30, 31, 32, 33, 38, 39, 40, 41, 42
    );
    private static final int EDIT_SLOT = 53;
    private static final int MAX_SERIALIZED_LENGTH = 100_000;
    private static final Map<String, ItemStack> CACHE = new HashMap<>();
    private static HerculesConfig cfg;
    private static Mode mode = Mode.OFF;
    private static ItemStack pending = ItemStack.EMPTY;
    private static AbstractContainerScreen<?> openMenu;

    private HerculesPlotIcons() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        maps();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !isMenu(container)) return;
            openMenu = container;
            mode = Mode.OFF;
            pending = ItemStack.EMPTY;
            CACHE.clear();
            ScreenEvents.remove(container).register(removed -> {
                if (openMenu == container) resetTransient();
            });
        });
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!active(screen) || slot == null) return;
        ItemStack replacement = ItemStack.EMPTY;
        if (slot.index == EDIT_SLOT && cfg.plotIconEditorButton) {
            replacement = new ItemStack(Items.WOODEN_AXE);
        } else if (PLOT_SLOTS.contains(slot.index)) {
            replacement = icon(slot.index);
        }
        if (replacement.isEmpty()) return;
        graphics.item(replacement, slot.x, slot.y);
        graphics.itemDecorations(Minecraft.getInstance().font, replacement, slot.x, slot.y);
        if (slot.index == EDIT_SLOT) {
            int color = switch (mode) {
                case OFF -> 0xFFFF5555;
                case SET -> 0xFF55FF55;
                case RESET -> 0xFF55AAFF;
            };
            graphics.text(Minecraft.getInstance().font, mark(), slot.x + 1, slot.y + 1, color, true);
        }
    }

    public static boolean shouldBlockClick(AbstractContainerScreen<?> screen, Slot slot, int slotId,
                                           int button, ContainerInput input) {
        if (!active(screen) || slot == null) return false;
        int chestSlots = Math.max(0, screen.getMenu().slots.size() - 36);
        if (slotId == EDIT_SLOT && cfg.plotIconEditorButton) {
            if (input == ContainerInput.PICKUP) {
                mode = button == 1 ? previous(mode) : next(mode);
                pending = ItemStack.EMPTY;
                feedback("Plot icon editor: " + modeName() + ".");
            }
            return true;
        }
        if (mode == Mode.OFF) return false;
        if (slotId >= chestSlots) {
            if (slot.getItem().isEmpty()) {
                feedback("Select a non-empty inventory item.");
            } else {
                pending = slot.getItem().copy();
                pending.setCount(1);
                feedback("Selected " + clean(pending.getHoverName().getString()) + ". Click a plot to use it.");
            }
            return true;
        }
        if (!PLOT_SLOTS.contains(slotId)) return false;
        if (mode == Mode.RESET) {
            String key = key(slotId);
            boolean changed = cfg.plotIconStacks.remove(key) != null;
            CACHE.remove(key);
            if (changed) save();
            feedback(changed ? "Restored the original plot icon." : "That plot already uses its original icon.");
            return true;
        }
        if (pending.isEmpty()) {
            feedback("Select an item from your inventory first.");
            return true;
        }
        String encoded = encode(pending);
        if (encoded == null) {
            feedback("That item could not be saved as a plot icon.");
            return true;
        }
        String key = key(slotId);
        cfg.plotIconStacks.put(key, encoded);
        CACHE.put(key, pending.copy());
        save();
        feedback("Updated the plot icon to " + clean(pending.getHoverName().getString()) + ".");
        return true;
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, List<Component> original) {
        if (!active(screen) || !cfg.plotIconTooltipHelp) return original;
        Slot slot = ((ContainerScreenAccessor) screen).constellation$hoveredSlot();
        if (slot == null) return original;
        if (slot.index == EDIT_SLOT && cfg.plotIconEditorButton) {
            List<Component> out = new ArrayList<>();
            out.add(Component.literal("Plot Icon Editor").withStyle(ChatFormatting.GOLD));
            out.add(Component.empty());
            out.add(modeLine(Mode.OFF, "Normal menu behavior"));
            out.add(modeLine(Mode.SET, "Select an inventory item, then a plot"));
            out.add(modeLine(Mode.RESET, "Click a plot to restore its icon"));
            out.add(Component.empty());
            out.add(Component.literal("Left/right click to change mode.").withStyle(ChatFormatting.YELLOW));
            return out;
        }
        if (mode != Mode.OFF && slot.index >= Math.max(0, screen.getMenu().slots.size() - 36)) {
            List<Component> out = new ArrayList<>(original);
            out.add(Component.empty());
            out.add(Component.literal(mode == Mode.SET ? "Click to select this icon." : "Switch to Set mode to select an icon.")
                .withStyle(mode == Mode.SET ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            return out;
        }
        if (!PLOT_SLOTS.contains(slot.index)) return original;
        ItemStack icon = icon(slot.index);
        if (icon.isEmpty() && mode == Mode.OFF) return original;
        List<Component> out = new ArrayList<>(original);
        out.add(Component.empty());
        if (!icon.isEmpty()) {
            out.add(Component.literal("Custom icon: " + clean(icon.getHoverName().getString())).withStyle(ChatFormatting.AQUA));
        }
        if (mode == Mode.SET) {
            out.add(Component.literal(pending.isEmpty() ? "Select an inventory item first." : "Click to use the selected icon.")
                .withStyle(pending.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        } else if (mode == Mode.RESET) {
            out.add(Component.literal("Click to restore the original icon.").withStyle(ChatFormatting.BLUE));
        }
        return out;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ploticons")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .executes(context -> clear(false)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearall")
                .executes(context -> clear(true)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
                .executes(context -> {
                    cfg.plotIcons = !cfg.plotIcons;
                    save();
                    return status();
                }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        maps();
        String prefix = profilePrefix();
        long count = cfg.plotIconStacks.keySet().stream().filter(key -> key.startsWith(prefix)).count();
        local("Custom plot icons " + (cfg.plotIcons ? "on" : "off") + ", " + count
            + " saved for " + (cfg.plotIconPerProfile ? "this profile." : "the global layout."));
        local("Open Configure Plots and use the wooden axe in the bottom-right slot to edit icons.");
        return 1;
    }

    private static int clear(boolean all) {
        maps();
        if (all || !cfg.plotIconPerProfile) {
            cfg.plotIconStacks.clear();
        } else {
            String prefix = profilePrefix();
            cfg.plotIconStacks.keySet().removeIf(key -> key.startsWith(prefix));
        }
        CACHE.clear();
        save();
        local(all || !cfg.plotIconPerProfile
            ? "Cleared every saved plot icon."
            : "Cleared saved plot icons for this profile.");
        return 1;
    }

    private static int option(String name, String state) {
        Boolean value = parse(state);
        if (value == null) {
            local("State must be on or off.");
            return 0;
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.plotIcons = value;
            case "editor" -> cfg.plotIconEditorButton = value;
            case "chat" -> cfg.plotIconChatFeedback = value;
            case "tooltip" -> cfg.plotIconTooltipHelp = value;
            case "perprofile" -> {
                cfg.plotIconPerProfile = value;
                CACHE.clear();
            }
            default -> {
                local("Unknown plot-icon option.");
                return 0;
            }
        }
        save();
        return status();
    }

    private static Component modeLine(Mode lineMode, String description) {
        boolean selected = mode == lineMode;
        ChatFormatting color = switch (lineMode) {
            case OFF -> ChatFormatting.RED;
            case SET -> ChatFormatting.GREEN;
            case RESET -> ChatFormatting.BLUE;
        };
        return Component.literal((selected ? "> " : "  ") + modeLabel(lineMode) + ": ")
            .withStyle(color).append(Component.literal(description).withStyle(ChatFormatting.GRAY));
    }

    private static ItemStack icon(int slot) {
        maps();
        String key = key(slot);
        if (CACHE.containsKey(key)) return CACHE.get(key);
        String raw = cfg.plotIconStacks.get(key);
        if (raw == null || raw.isBlank()) return ItemStack.EMPTY;
        ItemStack decoded = decode(raw);
        if (decoded.isEmpty()) {
            cfg.plotIconStacks.remove(key);
            save();
            return ItemStack.EMPTY;
        }
        decoded.setCount(1);
        CACHE.put(key, decoded);
        return decoded;
    }

    private static String encode(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || stack == null || stack.isEmpty()) return null;
        try {
            Tag tag = ItemStack.OPTIONAL_CODEC.encodeStart(
                mc.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
            String serialized = tag.toString();
            return serialized.length() <= MAX_SERIALIZED_LENGTH ? serialized : null;
        } catch (Exception exception) {
            ConstellationClient.LOGGER.warn("Failed to encode Garden plot icon", exception);
            return null;
        }
    }

    private static ItemStack decode(String raw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || raw == null || raw.length() > MAX_SERIALIZED_LENGTH) return ItemStack.EMPTY;
        try {
            Tag tag = TagParser.create(NbtOps.INSTANCE).parseFully(raw);
            DataResult<ItemStack> result = ItemStack.OPTIONAL_CODEC.parse(
                mc.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag);
            return result.result().orElse(ItemStack.EMPTY);
        } catch (Exception exception) {
            ConstellationClient.LOGGER.warn("Failed to decode Garden plot icon", exception);
            return ItemStack.EMPTY;
        }
    }

    private static String key(int slot) {
        return profilePrefix() + slot;
    }

    private static String profilePrefix() {
        if (!cfg.plotIconPerProfile) return "global|";
        String profile = LyraStorageValue.currentProfileKey();
        if (profile == null || profile.isBlank()) profile = "unknown";
        return profile.toLowerCase(Locale.ROOT) + "|";
    }

    private static boolean active(AbstractContainerScreen<?> screen) {
        return cfg != null && cfg.enabled && cfg.plotIcons
            && ConstellationClient.loc().area() == LocationManager.SkyblockArea.GARDEN && isMenu(screen);
    }

    private static boolean isMenu(AbstractContainerScreen<?> screen) {
        return screen != null && clean(screen.getTitle().getString()).equals("Configure Plots")
            && screen.getMenu().slots.size() >= 90;
    }

    private static Mode next(Mode value) {
        return switch (value) {
            case OFF -> Mode.SET;
            case SET -> Mode.RESET;
            case RESET -> Mode.OFF;
        };
    }

    private static Mode previous(Mode value) {
        return switch (value) {
            case OFF -> Mode.RESET;
            case SET -> Mode.OFF;
            case RESET -> Mode.SET;
        };
    }

    private static String mark() {
        return switch (mode) {
            case OFF -> "OFF";
            case SET -> "SET";
            case RESET -> "R";
        };
    }

    private static String modeName() {
        return modeLabel(mode).toLowerCase(Locale.ROOT);
    }

    private static String modeLabel(Mode value) {
        return switch (value) {
            case OFF -> "OFF";
            case SET -> "SET";
            case RESET -> "RESET";
        };
    }

    private static void resetTransient() {
        openMenu = null;
        mode = Mode.OFF;
        pending = ItemStack.EMPTY;
        CACHE.clear();
    }

    private static void feedback(String text) {
        if (cfg.plotIconChatFeedback) local(text);
    }

    private static String clean(String value) {
        String clean = ChatFormatting.stripFormatting(value);
        return clean == null ? "" : clean.trim();
    }

    private static Boolean parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }

    private static void maps() {
        if (cfg.plotIconStacks == null) cfg.plotIconStacks = new HashMap<>();
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a72[Plot Icons] \u00a7f" + text));
    }

    private static void save() {
        ConstellationClient.saveConfig();
    }
}
