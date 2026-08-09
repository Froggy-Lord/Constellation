package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Devonian (GPL-3.0): features/misc/QuiverDisplay.kt
// ported from NoFrills (GPL-3.0): hud/elements/Quiver.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/QuiverWarning.java
public final class LyraQuiver {
    public record State(String arrowName, int amount) {}

    private static final Pattern ARROWS = Pattern.compile("^Arrows Remaining: ([\\d,]+)$");
    private static final Pattern WARNING = Pattern.compile("^(?:You only have (?:50|10) .+|You don't have any more .+) left in your Quiver!$");
    private static LyraConfig cfg;
    private static State state;
    private static boolean initialized, pendingDungeonReminder, wasInDungeon, warnedLow;
    private static long diagnosticUntil;
    private static Object levelIdentity;

    private LyraQuiver() {}

    public static void init(LyraConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(2, "lyra-quiver", LyraQuiver::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(clean(message.getString()));
            return true;
        });
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != levelIdentity) { levelIdentity = mc.level; state = null; warnedLow = false; }
        if (!configured() || mc.player == null || mc.level == null || !allowedWorld()) { state = null; return; }
        boolean dungeon = ConstellationClient.loc().inDungeons();
        if (wasInDungeon && !dungeon && pendingDungeonReminder) {
            pendingDungeonReminder = false;
            if (cfg.quiverReminderAfterDungeon) showWarning("Low on arrows after your dungeon run");
        }
        wasInDungeon = dungeon;
        int slot = dungeon && !ConstellationClient.dungeon().inBoss() ? 9 : 44;
        ItemStack stack = mc.player.containerMenu.slots.size() > slot ? mc.player.containerMenu.getSlot(slot).getItem() : ItemStack.EMPTY;
        State next = parse(stack);
        if (next == null) return;
        State previous = state;
        state = next;
        int threshold = Math.clamp(cfg.quiverLowAmount, 0, Math.max(0, cfg.quiverCapacity));
        if (next.amount() > threshold) warnedLow = false;
        if (cfg.quiverLowWarning && threshold > 0 && next.amount() <= threshold && !warnedLow
            && previous != null && previous.amount() > threshold) {
            warnedLow = true;
            if (dungeon && !cfg.quiverWarningInDungeons) pendingDungeonReminder = true;
            else showWarning(next.amount() == 0 ? "Quiver empty" : "Low on arrows: " + next.amount());
        }
    }

    private static void onChat(String message) {
        if (!configured() || !cfg.quiverLowWarning || !WARNING.matcher(message).matches()) return;
        warnedLow = true;
        boolean dungeon = ConstellationClient.loc().inDungeons();
        if (dungeon) pendingDungeonReminder = true;
        if (!dungeon || cfg.quiverWarningInDungeons) showWarning(message.startsWith("You don't") ? "Quiver empty" : "Low on arrows");
    }

    private static void showWarning(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (cfg.quiverWarningTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTimes(0, 30, 10);
            mc.gui.hud.setTitle(Component.literal(text).withColor(cfg.quiverLowColor & 0xFFFFFF));
        }
        if (cfg.quiverWarningChat) local(text + ".");
        if (cfg.quiverWarningSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .9f, .7f);
    }

    private static State parse(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.ARROW && stack.getItem() != Items.FEATHER) return null;
        for (String line : lore(stack)) {
            Matcher matcher = ARROWS.matcher(clean(line));
            if (!matcher.matches()) continue;
            try {
                int amount = Integer.parseInt(matcher.group(1).replace(",", ""));
                String name = clean(stack.getHoverName().getString()).replaceAll("(?i)\\s+Arrow$", "");
                return new State(name.isBlank() ? "Arrows" : name, Math.max(0, amount));
            } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    public static String hotbarCount(ItemStack stack) {
        if (!configured() || !cfg.quiverTrueHotbarCount || !allowedWorld() || !quiverItem(stack)) return null;
        State value = parse(stack);
        return value == null ? null : compact(value.amount());
    }

    private static boolean quiverItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return false;
        CompoundTag root = custom.copyTag(), extra = root.getCompoundOrEmpty("ExtraAttributes");
        if (extra.isEmpty()) extra = root;
        return extra.getBooleanOr("quiver_arrow", false)
            || "true".equalsIgnoreCase(extra.getStringOr("quiver_arrow", "false"));
    }

    public static boolean visible() {
        if (!configured() || !cfg.quiverHud || state == null || !allowedWorld()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return switch (cfg.quiverVisibility.toUpperCase(Locale.ROOT)) {
            case "ALWAYS" -> true;
            case "BOW_INVENTORY" -> mc.player.getInventory().contains(stack -> bow(stack));
            default -> bow(mc.player.getMainHandItem()) || bow(mc.player.getOffhandItem());
        };
    }

    private static boolean bow(ItemStack stack) { return stack != null && (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)); }
    public static State state() { return state; }
    public static LyraConfig config() { return cfg; }
    public static int amountColor() {
        if (state == null || cfg == null) return 0xFFFFFFFF;
        if (!cfg.quiverColorAmount) return cfg.quiverNormalColor;
        double ratio = state.amount() / (double)Math.max(1, cfg.quiverCapacity);
        if (ratio <= .1) return cfg.quiverLowColor;
        if (ratio <= .5) return 0xFFFFFF55;
        return cfg.quiverNormalColor;
    }

    private static boolean configured() { return cfg != null && cfg.enabled; }
    private static boolean allowedWorld() { return cfg != null && (ConstellationClient.loc().onHypixel() || cfg.quiverLocalWorlds || System.currentTimeMillis() < diagnosticUntil); }
    private static List<String> lore(ItemStack stack) { ItemLore lore = stack.get(DataComponents.LORE); return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList(); }
    private static String clean(String value) { String plain = ChatFormatting.stripFormatting(value); return plain == null ? "" : plain.trim(); }
    private static String compact(int value) { if (value < 1_000) return Integer.toString(value); if (value < 1_000_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000d); return String.format(Locale.ROOT, "%.1fm", value / 1_000_000d); }
    private static void reset() { state = null; pendingDungeonReminder = false; wasInDungeon = false; warnedLow = false; diagnosticUntil = 0; levelIdentity = null; }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("quiverdisplay")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("test").executes(c -> test()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("visibility").then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word()).executes(c -> visibility(StringArgumentType.getString(c, "mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("threshold").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("amount", IntegerArgumentType.integer(0, 100000)).executes(c -> number("threshold", IntegerArgumentType.getInteger(c, "amount")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("capacity").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("amount", IntegerArgumentType.integer(1, 100000)).executes(c -> number("capacity", IntegerArgumentType.getInteger(c, "amount")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word()).executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static int status() { local("HUD " + on(cfg.quiverHud) + ", true count " + on(cfg.quiverTrueHotbarCount) + ", warning " + on(cfg.quiverLowWarning) + " at " + cfg.quiverLowAmount + ", visibility " + cfg.quiverVisibility + "; arrows " + (state == null ? "unknown" : state.arrowName() + " x" + state.amount()) + "."); return 1; }
    private static int test() { state = new State("Flint", Math.max(0, cfg.quiverLowAmount)); diagnosticUntil = System.currentTimeMillis() + 15_000; showWarning("Low on arrows: " + state.amount()); local("Quiver test state shown for 15 seconds."); return 1; }
    private static int visibility(String raw) { String value = raw.toUpperCase(Locale.ROOT); if (!value.equals("ALWAYS") && !value.equals("BOW_INVENTORY") && !value.equals("ONLY_BOW_HAND")) { local("Visibility must be always, bow_inventory, or only_bow_hand."); return 0; } cfg.quiverVisibility = value; save(); return status(); }
    private static int number(String type, int value) { if (type.equals("threshold")) cfg.quiverLowAmount = value; else cfg.quiverCapacity = value; save(); return status(); }
    private static int option(String name, String raw) {
        Boolean value = switch (raw.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; };
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "hud" -> cfg.quiverHud = value; case "count" -> cfg.quiverTrueHotbarCount = value;
            case "icon" -> cfg.quiverShowIcon = value; case "color" -> cfg.quiverColorAmount = value;
            case "warning" -> cfg.quiverLowWarning = value; case "dungeon" -> cfg.quiverWarningInDungeons = value;
            case "reminder" -> cfg.quiverReminderAfterDungeon = value; case "title" -> cfg.quiverWarningTitle = value;
            case "chat" -> cfg.quiverWarningChat = value; case "sound" -> cfg.quiverWarningSound = value;
            case "local" -> cfg.quiverLocalWorlds = value;
            default -> { local("Option must be hud, count, icon, color, warning, dungeon, reminder, title, chat, sound, or local."); return 0; }
        }
        save(); return status();
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5[Quiver] §f" + text)); }
}
