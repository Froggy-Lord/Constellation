package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.AurigaConfig;
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
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/SkyblockGuideHighlightFeature.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/PowerStoneGuideFeatures.kt
public final class AurigaSkyblockGuide {
    private static final Pattern CROSS = Pattern.compile("^\\s*\\u2716.*");
    private static final Pattern TOTAL = Pattern.compile("^Total Progress: \\d{1,2}(?:\\.\\d)?%$");
    private static final Pattern CATEGORY = Pattern.compile("^Progress to Complete Category: \\d{1,2}(?:\\.\\d)?%$");
    private static final List<Rule> RULES = List.of(
        new Rule("missing", Pattern.compile(".*Guide \u279c.*"), lines -> any(lines, CROSS)),
        new Rule("abiphone", Pattern.compile("Miscellaneous \u279c Abiphone Contac.*"), lines -> has(lines, "This task can only be completed once!")),
        new Rule("onetime", Pattern.compile("Core \u279c Bank Upgrades"), AurigaSkyblockGuide::once),
        new Rule("story", Pattern.compile("Core \u279c Fast Travels Unlocked"), AurigaSkyblockGuide::once),
        new Rule("onetime", Pattern.compile("Event \u279c Spooky Festival"), AurigaSkyblockGuide::once),
        new Rule("onetime", Pattern.compile("Miscellaneous \u279c The Dojo"), AurigaSkyblockGuide::once),
        new Rule("jacob", Pattern.compile("Event \u279c Jacob's Farming Contest"), AurigaSkyblockGuide::once),
        new Rule("onetime", Pattern.compile("Slaying \u279c .*"), AurigaSkyblockGuide::once),
        new Rule("story", Pattern.compile("Story \u279c Complete Objectives"), AurigaSkyblockGuide::once),
        new Rule("onetime", Pattern.compile("Mining \u279c Rock Milestones"), AurigaSkyblockGuide::once),
        new Rule("onetime", Pattern.compile("Fishing \u279c Dolphin Milestones"), AurigaSkyblockGuide::once),
        new Rule("essence", Pattern.compile("Essence Shop \u279c.*"), lines -> any(lines, CROSS)),
        new Rule("minions", Pattern.compile("Crafted Minions"), lines -> any(lines, CROSS) || has(lines, "You haven't crafted this minion.")),
        new Rule("story", Pattern.compile("Miscellaneous \u279c Harp Songs"), lines -> any(lines, CROSS)),
        new Rule("consumables", Pattern.compile("Miscellaneous \u279c Consumable Items"), lines -> lines.stream().anyMatch(line -> line.matches("This task can be completed \\d+ times!"))),
        new Rule("onetime", Pattern.compile("Complete Dungeons \u279c.*"), lines -> once(lines) || has(lines, "You have not unlocked the content")),
        new Rule("onetime", Pattern.compile("Dungeon \u279c Complete Dungeons"), lines -> any(lines, CROSS)),
        new Rule("menu", Pattern.compile("Tasks \u279c .*"), lines -> any(lines, TOTAL)),
        new Rule("menu", Pattern.compile("Skill Related Tasks"), lines -> any(lines, CATEGORY)),
        new Rule("collections", Pattern.compile("(?:\\w+ Collections|Collections)"), lines -> lines.stream().anyMatch(line ->
            line.startsWith("Progress to ") || line.startsWith("Find this item to add it to your")
                || line.startsWith("Kill this boss once to view collection") || line.matches("(?:Boss )?Collections (?:Unlocked|Maxed Out): .*")))
    );
    private static AurigaConfig cfg;

    private AurigaSkyblockGuide() {}

    public static void init(AurigaConfig config) { cfg = config; }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        Match match = match(screen, slot);
        if (match == null) return;
        int color = match.powerStone ? cfg.skyblockGuidePowerStoneColor : cfg.skyblockGuideMissingColor;
        if (cfg.skyblockGuideHighlight) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
        if (cfg.skyblockGuideMarker && !cfg.skyblockGuideMarkerText.isBlank())
            graphics.text(Minecraft.getInstance().font, cfg.skyblockGuideMarkerText.substring(0, Math.min(2, cfg.skyblockGuideMarkerText.length())),
                slot.x + 1, slot.y + 1, color | 0xFF000000, true);
    }

    public static boolean onSlotClick(AbstractContainerScreen<?> screen, Slot slot, int button) {
        Match match = match(screen, slot);
        if (match == null || !match.powerStone || !cfg.skyblockGuidePowerStoneClick || button != 0 || match.item.isBlank()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return false;
        mc.player.connection.sendCommand("bz " + match.item);
        return true;
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, ItemStack stack, List<Component> original) {
        if (original == null || screen == null || stack == null || stack.isEmpty() || !active() || !cfg.skyblockGuideTooltip) return original;
        Slot slot = screen.getMenu().slots.stream().filter(candidate -> candidate.getItem() == stack).findFirst().orElse(null);
        if (slot == null) return original;
        Match match = match(screen, slot);
        if (match == null) return original;
        List<Component> out = new ArrayList<>(original);
        if (match.powerStone) {
            if (cfg.skyblockGuidePowerStonePrice) {
                double unit = PriceProvider.purchaseValue(id(match.item));
                if (unit > 0) out.add(Component.literal(String.format(Locale.ROOT, "9x from Bazaar: §6%,.0f coins", unit * 9)));
            }
            if (cfg.skyblockGuidePowerStoneClick) out.add(Component.literal("§eClick to open this Power Stone in Bazaar."));
        } else out.add(Component.literal("§cMissing SkyBlock Guide progress."));
        return out;
    }

    private static Match match(AbstractContainerScreen<?> screen, Slot slot) {
        if (!active() || screen == null || slot == null || slot.index == 4 || slot.getItem().isEmpty() || playerSlot(slot)) return null;
        String title = clean(screen.getTitle().getString()).strip();
        List<String> lines = new ArrayList<>();
        lines.add(clean(slot.getItem().getHoverName().getString()).strip());
        lines.addAll(lore(slot.getItem()));
        if (cfg.skyblockGuidePowerStones && title.equals("Power Stones Guide") && has(lines, "Learned: Not Yet")) {
            int index = lines.indexOf("Power stone:");
            String item = index >= 0 && index + 1 < lines.size() ? lines.get(index + 1).strip() : "";
            return item.isBlank() ? null : new Match(true, item);
        }
        for (Rule rule : RULES) if (enabled(rule.key) && rule.title.matcher(title).matches() && rule.condition.test(lines))
            return new Match(false, "");
        return null;
    }

    private static boolean enabled(String key) {
        return switch (key) {
            case "menu" -> cfg.skyblockGuideMenu;
            case "missing" -> cfg.skyblockGuideMissingTasks;
            case "collections" -> cfg.skyblockGuideCollections;
            case "abiphone" -> cfg.skyblockGuideAbiphone;
            case "minions" -> cfg.skyblockGuideMinions;
            case "essence" -> cfg.skyblockGuideEssence;
            case "consumables" -> cfg.skyblockGuideConsumables;
            case "jacob" -> cfg.skyblockGuideJacob;
            case "story" -> cfg.skyblockGuideStory;
            case "onetime" -> cfg.skyblockGuideOneTime;
            default -> false;
        };
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("skyblockguide")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("Guide " + on(cfg.skyblockGuideHelper) + ", tasks " + on(cfg.skyblockGuideMissingTasks) + ", menus " + on(cfg.skyblockGuideMenu)
            + ", Power Stones " + on(cfg.skyblockGuidePowerStones) + ", one-time " + on(cfg.skyblockGuideOneTime) + ".");
        return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = parse(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.skyblockGuideHelper = value; case "menu" -> cfg.skyblockGuideMenu = value;
            case "tasks" -> cfg.skyblockGuideMissingTasks = value; case "powerstones" -> cfg.skyblockGuidePowerStones = value;
            case "collections" -> cfg.skyblockGuideCollections = value; case "abiphone" -> cfg.skyblockGuideAbiphone = value;
            case "minions" -> cfg.skyblockGuideMinions = value; case "essence" -> cfg.skyblockGuideEssence = value;
            case "consumables" -> cfg.skyblockGuideConsumables = value; case "jacob" -> cfg.skyblockGuideJacob = value;
            case "story" -> cfg.skyblockGuideStory = value; case "onetime" -> cfg.skyblockGuideOneTime = value;
            case "highlight" -> cfg.skyblockGuideHighlight = value; case "marker" -> cfg.skyblockGuideMarker = value;
            case "tooltip" -> cfg.skyblockGuideTooltip = value; case "price" -> cfg.skyblockGuidePowerStonePrice = value;
            case "click" -> cfg.skyblockGuidePowerStoneClick = value;
            default -> { local("Unknown SkyBlock Guide option."); return 0; }
        }
        ConstellationClient.saveConfig();
        return status();
    }

    private static boolean active() { return cfg != null && cfg.enabled && cfg.skyblockGuideHelper && ConstellationClient.loc().onHypixel(); }
    private static boolean playerSlot(Slot slot) { Minecraft mc = Minecraft.getInstance(); return mc.player != null && slot.container == mc.player.getInventory(); }
    private static boolean once(List<String> lines) { return has(lines, "This task can only be completed once!"); }
    private static boolean has(List<String> lines, String value) { return lines.stream().anyMatch(value::equals); }
    private static boolean any(List<String> lines, Pattern pattern) { return lines.stream().anyMatch(line -> pattern.matcher(line).matches()); }
    private static List<String> lore(ItemStack stack) { ItemLore lore = stack.get(DataComponents.LORE); return lore == null ? List.of() : lore.lines().stream().map(line -> clean(line.getString()).strip()).toList(); }
    private static String clean(String value) { String clean = ChatFormatting.stripFormatting(value); return clean == null ? value : clean; }
    private static String id(String name) { return name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", ""); }
    private static Boolean parse(String raw) { return switch (raw.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "§aon" : "§coff"; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Auriga §8> §f" + text)); }
    private record Rule(String key, Pattern title, Predicate<List<String>> condition) {}
    private record Match(boolean powerStone, String item) {}
}
