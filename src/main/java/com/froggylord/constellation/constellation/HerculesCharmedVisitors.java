package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/visitor/GardenCharmedVisitors.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/garden/visitor/CharmedVisitorsConfig.kt
public final class HerculesCharmedVisitors {
    private static final String CHARMED_NAME = "This Visitor has been Charmed!";
    private static HerculesConfig cfg;
    private static Object observedScreen;
    private static String ignoredVisitor = "";

    private HerculesCharmedVisitors() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        maps();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetTransient());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetTransient());
    }

    public static void observe(AbstractContainerScreen<?> screen, Slot slot) {
        if (cfg == null || !cfg.enabled || !cfg.charmedVisitors || screen == null || slot == null || slot.index != 48) return;
        if (screen != observedScreen) {
            observedScreen = screen;
            ignoredVisitor = "";
        }
        String profile = profile();
        if (profile.isBlank()) return;
        String visitor = visitorName(screen);
        if (visitor.isBlank() || visitor.equals(ignoredVisitor)) return;
        boolean charmed = name(slot.getItem()).startsWith(CHARMED_NAME);
        Set<String> values = values(profile);
        boolean changed = charmed ? values.add(visitor) : values.remove(visitor);
        if (!changed) return;
        store(profile, values);
        if (cfg.charmedChatChanges) local((charmed ? "Added " : "Removed ") + visitor
            + (charmed ? " to" : " from") + " the charmed visitor list.");
    }

    public static List<String> rows() {
        if (!active()) return List.of();
        List<String> rows = new ArrayList<>(values(profile()));
        if (cfg.charmedAlphabetical) rows.sort(String.CASE_INSENSITIVE_ORDER);
        int limit = Math.clamp(cfg.charmedMaxRows, 1, 50);
        if (rows.size() > limit) {
            int hidden = rows.size() - limit;
            rows = new ArrayList<>(rows.subList(0, limit));
            rows.add("+" + hidden + " more");
        }
        return List.copyOf(rows);
    }

    public static int count() { return values(profile()).size(); }
    public static boolean showCount() { return cfg != null && cfg.charmedShowCount; }
    public static boolean isCharmed(String visitor) {
        return visitor != null && values(profile()).stream().anyMatch(value -> value.equalsIgnoreCase(visitor));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("charmedvisitors")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(context -> list()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("add")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("visitor", StringArgumentType.greedyString())
                    .executes(context -> add(StringArgumentType.getString(context, "visitor")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("visitor", StringArgumentType.greedyString())
                    .executes(context -> remove(StringArgumentType.getString(context, "visitor")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(context -> clear()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("amount", IntegerArgumentType.integer(1, 50))
                    .executes(context -> rows(IntegerArgumentType.getInteger(context, "amount")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("Charmed Visitors " + on(cfg.charmedVisitors) + ", " + count() + " saved for this profile."
            + " Count " + on(cfg.charmedShowCount) + ", shopping mark " + on(cfg.charmedMarkShoppingList)
            + ", alphabetical " + on(cfg.charmedAlphabetical) + ".");
        return 1;
    }

    private static int list() {
        List<String> values = new ArrayList<>(values(profile()));
        values.sort(String.CASE_INSENSITIVE_ORDER);
        local(values.isEmpty() ? "No charmed visitors are saved for this profile." : String.join(", ", values));
        return 1;
    }

    private static int add(String input) {
        String name = clean(input);
        if (name.isBlank()) { local("Enter a visitor name."); return 0; }
        String profile = profile();
        if (profile.isBlank()) { local("SkyBlock profile data is not available yet."); return 0; }
        Set<String> values = values(profile);
        values.removeIf(value -> value.equalsIgnoreCase(name));
        values.add(name);
        store(profile, values);
        local("Added " + name + " to the charmed visitor list.");
        return 1;
    }

    private static int remove(String input) {
        String name = clean(input), profile = profile();
        Set<String> values = values(profile);
        boolean changed = values.removeIf(value -> value.equalsIgnoreCase(name));
        if (!changed) { local("That visitor is not in the charmed list."); return 0; }
        store(profile, values);
        if (observedScreen != null) ignoredVisitor = name;
        local("Removed " + name + " from the charmed visitor list.");
        return 1;
    }

    private static int clear() {
        String profile = profile();
        if (profile.isBlank()) { local("SkyBlock profile data is not available yet."); return 0; }
        cfg.charmedVisitorsByProfile.remove(profile);
        ConstellationClient.saveConfig();
        ignoredVisitor = observedScreen instanceof AbstractContainerScreen<?> screen ? visitorName(screen) : "";
        local("Cleared charmed visitors for this profile.");
        return 1;
    }

    private static int rows(int amount) { cfg.charmedMaxRows = amount; ConstellationClient.saveConfig(); return status(); }

    private static int option(String name, String state) {
        Boolean value = parseState(state);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.charmedVisitors = value;
            case "count" -> cfg.charmedShowCount = value;
            case "shopping" -> cfg.charmedMarkShoppingList = value;
            case "alphabetical", "sort" -> cfg.charmedAlphabetical = value;
            case "garden" -> cfg.charmedOnlyInGarden = value;
            case "chat" -> cfg.charmedChatChanges = value;
            default -> { local("Unknown Charmed Visitors option."); return 0; }
        }
        ConstellationClient.saveConfig();
        return status();
    }

    private static boolean active() {
        if (cfg == null || !cfg.enabled || !cfg.charmedVisitors || profile().isBlank()) return false;
        return !cfg.charmedOnlyInGarden || ConstellationClient.loc().area() == LocationManager.SkyblockArea.GARDEN;
    }

    private static String visitorName(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu().slots.size() <= 48) return "";
        if (!name(screen.getMenu().getSlot(29).getItem()).equals("Accept Offer")) return "";
        ItemStack info = screen.getMenu().getSlot(13).getItem();
        ItemLore lore = info.get(DataComponents.LORE);
        if (lore == null || lore.lines().size() != 4) return "";
        return name(info);
    }

    private static Set<String> values(String profile) {
        maps();
        if (profile == null || profile.isBlank()) return new LinkedHashSet<>();
        return new LinkedHashSet<>(cfg.charmedVisitorsByProfile.getOrDefault(profile, List.of()));
    }

    private static void store(String profile, Set<String> values) {
        cfg.charmedVisitorsByProfile.put(profile, new ArrayList<>(values));
        ConstellationClient.saveConfig();
    }

    private static void maps() {
        if (cfg.charmedVisitorsByProfile == null) cfg.charmedVisitorsByProfile = new java.util.HashMap<>();
    }

    private static String profile() { return LyraStorageValue.currentProfileKey(); }
    private static String name(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return clean(stack.getHoverName().getString());
    }
    private static String clean(String value) {
        String stripped = ChatFormatting.stripFormatting(value == null ? "" : value);
        return stripped == null ? "" : stripped.trim();
    }
    private static Boolean parseState(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "on", "true", "yes" -> true; case "off", "false", "no" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "ON" : "OFF"; }
    private static void resetTransient() { observedScreen = null; ignoredVisitor = ""; }
    private static void local(String message) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Charmed Visitors] " + message)); }
}
