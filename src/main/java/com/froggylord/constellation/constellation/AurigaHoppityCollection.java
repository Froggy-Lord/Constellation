package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityCollectionStats.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/inventory/chocolatefactory/HoppityCollectionStatsConfig.kt
public final class AurigaHoppityCollection {
    public enum Rarity {
        COMMON("Common", 0xFFFFFFFF), UNCOMMON("Uncommon", 0xFF55FF55), RARE("Rare", 0xFF5555FF),
        EPIC("Epic", 0xFFAA00AA), LEGENDARY("Legendary", 0xFFFFAA00), MYTHIC("Mythic", 0xFFFF55FF),
        DIVINE("Divine", 0xFF55FFFF), UNKNOWN("Unknown", 0xFFAAAAAA);
        public final String label; public final int color;
        Rarity(String label, int color) { this.label = label; this.color = color; }
    }
    public record RarityState(int unique, int total, int duplicates) {}
    public record State(Map<Rarity, RarityState> rarities, int unique, int total, int duplicates,
                        int progress, int progressTotal, int pagesSeen, int maxPages, boolean complete) {}

    private static final Pattern TITLE = Pattern.compile("^(?:\\((\\d+)/(\\d+)\\) )?Hoppity's Collection$");
    private static final Pattern DUPLICATES = Pattern.compile("^Duplicates Found: ([\\d,]+)$");
    private static final Pattern PROGRESS = Pattern.compile("^\\s*(\\d+)/(\\d+)\\s*$");
    private static final Pattern REQUIREMENT_AMOUNT = Pattern.compile("^\\u2716 Requirement ([\\d,]+)/([\\d,]+)$");
    private static AurigaConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static String fingerprint = "";
    private static final Set<Integer> SEEN_PAGES = new HashSet<>();
    private static int maxPages;
    private static State state = empty();

    private AurigaHoppityCollection() {}

    public static void init(AurigaConfig config) {
        cfg = config;
        normalize();
        ScreenEvents.AFTER_INIT.register((client, opened, width, height) -> {
            if (!(opened instanceof AbstractContainerScreen<?> container) || !TITLE.matcher(clean(container.getTitle().getString())).matches()) return;
            screen = container;
            update(container);
            ScreenEvents.afterTick(opened).register(ignored -> update(container));
            ScreenEvents.remove(opened).register(ignored -> {
                if (screen == container) { screen = null; fingerprint = ""; SEEN_PAGES.clear(); maxPages = 0; restore(); }
            });
        });
        restore();
    }

    private static void update(AbstractContainerScreen<?> container) {
        if (!active() || container != screen) return;
        String title = clean(container.getTitle().getString());
        Matcher page = TITLE.matcher(title);
        if (!page.matches()) return;
        int currentPage = page.group(1) == null ? 1 : integer(page.group(1));
        int pages = page.group(2) == null ? 1 : integer(page.group(2));
        String nextFingerprint = title + "|" + container.getMenu().slots.stream()
            .map(slot -> clean(slot.getItem().getHoverName().getString()) + lore(slot.getItem())).toList();
        if (nextFingerprint.equals(fingerprint)) return;
        fingerprint = nextFingerprint;
        SEEN_PAGES.add(currentPage);
        maxPages = Math.max(maxPages, pages);
        boolean changed = false;
        int progress = -1, progressTotal = -1;
        String profile = profile();
        for (Slot slot : container.getMenu().slots) {
            if (slot.getItem().isEmpty() || playerSlot(slot)) continue;
            List<String> lore = lore(slot.getItem());
            for (String line : lore) {
                Matcher found = PROGRESS.matcher(line);
                if (found.matches()) { progress = integer(found.group(1)); progressTotal = integer(found.group(2)); }
            }
            String name = clean(slot.getItem().getHoverName().getString());
            boolean notFound = lore.stream().anyMatch(line -> line.contains("have not found this rabbit yet") || line.contains("cannot find this rabbit until you"));
            Matcher duplicate = first(lore, DUPLICATES);
            if (!notFound && duplicate == null) continue;
            int count = notFound ? 0 : integer(duplicate.group(1)) + 1;
            String key = profile + "|" + name;
            Rarity rarity = rarity(slot.getItem());
            if (!Integer.valueOf(count).equals(cfg.hoppityRabbitCounts.put(key, count))) changed = true;
            if (!rarity.name().equals(cfg.hoppityRabbitRarities.put(key, rarity.name()))) changed = true;
        }
        if (progress >= 0) {
            if (!Integer.valueOf(progress).equals(cfg.hoppityCollectionProgress.put(profile + "|current", progress))) changed = true;
            if (!Integer.valueOf(progressTotal).equals(cfg.hoppityCollectionProgress.put(profile + "|total", progressTotal))) changed = true;
        }
        String pageState = encodePages();
        if (!pageState.equals(cfg.hoppityCollectionPages.put(profile, pageState))) changed = true;
        rebuild();
        if (changed && cfg.hoppityCollectionPersistProfiles) ConstellationClient.saveConfig();
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> container, Slot slot) {
        if (!active() || container != screen || slot == null || slot.getItem().isEmpty() || playerSlot(slot)) return;
        List<String> lines = lore(slot.getItem());
        boolean missing = lines.stream().anyMatch(line -> line.contains("have not found this rabbit yet") || line.contains("cannot find this rabbit until you"));
        boolean found = first(lines, DUPLICATES) != null;
        if (!missing && !found) return;
        int color = 0;
        if (missing && cfg.hoppityCollectionHighlightMissing)
            color = cfg.hoppityCollectionRarityMissingHighlight ? rarity(slot.getItem()).color & 0x80FFFFFF : cfg.hoppityCollectionMissingColor;
        if (found && cfg.hoppityCollectionHighlightFound) color = cfg.hoppityCollectionFoundColor;
        if (missing && cfg.hoppityCollectionHighlightFactory && lines.stream().anyMatch(line -> line.contains("Factory Milestones"))) color = cfg.hoppityCollectionFactoryColor;
        if (missing && cfg.hoppityCollectionHighlightShop && lines.stream().anyMatch(line -> line.contains("Shop Milestones"))) color = cfg.hoppityCollectionShopColor;
        if (missing && cfg.hoppityCollectionHighlightRequirements && lines.stream().anyMatch(line ->
            line.startsWith("\u2714 Requirement") || line.startsWith("\u2716 Requirement") || REQUIREMENT_AMOUNT.matcher(line).matches()))
            color = cfg.hoppityCollectionRequirementColor;
        if (missing && cfg.hoppityCollectionHighlightStrays && lines.stream().anyMatch(line -> line.contains("Golden Stray"))) color = cfg.hoppityCollectionStrayColor;
        if (color != 0) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> container, ItemStack stack, List<Component> original) {
        if (!active() || !cfg.hoppityCollectionTooltip || container != screen || original == null || stack == null || stack.isEmpty()) return original;
        List<String> lines = lore(stack);
        boolean missing = lines.stream().anyMatch(line -> line.contains("have not found this rabbit yet") || line.contains("cannot find this rabbit until you"));
        Matcher duplicate = first(lines, DUPLICATES);
        if (!missing && duplicate == null) return original;
        ArrayList<Component> out = new ArrayList<>(original);
        Rarity rarity = rarity(stack);
        out.add(Component.literal("Collection: " + (missing ? "§cmissing" : "§afound") + " §7(" + rarity.label + ")"));
        if (duplicate != null) out.add(Component.literal("Collection Count: §a" + (integer(duplicate.group(1)) + 1)));
        if (state.maxPages > 0 && !state.complete) out.add(Component.literal("§cScan all " + state.maxPages + " pages for complete statistics."));
        return out;
    }

    private static void rebuild() {
        String profile = profile();
        EnumMap<Rarity, Integer> unique = new EnumMap<>(Rarity.class), totals = new EnumMap<>(Rarity.class), dupes = new EnumMap<>(Rarity.class);
        int allUnique = 0, allTotal = 0, allDupes = 0;
        for (var entry : cfg.hoppityRabbitRarities.entrySet()) {
            if (!entry.getKey().startsWith(profile + "|")) continue;
            Rarity rarity = parseRarity(entry.getValue());
            int count = cfg.hoppityRabbitCounts.getOrDefault(entry.getKey(), 0);
            totals.merge(rarity, 1, Integer::sum);
            if (count > 0) { unique.merge(rarity, 1, Integer::sum); dupes.merge(rarity, count - 1, Integer::sum); allUnique++; allDupes += count - 1; }
            allTotal++;
        }
        EnumMap<Rarity, RarityState> rows = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) if (totals.getOrDefault(rarity, 0) > 0)
            rows.put(rarity, new RarityState(unique.getOrDefault(rarity, 0), totals.getOrDefault(rarity, 0), dupes.getOrDefault(rarity, 0)));
        int progress = cfg.hoppityCollectionProgress.getOrDefault(profile + "|current", allUnique);
        int progressTotal = cfg.hoppityCollectionProgress.getOrDefault(profile + "|total", allTotal);
        decodePages(cfg.hoppityCollectionPages.getOrDefault(profile, ""));
        state = new State(Map.copyOf(rows), allUnique, allTotal, allDupes, progress, progressTotal,
            SEEN_PAGES.size(), maxPages, maxPages > 0 && SEEN_PAGES.size() >= maxPages);
    }

    public static State state() { return state; }
    public static AurigaConfig config() { return cfg; }
    public static boolean visible() { return active() && screen != null; }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hoppitycollection")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(context -> clearProfile()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("Collection " + on(cfg.hoppityCollectionStats) + ", " + state.unique + "/" + state.progressTotal
            + " unique, " + state.duplicates + " duplicates, pages " + state.pagesSeen + "/" + state.maxPages
            + (state.complete ? " complete." : " partial."));
        return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = bool(raw); if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.hoppityCollectionStats=value; case "hud" -> cfg.hoppityCollectionHud=value;
            case "rarities" -> cfg.hoppityCollectionShowRarities=value; case "duplicates" -> cfg.hoppityCollectionShowDuplicates=value;
            case "total" -> cfg.hoppityCollectionShowTotal=value; case "pages" -> cfg.hoppityCollectionShowPages=value;
            case "progress" -> cfg.hoppityCollectionShowProgress=value; case "tooltip" -> cfg.hoppityCollectionTooltip=value;
            case "missing" -> cfg.hoppityCollectionHighlightMissing=value; case "found" -> cfg.hoppityCollectionHighlightFound=value;
            case "rarity" -> cfg.hoppityCollectionRarityMissingHighlight=value; case "factory" -> cfg.hoppityCollectionHighlightFactory=value;
            case "shop" -> cfg.hoppityCollectionHighlightShop=value; case "requirements" -> cfg.hoppityCollectionHighlightRequirements=value;
            case "strays" -> cfg.hoppityCollectionHighlightStrays=value; case "persist" -> cfg.hoppityCollectionPersistProfiles=value;
            default -> { local("Unknown Hoppity Collection option."); return 0; }
        }
        ConstellationClient.saveConfig(); return status();
    }

    private static int clearProfile() {
        String prefix = profile() + "|";
        cfg.hoppityRabbitCounts.keySet().removeIf(key -> key.startsWith(prefix));
        cfg.hoppityRabbitRarities.keySet().removeIf(key -> key.startsWith(prefix));
        cfg.hoppityCollectionProgress.keySet().removeIf(key -> key.startsWith(prefix));
        cfg.hoppityCollectionPages.remove(profile()); SEEN_PAGES.clear(); maxPages=0; state=empty();
        ConstellationClient.saveConfig(); local("Current profile collection cache cleared."); return 1;
    }

    private static void restore() { if (cfg != null) { normalize(); rebuild(); } }
    private static String encodePages() { return maxPages + ":" + SEEN_PAGES.stream().sorted().map(String::valueOf).reduce((a,b)->a+","+b).orElse(""); }
    private static void decodePages(String raw) {
        SEEN_PAGES.clear(); maxPages=0; if (raw.isBlank()) return;
        String[] parts=raw.split(":",2); maxPages=integer(parts[0]); if(parts.length<2)return;
        for(String value:parts[1].split(","))if(!value.isBlank())SEEN_PAGES.add(integer(value));
    }
    private static Rarity rarity(ItemStack stack) {
        TextColor color = stack.getHoverName().getStyle().getColor();
        if (color == null) return Rarity.UNKNOWN;
        int value = color.getValue() & 0xFFFFFF;
        return switch (value) { case 0xFFFFFF -> Rarity.COMMON; case 0x55FF55 -> Rarity.UNCOMMON; case 0x5555FF -> Rarity.RARE;
            case 0xAA00AA -> Rarity.EPIC; case 0xFFAA00 -> Rarity.LEGENDARY; case 0xFF55FF -> Rarity.MYTHIC; case 0x55FFFF -> Rarity.DIVINE; default -> Rarity.UNKNOWN; };
    }
    private static Rarity parseRarity(String value) { try { return Rarity.valueOf(value); } catch (Exception ignored) { return Rarity.UNKNOWN; } }
    private static Matcher first(List<String> lines, Pattern pattern) { for(String line:lines){Matcher matcher=pattern.matcher(line);if(matcher.matches())return matcher;}return null; }
    private static List<String> lore(ItemStack stack) { ItemLore lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(line->clean(line.getString())).toList(); }
    private static String clean(String value) { String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.strip(); }
    private static int integer(String value) { try{return Integer.parseInt(value.replace(",",""));}catch(Exception ignored){return 0;} }
    private static boolean playerSlot(Slot slot) { Minecraft mc=Minecraft.getInstance();return mc.player!=null&&slot.container==mc.player.getInventory(); }
    private static boolean active() { return cfg!=null&&cfg.enabled&&cfg.hoppityCollectionStats&&ConstellationClient.loc().onHypixel(); }
    private static String profile() { String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT); }
    private static Boolean bool(String value) { return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;}; }
    private static String on(boolean value){return value?"on":"off";}
    private static void normalize(){if(cfg.hoppityRabbitCounts==null)cfg.hoppityRabbitCounts=new LinkedHashMap<>();if(cfg.hoppityRabbitRarities==null)cfg.hoppityRabbitRarities=new LinkedHashMap<>();if(cfg.hoppityCollectionProgress==null)cfg.hoppityCollectionProgress=new LinkedHashMap<>();if(cfg.hoppityCollectionPages==null)cfg.hoppityCollectionPages=new LinkedHashMap<>();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a76[Hoppity Collection] \u00a7f"+text));}
    private static State empty(){return new State(Map.of(),0,0,0,0,0,0,0,false);}
}
