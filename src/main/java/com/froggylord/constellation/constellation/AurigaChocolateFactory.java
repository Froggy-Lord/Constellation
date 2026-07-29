package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/chocolatefactory/ChocolateFactorySolver.java, TimeTowerReminder.java
// ported from Devonian (GPL-3.0-only): features/misc/FactoryHelper.kt
public final class AurigaChocolateFactory {
    public record Upgrade(int slot, String name, long cost, double increase, double payback, boolean affordable, int level) {}
    public record State(long chocolate, double cps, double multiplier, List<Upgrade> upgrades,
                        int bestSlot, int affordableSlot, long prestigeRemaining, boolean canPrestige,
                        boolean maxPrestige, boolean towerActive, double towerMultiplier,
                        int availableEggs, int hitmanSlots, int hitmanMax) {}

    private static final Pattern CPS = Pattern.compile("([\\d,.]+) Chocolate per second");
    private static final Pattern CPS_GAIN = Pattern.compile("\\+([\\d,.]+) Chocolate per second");
    private static final Pattern MULTIPLIER = Pattern.compile("Total Multiplier: ([\\d.]+)x");
    private static final Pattern MULTIPLIER_GAIN = Pattern.compile("\\+([\\d.]+)x Chocolate per second");
    private static final Pattern COST = Pattern.compile("Cost:? ([\\d,]+) Chocolate");
    private static final Pattern COST_LINE = Pattern.compile("(?m)^([\\d,]+) Chocolate$");
    private static final Pattern CHOCOLATE = Pattern.compile("^([\\d,]+) Chocolate$");
    private static final Pattern LEVEL = Pattern.compile("\\[(\\d+)]|\\b([IVXLC]+)$");
    private static final Pattern PRESTIGE = Pattern.compile("Chocolate this Prestige: ([\\d,]+).*Requires ([\\d.]+[MB]) Chocolate");
    private static final Pattern TOWER_STATUS = Pattern.compile("Status: (ACTIVE|INACTIVE)");
    private static final Pattern TOWER_MULTIPLIER = Pattern.compile("by \\+([\\d.]+)x for (\\d+)h");
    private static final Pattern TOWER_CHAT = Pattern.compile("^TIME TOWER! Your Chocolate Factory production has increased by \\+([\\d.]+)x for (\\d+)h!$");
    private static final Pattern EGGS = Pattern.compile("Available eggs: (\\d+)");
    private static final Pattern HITMAN = Pattern.compile("Purchased slots: (\\d+)/(\\d+)");

    private static AurigaConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static State state = empty();
    private static int ticks;
    private static boolean warned;
    private static long previousTowerRemaining = Long.MIN_VALUE;
    private static int lastStray = -1;

    private AurigaChocolateFactory() {}

    public static void init(AurigaConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, opened, width, height) -> {
            if (!(opened instanceof AbstractContainerScreen<?> container)
                || !clean(container.getTitle().getString()).equals("Chocolate Factory")) return;
            screen = container;
            update(container);
            ScreenEvents.afterTick(opened).register(ignored -> update(container));
            ScreenEvents.remove(opened).register(ignored -> {
                if (screen == container) {
                    screen = null;
                    state = empty();
                    lastStray = -1;
                }
            });
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) chat(clean(message.getString()));
            return true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks >= 20) { ticks = 0; towerTick(); }
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("chocolatefactory")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(c -> {
                cfg.chocolateFactoryHelper = !cfg.chocolateFactoryHelper; save(); return status();
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cleartower").executes(c -> {
                cfg.chocolateFactoryTimeTowerExpiry.remove(profile()); save(); return status();
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("warning")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("minutes", IntegerArgumentType.integer(0, 60))
                    .executes(c -> { cfg.chocolateFactoryWarningMinutes = IntegerArgumentType.getInteger(c, "minutes"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static void update(AbstractContainerScreen<?> container) {
        if (container != screen || !active() || container.getMenu().slots.size() <= 51) return;
        long chocolate = parseLong(CHOCOLATE, clean(container.getMenu().getSlot(13).getItem().getHoverName().getString()), 1, -1);
        String production = joinedLore(container.getMenu().getSlot(45).getItem());
        double cps = parseDouble(CPS, production, 1, -1);
        double multiplier = parseDouble(MULTIPLIER, production, 1, 1);
        ArrayList<Upgrade> upgrades = new ArrayList<>();
        for (int slot = 28; slot <= 34; slot++) rabbit(container, slot, chocolate, multiplier).ifPresent(upgrades::add);
        coach(container, 42, chocolate, cps, multiplier).ifPresent(upgrades::add);
        upgrades.sort(Comparator.comparingDouble(Upgrade::payback));
        int best = upgrades.isEmpty() ? -1 : upgrades.getFirst().slot();
        int affordable = upgrades.stream().filter(Upgrade::affordable).mapToInt(Upgrade::slot).findFirst().orElse(-1);

        String prestige = joinedLore(container.getMenu().getSlot(27).getItem());
        Matcher pm = PRESTIGE.matcher(prestige);
        long prestigeRemaining = -1;
        if (pm.find()) prestigeRemaining = Math.max(0, suffix(pm.group(2)) - number(pm.group(1)));
        boolean canPrestige = prestige.contains("Click to prestige!");
        boolean maxPrestige = prestige.contains("reached max prestige");

        String tower = joinedLore(container.getMenu().getSlot(39).getItem());
        Matcher tm = TOWER_MULTIPLIER.matcher(tower);
        double towerMultiplier = tm.find() ? Double.parseDouble(tm.group(1)) : -1;
        Matcher ts = TOWER_STATUS.matcher(tower);
        boolean towerActive = ts.find() && ts.group(1).equals("ACTIVE");

        String hitman = joinedLore(container.getMenu().getSlot(51).getItem());
        int eggs = (int) parseLong(EGGS, hitman, 1, -1);
        Matcher hm = HITMAN.matcher(hitman);
        int purchased = -1, maximum = -1;
        if (hm.find()) { purchased = Integer.parseInt(hm.group(1)); maximum = Integer.parseInt(hm.group(2)); }
        state = new State(chocolate, cps, multiplier, List.copyOf(upgrades), best, affordable,
            prestigeRemaining, canPrestige, maxPrestige, towerActive, towerMultiplier, eggs, purchased, maximum);
        stray(container);
    }

    private static java.util.Optional<Upgrade> rabbit(AbstractContainerScreen<?> container, int slot, long chocolate, double multiplier) {
        ItemStack stack = container.getMenu().getSlot(slot).getItem();
        String lore = joinedLore(stack);
        List<Double> gains = all(CPS_GAIN, lore);
        if (gains.isEmpty()) return java.util.Optional.empty();
        double current = gains.size() > 1 ? gains.get(0) : 0;
        double next = gains.getLast();
        long cost = cost(lore);
        double increase = Math.max(0, next - current) * Math.max(1, multiplier);
        if (cost < 0 || increase <= 0) return java.util.Optional.empty();
        return java.util.Optional.of(new Upgrade(slot, clean(stack.getHoverName().getString()), cost, increase,
            cost / increase, chocolate >= cost, level(stack)));
    }

    private static java.util.Optional<Upgrade> coach(AbstractContainerScreen<?> container, int slot, long chocolate, double cps, double multiplier) {
        ItemStack stack = container.getMenu().getSlot(slot).getItem();
        String lore = joinedLore(stack);
        List<Double> gains = all(MULTIPLIER_GAIN, lore);
        if (gains.isEmpty() || cps <= 0 || multiplier <= 0) return java.util.Optional.empty();
        double current = gains.size() > 1 ? gains.get(0) : 0;
        double next = gains.getLast();
        long cost = cost(lore);
        double increase = cps / multiplier * Math.max(0, next - current);
        if (cost < 0 || increase <= 0) return java.util.Optional.empty();
        return java.util.Optional.of(new Upgrade(slot, clean(stack.getHoverName().getString()), cost, increase,
            cost / increase, chocolate >= cost, level(stack)));
    }

    private static void stray(AbstractContainerScreen<?> container) {
        int found = -1; boolean golden = false;
        for (int slot = 0; slot <= 26; slot++) {
            String name = clean(container.getMenu().getSlot(slot).getItem().getHoverName().getString());
            if (name.equals("CLICK ME!") || name.startsWith("Golden Rabbit - ")) {
                found = slot; golden = name.startsWith("Golden Rabbit"); break;
            }
        }
        if (found >= 0 && found != lastStray && cfg.chocolateFactoryStraySound) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && (!golden || cfg.chocolateFactoryGoldenSound))
                mc.player.playSound(golden ? SoundEvents.NOTE_BLOCK_HARP.value() : SoundEvents.NOTE_BLOCK_PLING.value(), .8f, golden ? 1.4f : 1f);
        }
        lastStray = found;
    }

    private static void chat(String text) {
        if (!active() || !cfg.chocolateFactoryTimeTower) return;
        Matcher matcher = TOWER_CHAT.matcher(text);
        if (!matcher.matches()) return;
        int hours = Integer.parseInt(matcher.group(2));
        cfg.chocolateFactoryTimeTowerExpiry.put(profile(), System.currentTimeMillis() + hours * 3_600_000L);
        warned = false;
        previousTowerRemaining = hours * 3_600_000L;
        save();
    }

    private static void towerTick() {
        if (!active() || !cfg.chocolateFactoryTimeTower) return;
        long expiry = towerExpiry();
        long remaining = expiry - System.currentTimeMillis();
        if (previousTowerRemaining == Long.MIN_VALUE) {
            previousTowerRemaining = remaining;
            warned = remaining > 0 && remaining <= Math.clamp(cfg.chocolateFactoryWarningMinutes, 0, 60) * 60_000L;
            return;
        }
        if (remaining <= 0) {
            if (expiry > 0 && previousTowerRemaining > 0) alert("Time Tower expired.");
            previousTowerRemaining = remaining;
            return;
        }
        long warning = Math.clamp(cfg.chocolateFactoryWarningMinutes, 0, 60) * 60_000L;
        if (!warned && previousTowerRemaining > warning && remaining <= warning) {
            warned = true;
            alert("Time Tower expires in " + formatTime(remaining / 1000.0) + ".");
        }
        previousTowerRemaining = remaining;
    }

    private static void alert(String text) {
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return;
        if (cfg.chocolateFactoryTimeTowerChat) local(text);
        if (cfg.chocolateFactoryTimeTowerTitle) mc.gui.hud.setTitle(Component.literal(text).withColor(0xFFAA00));
        if (cfg.chocolateFactoryTimeTowerSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .9f, .8f);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> container, Slot slot) {
        if (!active() || container != screen || slot == null) return;
        int color = 0;
        if (cfg.chocolateFactoryStrayRabbits && slot.index >= 0 && slot.index <= 26) {
            String name = clean(slot.getItem().getHoverName().getString());
            if (name.equals("CLICK ME!") || name.startsWith("Golden Rabbit - ")) color = cfg.chocolateFactoryStrayColor;
        }
        if (cfg.chocolateFactoryPrestige && slot.index == 27 && state.canPrestige()) color = cfg.chocolateFactoryPrestigeColor;
        Upgrade ranked = upgrade(slot.index);
        if (cfg.chocolateFactoryBestUpgrade && slot.index == state.bestSlot() && ranked != null)
            color = ranked.affordable() ? cfg.chocolateFactoryBestColor : cfg.chocolateFactoryUnaffordableColor;
        if (cfg.chocolateFactoryBestAffordable && slot.index == state.affordableSlot()) color = cfg.chocolateFactoryAffordableColor;
        if (color != 0) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> container, ItemStack stack, List<Component> original) {
        if (!active() || container != screen || !cfg.chocolateFactoryUpgradeTimers) return original;
        Slot hovered = ((com.froggylord.constellation.mixin.ContainerScreenAccessor) container).constellation$hoveredSlot();
        if (hovered == null) return original;
        Upgrade upgrade = upgrade(hovered.index);
        if (upgrade == null) return original;
        ArrayList<Component> out = new ArrayList<>(original);
        out.add(Component.literal("\u00a78----------------"));
        out.add(Component.literal("\u00a77CPS increase: \u00a76" + compact(upgrade.increase())));
        out.add(Component.literal("\u00a77Time until affordable: \u00a7e" + (upgrade.affordable() ? "Now" : formatTime((upgrade.cost() - state.chocolate()) / state.cps()))));
        if (cfg.chocolateFactoryPayback) out.add(Component.literal("\u00a77Payback time: \u00a7b" + formatTime(upgrade.payback())));
        int rank = state.upgrades().indexOf(upgrade) + 1;
        out.add(Component.literal("\u00a77Efficiency rank: \u00a7f" + rank + " / " + state.upgrades().size()));
        return out;
    }

    public static State state() { return state; }
    public static AurigaConfig config() { return cfg; }
    public static boolean visible() { return active() && (screen != null || towerExpiry() > 0); }
    public static long towerExpiry() { return cfg == null ? 0 : cfg.chocolateFactoryTimeTowerExpiry.getOrDefault(profile(), 0L); }
    public static String profile() {
        String value = LyraStorageValue.currentProfileKey();
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }
    public static String formatTime(double secondsValue) {
        if (!Double.isFinite(secondsValue) || secondsValue < 0) return "Unknown";
        long seconds = Math.max(0, Math.round(secondsValue));
        long days = seconds / 86400; seconds %= 86400; long hours = seconds / 3600; seconds %= 3600; long minutes = seconds / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m " + seconds % 60 + "s";
    }

    private static Upgrade upgrade(int slot) { return state.upgrades().stream().filter(u -> u.slot() == slot).findFirst().orElse(null); }
    private static State empty() { return new State(-1, -1, -1, List.of(), -1, -1, -1, false, false, false, -1, -1, -1, -1); }
    private static boolean active() { return cfg != null && cfg.enabled && cfg.chocolateFactoryHelper && ConstellationClient.loc().onHypixel(); }
    private static String joinedLore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE); if (lore == null) return "";
        return lore.lines().stream().map(line -> clean(line.getString())).reduce("", (a, b) -> a + "\n" + b);
    }
    private static long parseLong(Pattern pattern, String text, int group, long fallback) {
        Matcher matcher = pattern.matcher(text); return matcher.find() ? number(matcher.group(group)) : fallback;
    }
    private static long cost(String lore) {
        long strict = parseLong(COST, lore, 1, -1);
        return strict >= 0 ? strict : parseLong(COST_LINE, lore, 1, -1);
    }
    private static double parseDouble(Pattern pattern, String text, int group, double fallback) {
        Matcher matcher = pattern.matcher(text); if (!matcher.find()) return fallback;
        try { return Double.parseDouble(matcher.group(group).replace(",", "")); } catch (Exception ignored) { return fallback; }
    }
    private static List<Double> all(Pattern pattern, String text) {
        ArrayList<Double> values = new ArrayList<>(); Matcher matcher = pattern.matcher(text);
        while (matcher.find()) try { values.add(Double.parseDouble(matcher.group(1).replace(",", ""))); } catch (Exception ignored) {}
        return values;
    }
    private static long number(String value) { try { return Long.parseLong(value.replace(",", "")); } catch (Exception ignored) { return 0; } }
    private static long suffix(String value) {
        try {
            double number = Double.parseDouble(value.substring(0, value.length() - 1));
            return Math.round(number * (value.endsWith("B") ? 1_000_000_000L : 1_000_000L));
        } catch (Exception ignored) { return 0; }
    }
    private static int level(ItemStack stack) {
        Matcher matcher = LEVEL.matcher(clean(stack.getHoverName().getString())); if (!matcher.find()) return -1;
        if (matcher.group(1) != null) return Integer.parseInt(matcher.group(1));
        return roman(matcher.group(2));
    }
    private static int roman(String value) {
        int out = 0, last = 0; for (int i = value.length() - 1; i >= 0; i--) {
            int current = switch (value.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100; default -> 0; };
            out += current < last ? -current : current; last = Math.max(last, current);
        } return out;
    }
    private static String compact(double value) {
        if (!Double.isFinite(value) || value < 0) return "Unknown";
        if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000);
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000);
        return String.format(Locale.ROOT, "%.1f", value);
    }
    private static String clean(String value) { String stripped = net.minecraft.ChatFormatting.stripFormatting(value); return stripped == null ? "" : stripped.trim(); }
    private static int status() {
        String production = state.chocolate() < 0 ? "no menu data" : compact(state.chocolate()) + " Chocolate, " + compact(state.cps()) + " CPS";
        local("Helper " + on(cfg.chocolateFactoryHelper) + ", " + production
            + ", tower " + (towerExpiry() > System.currentTimeMillis() ? formatTime((towerExpiry() - System.currentTimeMillis()) / 1000.0) : "inactive") + ".");
        return 1;
    }
    private static int option(String name, String raw) {
        Boolean value = bool(raw); if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.chocolateFactoryHelper = value; case "hud" -> cfg.chocolateFactoryHud = value;
            case "best" -> cfg.chocolateFactoryBestUpgrade = value; case "affordable" -> cfg.chocolateFactoryBestAffordable = value;
            case "timers" -> cfg.chocolateFactoryUpgradeTimers = value; case "payback" -> cfg.chocolateFactoryPayback = value;
            case "prestige" -> cfg.chocolateFactoryPrestige = value; case "strays" -> cfg.chocolateFactoryStrayRabbits = value;
            case "straysound" -> cfg.chocolateFactoryStraySound = value; case "goldensound" -> cfg.chocolateFactoryGoldenSound = value;
            case "tower" -> cfg.chocolateFactoryTimeTower = value; case "towerchat" -> cfg.chocolateFactoryTimeTowerChat = value;
            case "towertitle" -> cfg.chocolateFactoryTimeTowerTitle = value; case "towersound" -> cfg.chocolateFactoryTimeTowerSound = value;
            case "hitman" -> cfg.chocolateFactoryShowHitman = value; case "levels" -> cfg.chocolateFactoryShowLevels = value;
            default -> { local("Unknown Chocolate Factory option."); return 0; }
        } save(); return status();
    }
    private static Boolean bool(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "on","true","yes","1" -> true; case "off","false","no","0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a76[Chocolate Factory] \u00a7f" + text)); }
    private static void save() { ConstellationClient.saveConfig(); }
}
