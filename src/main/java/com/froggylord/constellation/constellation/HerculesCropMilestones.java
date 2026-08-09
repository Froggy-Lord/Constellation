package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): data/garden/cropmilestones/CropMilestonesApi.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/farming/GardenCropMilestoneDisplay.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/tracker/GardenCropBreakTracker.kt
public final class HerculesCropMilestones {
    public record Row(String label, String value, int color) {}

    private static final Pattern CROP_LORE = Pattern.compile("^Harvest\\s+(?<crop>.+?)\\s+on\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_LORE = Pattern.compile("^Total:\\s*(?<amount>[\\d,]+).*$", Pattern.CASE_INSENSITIVE);
    private static final int[] COMMON = data("30,50,80,200,350,700,1500,2500,3500,5000,6500,8000,10000,20000,35000,50000,75000,100000,175000,250000,325000,400000,500000,650000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000");
    private static final int[] MOONFLOWER = data("30,50,80,200,700,700,1500,2500,3500,5000,6500,8000,10000,20000,35000,50000,75000,100000,175000,250000,325000,400000,500000,650000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000,800000");
    private static final int[] ROOT = data("100,150,250,500,1000,2000,4500,9000,12000,15000,20000,25000,35000,70000,120000,180000,250000,350000,600000,850000,1100000,1400000,1800000,2200000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000,2600000");
    private static final int[] WART_COCOA = data("90,150,250,500,1000,2000,4000,7500,10000,15000,20000,25000,30000,50000,100000,150000,200000,300000,500000,750000,1000000,1300000,1600000,2000000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000,2400000");
    private static final int[] THIN = data("60,100,160,400,700,1400,3000,5000,7000,10000,13000,16000,20000,40000,70000,100000,150000,200000,350000,500000,650000,800000,1000000,1300000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000,1600000");
    private static final int[] MELON = data("150,250,400,1000,1800,3500,7500,12500,17500,25000,32500,40000,50000,100000,175000,250000,375000,500000,875000,1200000,1600000,2000000,2500000,3200000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000,4000000");
    private static final Map<HerculesGardenTracker.Crop, int[]> TIERS = tiers();

    private static HerculesConfig cfg;
    private static String profile = "";
    private static String toolKey = "";
    private static long toolCounter = -1;
    private static long toolCounterAt;
    private static double cropsPerSecond;
    private static long lastGainAt;
    private static boolean dirty;
    private static long lastSave;
    private static String warnedKey = "";
    private static long lastMenuRead;

    private HerculesCropMilestones() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        maps();
        ConstellationClient.tick().every(2, "hercules-crop-milestones", HerculesCropMilestones::tick);
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> resetTransient());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> {
            flush();
            resetTransient();
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cropmilestone")
            .executes(ctx -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sync").executes(ctx -> sync()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("set")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("crop", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Long>argument("counter", LongArgumentType.longArg(0))
                        .executes(ctx -> setCounter(StringArgumentType.getString(ctx, "crop"), LongArgumentType.getLong(ctx, "counter"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("goal")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("crop", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("tier", IntegerArgumentType.integer(1, 500))
                        .executes(ctx -> setGoal(StringArgumentType.getString(ctx, "crop"), IntegerArgumentType.getInteger(ctx, "tier"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cleargoal")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("crop", StringArgumentType.word())
                    .executes(ctx -> clearGoal(StringArgumentType.getString(ctx, "crop")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(ctx -> option(StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "state")))))));
    }

    public static boolean hudVisible() {
        return cfg != null && cfg.enabled && cfg.cropMilestoneProgress && inGarden() && currentCrop() != null && counter(currentCrop()) >= 0;
    }

    public static List<Row> hudRows() {
        HerculesGardenTracker.Crop crop = currentCrop();
        long count = counter(crop);
        if (crop == null || count < 0) return List.of();
        Goal goal = goal(crop, count);
        long have = goal.absolute ? count : Math.max(0, count - totalForTier(crop, goal.currentTier));
        long need = goal.absolute ? totalForTier(crop, goal.targetTier) : tierAmount(crop, goal.targetTier);
        double percentage = need <= 0 ? 1 : Math.clamp((double) have / need, 0, 1);
        List<Row> rows = new ArrayList<>();
        for (String raw : cfg.cropMilestoneRowOrder.split(",")) {
            switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "tier" -> { if (cfg.cropMilestoneShowTier) rows.add(new Row(crop.display(), goal.currentTier + " -> " + goal.targetTier, 0xFF55FFFF)); }
                case "progress" -> { if (cfg.cropMilestoneShowProgress) rows.add(new Row("Progress", number(have) + "/" + number(need), 0xFFFFFF55)); }
                case "percent" -> { if (cfg.cropMilestoneShowPercent) rows.add(new Row("Percent", decimal(percentage * 100, 2) + "%", 0xFFFFFF55)); }
                case "time" -> { if (cfg.cropMilestoneShowTime) rows.add(new Row("ETA", eta(goal.remaining), 0xFF55FFFF)); }
                case "cropssecond" -> { if (cfg.cropMilestoneShowCropsSecond) rows.add(new Row("Crops/s", decimal(rate(), 1), 0xFFFFFF55)); }
                case "cropsminute" -> { if (cfg.cropMilestoneShowCropsMinute) rows.add(new Row("Crops/min", number(Math.round(rate() * 60)), 0xFFFFFF55)); }
                case "cropshour" -> { if (cfg.cropMilestoneShowCropsHour) rows.add(new Row("Crops/hour", number(Math.round(rate() * 3600)), 0xFFFFFF55)); }
                case "bps" -> {
                    if (cfg.cropMilestoneShowBlocksSecond) {
                        var rates = HerculesGardenTracker.rates();
                        rows.add(new Row("Blocks/s", rates == null ? "0" : decimal(rates.instantBps(), cfg.cropMilestoneBpsPrecision), 0xFFFFFF55));
                    }
                }
            }
        }
        return rows;
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!menu(screen) || slot == null) return;
        if (cfg.cropMilestoneInventoryAverage && slot.index == 2) {
            double average = averageTier();
            if (average >= 0) {
                String text = "Average: " + decimal(average, 2);
                graphics.text(Minecraft.getInstance().font, text, slot.x - 48, slot.y - 30, 0xFFFFFF55, true);
            }
        }
        if (!cfg.cropMilestoneInventoryTiers) return;
        HerculesGardenTracker.Crop crop = cropFromLore(slot.getItem());
        if (crop == null) return;
        long count = counter(crop);
        if (count < 0) return;
        int tier = currentTier(crop, count);
        String text = Integer.toString(cfg.cropMilestoneInventoryOverflow ? tier : Math.min(TIERS.get(crop).length, tier));
        graphics.text(Minecraft.getInstance().font, text, slot.x + 16 - Minecraft.getInstance().font.width(text), slot.y + 8, 0xFFFFFF55, true);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, ItemStack stack, List<Component> current) {
        if (!menu(screen) || !cfg.cropMilestoneTooltipTotalProgress || current == null) return current;
        HerculesGardenTracker.Crop crop = cropFromLore(stack);
        long count = counter(crop);
        if (crop == null || count < 0) return current;
        long max = totalForTier(crop, TIERS.get(crop).length);
        double percent = max <= 0 ? 0 : Math.clamp((double) count / max, 0, 1);
        List<Component> result = new ArrayList<>(current);
        int index = result.size();
        for (int i = 0; i < result.size(); i++) if (clean(result.get(i).getString()).equalsIgnoreCase("Rewards:")) { index = i; break; }
        result.add(index, Component.literal("§7Progress to Tier " + TIERS.get(crop).length + ": §e" + decimal(percent * 100, 2) + "%"));
        result.add(index + 1, Component.literal("§e" + number(count) + "§6/§e" + shortNumber(max)));
        return result;
    }

    private static void tick() {
        maps();
        String currentProfile = profile();
        if (!currentProfile.equals(profile)) {
            flush();
            profile = currentProfile;
            resetTool();
        }
        if (!active()) {
            resetTool();
            flushSoon();
            return;
        }
        readMenu();
        readTool();
        warnClose();
        flushSoon();
    }

    private static void readMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || !menu(screen)) return;
        long now = System.currentTimeMillis();
        if (now - lastMenuRead < 500) return;
        lastMenuRead = now;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            HerculesGardenTracker.Crop crop = cropFromLore(stack);
            Long total = totalFromLore(stack);
            if (crop == null || total == null) continue;
            String key = key(crop);
            if (!Objects.equals(cfg.cropMilestoneCounters.get(key), total)) {
                cfg.cropMilestoneCounters.put(key, total);
                dirty = true;
            }
        }
    }

    private static void readTool() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        HerculesGardenTracker.Crop crop = HerculesGardenTracker.cropInHand(stack);
        Long value = toolCounter(stack);
        if (crop == null || value == null) {
            resetTool();
            return;
        }
        String key = crop.name() + "|" + toolIdentity(stack);
        long now = System.currentTimeMillis();
        if (!key.equals(toolKey)) {
            toolKey = key;
            toolCounter = value;
            toolCounterAt = now;
            return;
        }
        long delta = value - toolCounter;
        long elapsed = now - toolCounterAt;
        toolCounter = value;
        toolCounterAt = now;
        if (delta <= 0 || delta > 10_000_000 || elapsed <= 0) return;
        if (crop == HerculesGardenTracker.Crop.WHEAT) delta = Math.round(delta * .4);
        if (delta <= 0) return;
        double observed = delta / (elapsed / 1000.0);
        cropsPerSecond = cropsPerSecond <= 0 ? observed : cropsPerSecond * .65 + observed * .35;
        lastGainAt = now;
        if (!cfg.cropMilestoneCounters.containsKey(key(crop))) return;
        cfg.cropMilestoneCounters.merge(key(crop), delta, Long::sum);
        dirty = true;
    }

    private static void warnClose() {
        if (!cfg.cropMilestoneWarnClose || rate() <= 0) return;
        HerculesGardenTracker.Crop crop = currentCrop();
        long count = counter(crop);
        if (crop == null || count < 0) return;
        Goal goal = goal(crop, count);
        double seconds = goal.remaining / rate();
        String key = profile + "|" + crop.name() + "|" + goal.targetTier;
        if (seconds > Math.clamp(cfg.cropMilestoneWarningSeconds, 1, 30) || key.equals(warnedKey)) return;
        warnedKey = key;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (cfg.cropMilestoneWarningTitle) mc.gui.hud.setTitle(Component.literal("§b" + crop.display() + " " + goal.targetTier + " in " + Math.max(1, (int)Math.ceil(seconds)) + "s"));
        if (cfg.cropMilestoneWarningSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1, 1);
    }

    private static Goal goal(HerculesGardenTracker.Crop crop, long count) {
        int current = currentTier(crop, count);
        Integer custom = cfg.cropMilestoneGoals.get(goalKey(crop));
        int target;
        boolean absolute;
        if (custom != null && custom > current) { target = custom; absolute = true; }
        else if (cfg.cropMilestoneShowMaxTier && current < TIERS.get(crop).length) { target = TIERS.get(crop).length; absolute = true; }
        else { target = current + 1; absolute = false; }
        long targetTotal = totalForTier(crop, target);
        return new Goal(current, target, Math.max(0, targetTotal - count), absolute);
    }

    private record Goal(int currentTier, int targetTier, long remaining, boolean absolute) {}

    private static int currentTier(HerculesGardenTracker.Crop crop, long count) {
        int[] data = TIERS.get(crop);
        long total = 0;
        for (int tier = 0; tier < data.length; tier++) {
            total += data[tier];
            if (total >= count) return tier;
        }
        return data.length + (int)Math.max(0, (count - total) / data[data.length - 1]);
    }

    private static long totalForTier(HerculesGardenTracker.Crop crop, int requested) {
        if (requested <= 0) return 0;
        int[] data = TIERS.get(crop);
        long total = 0;
        for (int i = 0; i < Math.min(requested, data.length); i++) total += data[i];
        if (requested > data.length) total += (long)(requested - data.length) * data[data.length - 1];
        return total;
    }

    private static long tierAmount(HerculesGardenTracker.Crop crop, int tier) {
        int[] data = TIERS.get(crop);
        return data[Math.clamp(tier - 1, 0, data.length - 1)];
    }

    private static HerculesGardenTracker.Crop currentCrop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        HerculesGardenTracker.Crop held = HerculesGardenTracker.cropInHand(mc.player.getMainHandItem());
        if (held != null) return held;
        if (!cfg.cropMilestoneShowWithoutTool) return null;
        var rates = HerculesGardenTracker.rates();
        return rates == null ? null : crop(rates.crop());
    }

    private static HerculesGardenTracker.Crop cropFromLore(ItemStack stack) {
        ItemLore lore = stack == null ? null : stack.get(DataComponents.LORE);
        if (lore == null) return null;
        for (Component line : lore.lines()) {
            Matcher matcher = CROP_LORE.matcher(clean(line.getString()));
            if (matcher.matches()) return crop(matcher.group("crop"));
        }
        return null;
    }

    private static Long totalFromLore(ItemStack stack) {
        ItemLore lore = stack == null ? null : stack.get(DataComponents.LORE);
        if (lore == null) return null;
        for (Component line : lore.lines()) {
            Matcher matcher = TOTAL_LORE.matcher(clean(line.getString()));
            if (!matcher.matches()) continue;
            try { return Long.parseLong(matcher.group("amount").replace(",", "")); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static Long toolCounter(ItemStack stack) {
        CompoundTag extra = extra(stack);
        if (extra.contains("farmed_cultivating")) return extra.getLongOr("farmed_cultivating", 0);
        if (extra.contains("levelable_exp")) return (long)extra.getDoubleOr("levelable_exp", 0);
        if (extra.contains("mined_crops")) return extra.getLongOr("mined_crops", 0);
        return null;
    }

    private static String toolIdentity(ItemStack stack) {
        CompoundTag extra = extra(stack);
        String uuid = extra.getStringOr("uuid", "");
        return uuid.isBlank() ? LyraTooltips.marketId(stack) : uuid;
    }

    private static CompoundTag extra(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return new CompoundTag();
        CompoundTag root = custom.copyTag(), legacy = root.getCompoundOrEmpty("ExtraAttributes");
        return legacy.isEmpty() ? root : legacy;
    }

    private static int sync() {
        Minecraft mc = Minecraft.getInstance();
        if (!inGarden() || mc.getConnection() == null) { local("Enter the Garden first."); return 0; }
        mc.getConnection().sendCommand("cropmilestones");
        local("Reading exact counters when the Crop Milestones menu opens.");
        return 1;
    }

    private static int setCounter(String name, long count) {
        HerculesGardenTracker.Crop crop = crop(name);
        if (crop == null) { local("Unknown crop."); return 0; }
        cfg.cropMilestoneCounters.put(key(crop), count);
        save();
        return status();
    }

    private static int setGoal(String name, int tier) {
        HerculesGardenTracker.Crop crop = crop(name);
        if (crop == null) { local("Unknown crop."); return 0; }
        cfg.cropMilestoneGoals.put(goalKey(crop), tier);
        save();
        return status();
    }

    private static int clearGoal(String name) {
        HerculesGardenTracker.Crop crop = crop(name);
        if (crop == null) { local("Unknown crop."); return 0; }
        cfg.cropMilestoneGoals.remove(goalKey(crop));
        save();
        return status();
    }

    private static int option(String name, String state) {
        Boolean value = bool(state);
        if (value == null) { local("Use on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled", "progress" -> cfg.cropMilestoneProgress = value;
            case "withouttool" -> cfg.cropMilestoneShowWithoutTool = value;
            case "maxtier" -> cfg.cropMilestoneShowMaxTier = value;
            case "warning" -> cfg.cropMilestoneWarnClose = value;
            case "title" -> cfg.cropMilestoneWarningTitle = value;
            case "sound" -> cfg.cropMilestoneWarningSound = value;
            case "tiers" -> cfg.cropMilestoneInventoryTiers = value;
            case "overflow" -> cfg.cropMilestoneInventoryOverflow = value;
            case "tooltip" -> cfg.cropMilestoneTooltipTotalProgress = value;
            default -> { local("Options: progress, withouttool, maxtier, warning, title, sound, tiers, overflow, tooltip."); return 0; }
        }
        save();
        return status();
    }

    private static int status() {
        HerculesGardenTracker.Crop crop = currentCrop();
        long count = counter(crop);
        if (crop == null || count < 0) local("Crop milestones " + on(cfg.cropMilestoneProgress) + ". Hold a farming tool or run /cropmilestone sync.");
        else {
            Goal goal = goal(crop, count);
            local(crop.display() + " milestone " + goal.currentTier + ", " + number(count) + " crops, "
                + number(goal.remaining) + " to tier " + goal.targetTier + ".");
        }
        return 1;
    }

    private static long counter(HerculesGardenTracker.Crop crop) {
        if (crop == null) return -1;
        return cfg.cropMilestoneCounters.getOrDefault(key(crop), -1L);
    }

    public static long collection(HerculesGardenTracker.Crop crop) { return counter(crop); }
    public static double collectionRate() { return rate(); }

    private static double averageTier() {
        double total = 0;
        int found = 0;
        for (HerculesGardenTracker.Crop crop : HerculesGardenTracker.Crop.values()) {
            long count = counter(crop);
            if (count < 0) continue;
            int tier = currentTier(crop, count);
            total += cfg.cropMilestoneInventoryOverflow ? tier : Math.min(TIERS.get(crop).length, tier);
            found++;
        }
        return found == 0 ? -1 : total / HerculesGardenTracker.Crop.values().length;
    }

    private static String key(HerculesGardenTracker.Crop crop) { return profile() + "|" + crop.name(); }
    private static String goalKey(HerculesGardenTracker.Crop crop) { return profile() + "|" + crop.name(); }
    private static String profile() {
        String value = LyraStorageValue.currentProfileKey();
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }

    private static double rate() {
        if (System.currentTimeMillis() - lastGainAt > Math.max(2, cfg.cropMilestoneBpsResetSeconds) * 1000L) return 0;
        return Math.max(0, cropsPerSecond);
    }

    private static String eta(long remaining) {
        double rate = rate();
        if (rate <= 0) return "Waiting";
        long seconds = (long)Math.ceil(remaining / rate);
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private static HerculesGardenTracker.Crop crop(String raw) {
        if (raw == null) return null;
        String value = raw.replace("_", "").replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
        for (HerculesGardenTracker.Crop crop : HerculesGardenTracker.Crop.values())
            if (crop.name().replace("_", "").toLowerCase(Locale.ROOT).equals(value)
                || crop.display().replace(" ", "").toLowerCase(Locale.ROOT).equals(value)) return crop;
        return null;
    }

    private static boolean menu(AbstractContainerScreen<?> screen) {
        return cfg != null && cfg.enabled && inGarden() && screen != null && clean(screen.getTitle().getString()).equals("Crop Milestones");
    }
    private static boolean active() { return cfg != null && cfg.enabled && cfg.cropMilestoneProgress && inGarden(); }
    private static boolean inGarden() { return ConstellationClient.loc().area() == LocationManager.SkyblockArea.GARDEN; }
    private static String clean(String value) { String clean = ChatFormatting.stripFormatting(value); return clean == null ? "" : clean.trim(); }
    private static String number(long value) { return String.format(Locale.ROOT, "%,d", value); }
    private static String shortNumber(long value) { if (value >= 1_000_000_000) return decimal(value / 1e9, 2) + "b"; if (value >= 1_000_000) return decimal(value / 1e6, 2) + "m"; if (value >= 1_000) return decimal(value / 1e3, 1) + "k"; return Long.toString(value); }
    private static String decimal(double value, int precision) { return String.format(Locale.ROOT, "%." + Math.clamp(precision, 0, 6) + "f", value); }
    private static Boolean bool(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void local(String message) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§2[Milestones] §f" + message)); }

    private static void resetTool() { toolKey = ""; toolCounter = -1; toolCounterAt = 0; cropsPerSecond = 0; lastGainAt = 0; }
    private static void resetTransient() { flush(); profile = ""; resetTool(); warnedKey = ""; lastMenuRead = 0; }
    private static void flushSoon() { if (dirty && System.currentTimeMillis() - lastSave >= 5000) save(); }
    private static void flush() { if (dirty) save(); }
    private static void save() { dirty = false; lastSave = System.currentTimeMillis(); ConstellationClient.saveConfig(); }
    private static void maps() { if (cfg.cropMilestoneCounters == null) cfg.cropMilestoneCounters = new HashMap<>(); if (cfg.cropMilestoneGoals == null) cfg.cropMilestoneGoals = new HashMap<>(); }
    private static int[] data(String raw) { return Arrays.stream(raw.split(",")).mapToInt(Integer::parseInt).toArray(); }

    private static Map<HerculesGardenTracker.Crop, int[]> tiers() {
        Map<HerculesGardenTracker.Crop, int[]> map = new EnumMap<>(HerculesGardenTracker.Crop.class);
        map.put(HerculesGardenTracker.Crop.WHEAT, COMMON); map.put(HerculesGardenTracker.Crop.PUMPKIN, COMMON);
        map.put(HerculesGardenTracker.Crop.MUSHROOM, COMMON); map.put(HerculesGardenTracker.Crop.SUNFLOWER, COMMON);
        map.put(HerculesGardenTracker.Crop.MOONFLOWER, MOONFLOWER); map.put(HerculesGardenTracker.Crop.CARROT, ROOT);
        map.put(HerculesGardenTracker.Crop.POTATO, ROOT); map.put(HerculesGardenTracker.Crop.NETHER_WART, WART_COCOA);
        map.put(HerculesGardenTracker.Crop.COCOA, WART_COCOA); map.put(HerculesGardenTracker.Crop.SUGAR_CANE, THIN);
        map.put(HerculesGardenTracker.Crop.CACTUS, THIN); map.put(HerculesGardenTracker.Crop.WILD_ROSE, THIN);
        map.put(HerculesGardenTracker.Crop.MELON, MELON);
        return Map.copyOf(map);
    }
}
