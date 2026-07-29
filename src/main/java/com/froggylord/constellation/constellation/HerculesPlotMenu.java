package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/inventory/plots/GardenPlotMenuHighlighting.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/GardenPlotApi.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/garden/PlotMenuHighlightingConfig.kt
public final class HerculesPlotMenu {
    private static final int[] PLOT_BY_SLOT = {
        -1, -1, 21, 13, 9, 14, 22, -1, -1,
        -1, -1, 15, 5, 1, 6, 16, -1, -1,
        -1, -1, 10, 2, 0, 3, 11, -1, -1,
        -1, -1, 17, 7, 4, 8, 18, -1, -1,
        -1, -1, 23, 19, 12, 20, 24
    };
    private static final List<String> ALL = List.of("current", "pests", "sprays", "locked", "pasting");
    private static HerculesConfig cfg;

    private HerculesPlotMenu() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        normalizePriority();
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!active(screen) || slot == null) return;
        PlotStatus status = status(screen, slot);
        if (status == null) return;
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, status.color);
        if (cfg.plotMenuStatusLetters) {
            graphics.text(Minecraft.getInstance().font, status.mark, slot.x + 1, slot.y + 1, 0xFFFFFFFF, true);
        }
        if (cfg.plotMenuStatusCounts && status.count != null) {
            String count = status.count;
            int x = slot.x + 16 - Minecraft.getInstance().font.width(count);
            graphics.text(Minecraft.getInstance().font, count, x, slot.y + 8, 0xFFFFFFFF, true);
        }
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, List<Component> original) {
        if (!active(screen) || !cfg.plotMenuTooltipStatus) return original;
        Slot slot = ((ContainerScreenAccessor) screen).constellation$hoveredSlot();
        PlotStatus status = status(screen, slot);
        if (status == null) return original;
        List<Component> out = new ArrayList<>(original);
        out.add(Component.empty());
        out.add(Component.literal("Plot status: " + status.description).withColor(status.color & 0xFFFFFF));
        if (status.detail != null && !status.detail.isBlank()) {
            out.add(Component.literal(status.detail).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static PlotStatus status(AbstractContainerScreen<?> screen, Slot slot) {
        if (slot == null || slot.index < 0 || slot.index >= PLOT_BY_SLOT.length) return null;
        int plot = PLOT_BY_SLOT[slot.index];
        if (plot < 0) return null;
        ItemStack stack = slot.getItem();
        List<String> lore = lore(stack);
        boolean locked = lore.stream().anyMatch(line -> line.startsWith("Cost:") || line.contains("Plot is locked"));
        boolean pasting = lore.stream().anyMatch(line -> line.contains("Pasting in progress"));
        Integer current = currentPlot();
        HerculesPests.State pests = HerculesPests.state();
        int pestCount = pests == null ? 0 : pests.plotCounts().getOrDefault(plot, 0);
        long sprayLeft = HerculesSprays.remainingMillis(plot);
        Set<String> priority = priority();
        for (String type : priority) {
            PlotStatus match = switch (type) {
                case "current" -> cfg.plotMenuCurrent && Objects.equals(current, plot)
                    ? new PlotStatus("C", "Current plot", null, null, cfg.plotMenuCurrentColor) : null;
                case "pests" -> cfg.plotMenuPests && pestCount > 0
                    ? new PlotStatus("P", pestCount + (pestCount == 1 ? " pest" : " pests"),
                        null, Integer.toString(pestCount), cfg.plotMenuPestColor) : null;
                case "sprays" -> cfg.plotMenuSprays && sprayLeft > 0
                    ? new PlotStatus("S", "Active spray",
                        sprayDetail(plot, sprayLeft), null, cfg.plotMenuSprayColor) : null;
                case "locked" -> cfg.plotMenuLocked && locked
                    ? new PlotStatus("L", "Locked plot", null, null, cfg.plotMenuLockedColor) : null;
                case "pasting" -> cfg.plotMenuPasting && pasting
                    ? new PlotStatus("PA", "Pasting in progress", null, null, cfg.plotMenuPastingColor) : null;
                default -> null;
            };
            if (match != null) {
                String count = match.count;
                if (type.equals("sprays")) count = Long.toString(Math.max(1, (sprayLeft + 59_999L) / 60_000L));
                return new PlotStatus(match.mark, match.description, match.detail, count, match.color);
            }
        }
        return null;
    }

    private static String sprayDetail(int plot, long left) {
        String type = HerculesSprays.sprayType(plot);
        long seconds = Math.max(0, left / 1000);
        String time = seconds >= 3600
            ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        return (type == null ? "Spray" : type) + " - " + time + " remaining";
    }

    private static Integer currentPlot() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : HerculesPests.plotAt(mc.player.getX(), mc.player.getZ());
    }

    private static List<String> lore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();
        return lore.lines().stream().map(line -> clean(line.getString())).toList();
    }

    private static Set<String> priority() {
        normalizePriority();
        return new LinkedHashSet<>(Arrays.asList(cfg.plotMenuPriority.split(",")));
    }

    private static void normalizePriority() {
        if (cfg == null) return;
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (cfg.plotMenuPriority != null) {
            for (String part : cfg.plotMenuPriority.toLowerCase(Locale.ROOT).split("[,\\s]+")) {
                if (ALL.contains(part)) out.add(part);
            }
        }
        out.addAll(ALL);
        cfg.plotMenuPriority = String.join(",", out);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("plotmenu")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("priority")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("statuses", StringArgumentType.greedyString())
                    .executes(context -> setPriority(StringArgumentType.getString(context, "statuses")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "state"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("status", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> color(StringArgumentType.getString(context, "status"),
                            StringArgumentType.getString(context, "argb")))))));
    }

    private static int status() {
        local("Overlay " + on(cfg.plotMenuHighlighting) + ". Priority: " + cfg.plotMenuPriority + ".");
        local("Use /plotmenu option <current|pests|sprays|locked|pasting|letters|counts|tooltip> <on|off>.");
        return 1;
    }

    private static int setPriority(String raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String part : raw.toLowerCase(Locale.ROOT).split("[,\\s]+")) {
            if (!part.isBlank() && !ALL.contains(part)) {
                local("Unknown status: " + part + ". Valid statuses: " + String.join(", ", ALL) + ".");
                return 0;
            }
            if (!part.isBlank()) values.add(part);
        }
        if (values.isEmpty()) {
            local("Add at least one status.");
            return 0;
        }
        values.addAll(ALL);
        cfg.plotMenuPriority = String.join(",", values);
        save();
        return status();
    }

    private static int option(String name, String state) {
        Boolean value = parse(state);
        if (value == null) {
            local("State must be on or off.");
            return 0;
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.plotMenuHighlighting = value;
            case "current" -> cfg.plotMenuCurrent = value;
            case "pests" -> cfg.plotMenuPests = value;
            case "sprays" -> cfg.plotMenuSprays = value;
            case "locked" -> cfg.plotMenuLocked = value;
            case "pasting" -> cfg.plotMenuPasting = value;
            case "letters" -> cfg.plotMenuStatusLetters = value;
            case "counts" -> cfg.plotMenuStatusCounts = value;
            case "tooltip" -> cfg.plotMenuTooltipStatus = value;
            default -> {
                local("Unknown plot-menu option.");
                return 0;
            }
        }
        save();
        return status();
    }

    private static int color(String status, String raw) {
        int value;
        try {
            String clean = raw.replace("#", "").replace("0x", "");
            long parsed = Long.parseUnsignedLong(clean, 16);
            if (clean.length() == 6) parsed |= 0x90000000L;
            if (clean.length() != 6 && clean.length() != 8) throw new NumberFormatException();
            value = (int) parsed;
        } catch (NumberFormatException ignored) {
            local("Color must be RRGGBB or AARRGGBB.");
            return 0;
        }
        switch (status.toLowerCase(Locale.ROOT)) {
            case "current" -> cfg.plotMenuCurrentColor = value;
            case "pests" -> cfg.plotMenuPestColor = value;
            case "sprays" -> cfg.plotMenuSprayColor = value;
            case "locked" -> cfg.plotMenuLockedColor = value;
            case "pasting" -> cfg.plotMenuPastingColor = value;
            default -> {
                local("Unknown plot status.");
                return 0;
            }
        }
        save();
        local("Updated " + status.toLowerCase(Locale.ROOT) + " plot color.");
        return 1;
    }

    private static boolean active(AbstractContainerScreen<?> screen) {
        return cfg != null && cfg.enabled && cfg.plotMenuHighlighting
            && ConstellationClient.loc().area() == LocationManager.SkyblockArea.GARDEN
            && screen != null && clean(screen.getTitle().getString()).equals("Configure Plots");
    }

    private static Boolean parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }

    private static String on(boolean value) {
        return value ? "on" : "off";
    }

    private static String clean(String value) {
        String clean = ChatFormatting.stripFormatting(value);
        return clean == null ? "" : clean.trim();
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a72[Plots] \u00a7f" + text));
    }

    private static void save() {
        ConstellationClient.saveConfig();
    }

    private record PlotStatus(String mark, String description, String detail, String count, int color) {}
}
