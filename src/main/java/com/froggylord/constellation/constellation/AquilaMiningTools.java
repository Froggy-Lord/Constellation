package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): utils/ItemUtils.java
// ported from Skyblocker (LGPL-3.0-or-later): mixins/ItemStackMixin.java
// ported from Devonian (GPL-3.0-only): features/misc/ActionbarParser.kt
public final class AquilaMiningTools {
    public enum Type { DRILL, PICKONIMBUS }
    public record Durability(Type type, String name, String key, int current, int maximum) {
        public int percent() { return maximum <= 0 ? 0 : Math.clamp((int) Math.round(current * 100.0 / maximum), 0, 100); }
    }

    private static final Pattern FUEL = Pattern.compile("(?i)Fuel:\\s*([\\d,.]+)\\s*/\\s*([\\d,.]+)\\s*([kKmM]?)");
    private static final long PICKONIMBUS_NERF_MILLIS = 1_726_531_200_000L;
    private static AquilaConfig cfg;
    private static Durability state;
    private static final Set<String> WARNED = new HashSet<>();
    private static boolean initialized;

    private AquilaMiningTools() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(2, "aquila-mining-tools", AquilaMiningTools::update);
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static void update() {
        if (!active()) { state = null; WARNED.clear(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { state = null; return; }
        Durability next = durability(mc.player.getMainHandItem());
        if (next == null && !cfg.miningToolHeldOnly) {
            if (cfg.miningToolIncludeHotbar) {
                for (int slot = 0; slot < 9 && next == null; slot++) next = durability(mc.player.getInventory().getItem(slot));
            }
            if (cfg.miningToolIncludeInventory) {
                for (int slot = 9; slot < mc.player.getInventory().getContainerSize() && next == null; slot++)
                    next = durability(mc.player.getInventory().getItem(slot));
            }
        }
        state = selected(next) && !(cfg.miningToolHideFull && next.current() >= next.maximum()) ? next : null;
        warn(state);
    }

    private static boolean active() {
        if (cfg == null || !cfg.enabled || !cfg.miningToolSuite || !ConstellationClient.loc().onHypixel()) return false;
        return !cfg.miningToolOnlyInMiningAreas || miningArea();
    }

    private static boolean miningArea() {
        SkyblockArea area = ConstellationClient.loc().area();
        return area == SkyblockArea.GOLD_MINE || area == SkyblockArea.DEEP_CAVERNS || area == SkyblockArea.DWARVEN_MINES
            || area == SkyblockArea.CRYSTAL_HOLLOWS || area == SkyblockArea.GLACITE_TUNNELS
            || area == SkyblockArea.GLACITE_MINESHAFT;
    }

    private static boolean selected(Durability value) {
        return value != null && (value.type() == Type.DRILL ? cfg.drillFuelHud : cfg.pickonimbusHud);
    }

    private static void warn(Durability value) {
        if (value == null || !cfg.miningToolLowAlert) return;
        int threshold = Math.clamp(cfg.miningToolLowPercent, 1, 100);
        if (value.percent() > threshold) {
            WARNED.remove(value.key());
            return;
        }
        if (!WARNED.add(value.key())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String resource = value.type() == Type.DRILL ? "fuel" : "durability";
        String text = value.name() + " has " + value.percent() + "% " + resource + " left";
        if (cfg.miningToolLowAlertChat)
            mc.player.sendSystemMessage(Component.literal("§6[Mining Tool] §f" + text + "."));
        if (cfg.miningToolLowAlertTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTitle(Component.literal("Mining tool is low").withColor(cfg.miningToolDangerColor & 0xFFFFFF));
            mc.gui.hud.setSubtitle(Component.literal(text));
        }
        if (cfg.miningToolLowAlertSound)
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .8f, .7f);
    }

    public static Durability durability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag extra = extra(stack);
        String id = extra.getStringOr("id", "").toUpperCase(Locale.ROOT);
        String name = clean(stack.getHoverName().getString());
        if ("PICKONIMBUS".equals(id)) {
            int current = extra.contains("pickonimbus_durability") ? extra.getIntOr("pickonimbus_durability", 0) : 2000;
            long obtained = extra.getLongOr("timestamp", 0L);
            int maximum = current > 2000 || obtained > 0 && obtained < PICKONIMBUS_NERF_MILLIS ? 5000 : 2000;
            return new Durability(Type.PICKONIMBUS, name, identity(extra, id, name), Math.max(0, current), maximum);
        }
        if (!extra.contains("drill_fuel")) return null;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        for (Component line : lore.lines()) {
            Matcher matcher = FUEL.matcher(clean(line.getString()));
            if (!matcher.find()) continue;
            int current = amount(matcher.group(1), "");
            int maximum = amount(matcher.group(2), matcher.group(3));
            if (maximum <= 0) return null;
            return new Durability(Type.DRILL, name, identity(extra, id, name), Math.clamp(current, 0, maximum), maximum);
        }
        return null;
    }

    private static int amount(String raw, String suffix) {
        try {
            double value = Double.parseDouble(raw.replace(",", ""));
            if (suffix.equalsIgnoreCase("k")) value *= 1000;
            else if (suffix.equalsIgnoreCase("m")) value *= 1_000_000;
            return Math.max(0, (int) Math.round(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static CompoundTag extra(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return new CompoundTag();
        CompoundTag root = data.copyTag();
        CompoundTag legacy = root.getCompoundOrEmpty("ExtraAttributes");
        return legacy.isEmpty() ? root : legacy;
    }

    private static String identity(CompoundTag extra, String id, String name) {
        String uuid = extra.getStringOr("uuid", "");
        return uuid.isEmpty() ? id + ":" + name : uuid;
    }

    public static boolean customBarEnabled() {
        return active() && cfg.miningToolItemBars;
    }

    public static int barWidth(ItemStack stack) {
        Durability value = durability(stack);
        return value == null ? -1 : Math.clamp((int) Math.round(value.current() * 13.0 / value.maximum()), 0, 13);
    }

    public static int barColor(ItemStack stack) {
        Durability value = durability(stack);
        if (value == null) return CommonColors.WHITE;
        float ratio = Math.clamp(value.current() / (float) value.maximum(), 0f, 1f);
        int red = Math.round(255 * (1f - ratio));
        int green = Math.round(255 * ratio);
        return red << 16 | green << 8;
    }

    public static Durability state() { return state; }
    public static AquilaConfig config() { return cfg; }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("miningtools")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("threshold")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("percent", IntegerArgumentType.integer(1, 100))
                    .executes(context -> { cfg.miningToolLowPercent = IntegerArgumentType.getInteger(context, "percent"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("barwidth")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("characters", IntegerArgumentType.integer(5, 30))
                    .executes(context -> { cfg.miningToolBarWidth = IntegerArgumentType.getInteger(context, "characters"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> color(StringArgumentType.getString(context, "target"), StringArgumentType.getString(context, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int option(String name, String raw) {
        Boolean value = parse(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.miningToolSuite = value;
            case "drill" -> cfg.drillFuelHud = value;
            case "pickonimbus" -> cfg.pickonimbusHud = value;
            case "itembars" -> cfg.miningToolItemBars = value;
            case "heldonly" -> cfg.miningToolHeldOnly = value;
            case "hotbar" -> cfg.miningToolIncludeHotbar = value;
            case "inventory" -> cfg.miningToolIncludeInventory = value;
            case "miningonly" -> cfg.miningToolOnlyInMiningAreas = value;
            case "hidefull" -> cfg.miningToolHideFull = value;
            case "name" -> cfg.miningToolShowName = value;
            case "current" -> cfg.miningToolShowCurrent = value;
            case "percent" -> cfg.miningToolShowPercent = value;
            case "bar" -> cfg.miningToolShowBar = value;
            case "alert" -> cfg.miningToolLowAlert = value;
            case "chat" -> cfg.miningToolLowAlertChat = value;
            case "title" -> cfg.miningToolLowAlertTitle = value;
            case "sound" -> cfg.miningToolLowAlertSound = value;
            default -> { local("Unknown mining-tool option."); return 0; }
        }
        save();
        return status();
    }

    private static int status() {
        String found = state == null ? "no selected tool" : state.name() + " " + state.current() + "/" + state.maximum();
        local("Helper " + on(cfg.miningToolSuite) + ", item bars " + on(cfg.miningToolItemBars) + ", " + found + ".");
        return 1;
    }

    private static int color(String target, String raw) {
        Integer value = parseColor(raw);
        if (value == null) { local("Color must be an eight-digit ARGB hex value."); return 0; }
        switch (target.toLowerCase(Locale.ROOT)) {
            case "good" -> cfg.miningToolGoodColor = value;
            case "warning" -> cfg.miningToolWarningColor = value;
            case "danger" -> cfg.miningToolDangerColor = value;
            default -> { local("Color target must be good, warning, or danger."); return 0; }
        }
        save();
        return status();
    }

    private static Integer parseColor(String raw) {
        try {
            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            if (value.length() != 8) return null;
            return (int) Long.parseLong(value, 16);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void reset() { state = null; WARNED.clear(); }
    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[Mining Tool] §f" + text));
    }
    private static String clean(String text) { return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim(); }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static Boolean parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
}
