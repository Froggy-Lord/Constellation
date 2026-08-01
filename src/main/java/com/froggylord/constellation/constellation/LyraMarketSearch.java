package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.api.BazaarApi;
import com.froggylord.constellation.ui.LyraMarketSearchScreen;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.net.URI;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/searchoverlay/SearchOverManager.java
// item catalogue loader ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/SkyblockItemData.java
public final class LyraMarketSearch {
    public enum Location { NONE, AUCTION, BAZAAR, MUSEUM }
    public record Suggestion(String name, String id, ItemStack icon) {}
    private static final URI ITEMS = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");
    private static final URI AUCTIONS = URI.create("https://hysky.de/api/auctions/lowestbins/average?days=3");
    private static final URI BAZAAR = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
    private static final URI BAZAAR_STOCKS = URI.create("https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/bazaarstocks.json");
    private static final URI ESSENCE_COSTS = URI.create("https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/essencecosts.json");
    private static final URI MUSEUM = URI.create("https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/museum.json");
    private static final Map<String, CatalogItem> CATALOG = new LinkedHashMap<>();
    private static final Map<String, ItemStack> ICONS = new LinkedHashMap<>();
    private static final Map<String, String> SHARD_NAMES = new LinkedHashMap<>();
    private static volatile Set<String> bazaarItems = Set.of();
    private static volatile Set<String> auctionItems = Set.of();
    private static volatile Set<String> museumItems = Set.of();
    private static volatile Set<String> auctionPets = Set.of();
    private static volatile Set<String> level200Pets = Set.of();
    private static volatile Set<String> starableItems = Set.of();
    private static LyraConfig cfg;
    private static SignBlockEntity sign;
    private static boolean signFront;
    private static boolean command;
    private static Location location = Location.NONE;
    private static String search = "";
    private static boolean maxPet;
    private static int stars;
    private static volatile boolean loading;
    private static volatile long loadedAt;
    private static volatile long revision;
    private static volatile String loadError = "";
    private static long transactionId;

    private LyraMarketSearch() {}

    public static void init(LyraConfig config) {
        cfg = config;
        normalize();
        loadBundledNames();
        loadCatalogue();
        BazaarApi.ensureFresh();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !scope()) return;
            observe(container);
            ScreenEvents.afterTick(container).register(ignored -> observe(container));
        });
    }

    public static boolean intercept(SignBlockEntity blockEntity, boolean front) {
        Minecraft mc = Minecraft.getInstance();
        if (!scope() || mc.gui.screen() == null || blockEntity == null) return false;
        Component[] messages = blockEntity.getText(front).getMessages(mc.isTextFilteringEnabled());
        if (messages.length < 4 || !messages[3].getString().equalsIgnoreCase("enter query")) return false;
        String title = plain(mc.gui.screen().getTitle()).toLowerCase(Locale.ROOT);
        Location found = title.contains("auction") && cfg.marketSearchAuction ? Location.AUCTION
            : title.contains("bazaar") && cfg.marketSearchBazaar ? Location.BAZAAR
            : title.contains("museum") && cfg.marketSearchMuseum ? Location.MUSEUM : Location.NONE;
        if (found == Location.NONE) return false;
        sign = blockEntity;
        signFront = front;
        command = false;
        location = found;
        stars = Math.clamp(cfg.marketSearchDefaultStars, 0, 10);
        maxPet = false;
        search = cfg.marketSearchKeepPrevious ? join(messages[0].getString(), messages[1].getString()) : "";
        transactionId++;
        loadCatalogue();
        mc.gui.setScreen(new LyraMarketSearchScreen());
        return true;
    }

    public static void startCommand(Location wanted, String initial) {
        if (!scope() || !cfg.marketSearchCommands || wanted == Location.MUSEUM || wanted == Location.NONE
            || wanted == Location.AUCTION && !cfg.marketSearchAuction || wanted == Location.BAZAAR && !cfg.marketSearchBazaar) return;
        sign = null;
        command = true;
        location = wanted;
        stars = Math.clamp(cfg.marketSearchDefaultStars, 0, 10);
        maxPet = false;
        search = initial == null ? "" : initial.strip();
        transactionId++;
        loadCatalogue();
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new LyraMarketSearchScreen()));
    }

    public static boolean submit(long expectedTransaction) {
        if (expectedTransaction != transactionId || !canSubmit()) return false;
        String value = decoratedSearch();
        if (!search.isBlank()) saveHistory(search);
        if (command) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !value.isBlank()) mc.player.connection.sendCommand((location == Location.AUCTION ? "ahSearch " : "bz ") + value);
            clearTransaction();
            return true;
        }
        if (sign == null || Minecraft.getInstance().player == null) return false;
        String[] split = split(value);
        Component[] messages = sign.getText(signFront).getMessages(Minecraft.getInstance().isTextFilteringEnabled());
        Minecraft.getInstance().player.connection.send(new ServerboundSignUpdatePacket(sign.getBlockPos(), signFront,
            split[0], split[1], messages[2].getString(), messages[3].getString()));
        clearTransaction();
        return true;
    }

    public static void cancel(long expectedTransaction) {
        if (expectedTransaction != transactionId) return;
        if (command || sign == null || Minecraft.getInstance().player == null) { clearTransaction(); return; }
        Component[] messages = sign.getText(signFront).getMessages(Minecraft.getInstance().isTextFilteringEnabled());
        Minecraft.getInstance().player.connection.send(new ServerboundSignUpdatePacket(sign.getBlockPos(), signFront,
            messages[0].getString(), messages[1].getString(), messages[2].getString(), messages[3].getString()));
        clearTransaction();
    }

    public static List<Suggestion> suggestions() {
        if (cfg == null || !cfg.marketSearchSuggestions || search.isBlank()) return List.of();
        String needle = search.toLowerCase(Locale.ROOT);
        int limit = Math.clamp(cfg.marketSearchMaxSuggestions, 0, 12);
        Set<String> allowed = switch (location) { case AUCTION -> auctionItems; case BAZAAR -> bazaarItems; case MUSEUM -> museumItems; default -> Set.of(); };
        if (allowed.isEmpty()) return List.of();
        List<CatalogItem> values;
        synchronized (CATALOG) { values = new ArrayList<>(CATALOG.values()); }
        return values.stream().filter(item -> allowed.contains(item.name.toLowerCase(Locale.ROOT)))
            .filter(item -> item.name.toLowerCase(Locale.ROOT).contains(needle))
            .sorted(Comparator.comparingInt((CatalogItem item) -> rank(item.name, needle)).thenComparing(item -> item.name))
            .limit(limit).map(item -> new Suggestion(item.name, item.id, icon(item))).toList();
    }

    public static List<String> history() { normalize(); return List.copyOf(historyList()).subList(0, Math.min(cfg.marketSearchHistoryLength, historyList().size())); }
    public static void removeHistory(int index) { List<String> list = historyList(); if (index >= 0 && index < list.size()) { list.remove(index); ConstellationClient.saveConfig(); } }
    public static void select(String value) { search = value == null ? "" : value; }
    public static String search() { return search; }
    public static Location location() { return location; }
    public static boolean maxPet() { return maxPet; }
    public static void toggleMaxPet() { maxPet = !maxPet; }
    public static int stars() { return stars; }
    public static void cycleStars(int direction) { stars = Math.floorMod(stars + Integer.signum(direction), 11); }
    public static int catalogueSize() { synchronized (CATALOG) { return CATALOG.size(); } }
    public static boolean loading() { return loading; }
    public static String loadError() { return loadError; }
    public static long revision() { return revision; }
    public static long transactionId() { return transactionId; }
    public static void retry() { loadedAt = 0; loadError = ""; loadCatalogue(); }
    public static boolean canSubmit() { return !decoratedSearch().isBlank() && decoratedSearch().length() <= 30; }
    public static String validationMessage() { int length = decoratedSearch().length(); return length > 30 ? "Query is " + length + "/30 characters. Shorten it or remove a filter." : ""; }
    public static LyraConfig config() { return cfg; }

    private static void observe(AbstractContainerScreen<?> screen) {
        Location found = locationFromTitle(plain(screen.getTitle()));
        for (var slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = plain(stack.getHoverName()).strip();
            String id = skyblockId(stack);
            if (name.isBlank() || id.isBlank()) continue;
            synchronized (CATALOG) { CATALOG.putIfAbsent(id, new CatalogItem(name, id, found)); }
            ICONS.put(id, stack.copy());
            if (found != Location.NONE) addObserved(found, name);
        }
    }

    private static Location locationFromTitle(String title) { String value = title.toLowerCase(Locale.ROOT); return value.contains("auction") ? Location.AUCTION : value.contains("bazaar") ? Location.BAZAAR : value.contains("museum") ? Location.MUSEUM : Location.NONE; }

    private static void loadCatalogue() {
        if (loading || System.currentTimeMillis() - loadedAt < 3_600_000L) return;
        loading = true;
        Thread thread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
                HttpRequest request = HttpRequest.newBuilder(ITEMS).timeout(Duration.ofSeconds(15)).header("User-Agent", "Constellation/0.9").GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode() + " from " + ITEMS.getHost());
                JsonArray items = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("items");
                Map<String, CatalogItem> fresh = new LinkedHashMap<>();
                for (var element : items) {
                    JsonObject item = element.getAsJsonObject();
                    String id = text(item, "id"), name = text(item, "name");
                    String clean = ChatFormatting.stripFormatting(name);
                    if (!id.isBlank() && clean != null && !clean.isBlank()) fresh.put(id, new CatalogItem(clean, id, Location.NONE));
                }
                if (!fresh.isEmpty()) synchronized (CATALOG) { CATALOG.clear(); CATALOG.putAll(fresh); }
                loadMarketSets(client, fresh);
                loadedAt = System.currentTimeMillis();
                loadError = "";
                revision++;
            } catch (Exception exception) { loadError = "Catalogue unavailable"; loadedAt = System.currentTimeMillis() - 3_570_000L; ConstellationClient.LOGGER.warn("Failed to load SkyBlock item search catalogue", exception); }
            finally { loading = false; }
        }, "constellation-item-catalogue");
        thread.setDaemon(true);
        thread.start();
    }

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/searchoverlay/SearchOverManager.java loadItems
    private static void loadMarketSets(HttpClient client, Map<String, CatalogItem> catalog) throws Exception {
        Map<String, String> names = new LinkedHashMap<>();
        catalog.forEach((id, item) -> names.put(id, item.name));

        Set<String> bazaar = new HashSet<>();
        JsonArray stocks = fetch(client, BAZAAR_STOCKS).getAsJsonArray();
        Map<String, String> stockIds = new LinkedHashMap<>();
        for (var element : stocks) {
            JsonObject stock = element.getAsJsonObject();
            stockIds.put(text(stock, "stock"), text(stock, "id"));
        }
        Set<String> liveProducts = new HashSet<>();
        JsonObject liveBazaar = fetch(client, BAZAAR).getAsJsonObject().getAsJsonObject("products");
        for (String id : liveBazaar.keySet()) {
            JsonObject quick = liveBazaar.getAsJsonObject(id).getAsJsonObject("quick_status");
            if (quick != null && quick.has("sellVolume") && quick.get("sellVolume").getAsLong() > 0) liveProducts.add(id);
        }
        for (String product : liveProducts) {
            String id = stockIds.getOrDefault(product, product);
            String name = names.get(id);
            if (name == null && product.startsWith("ENCHANTMENT_")) name = enchantmentName(product);
            if (name == null && product.startsWith("SHARD_")) name = SHARD_NAMES.get(product);
            if (name == null) name = names.get(product);
            if (name == null) name = friendly(id);
            if (!name.isBlank()) { bazaar.add(name.toLowerCase(Locale.ROOT)); ensureCatalog(catalog, id, name); }
        }

        Set<String> auctions = new HashSet<>(), pets = new HashSet<>(), level200 = new HashSet<>(), auctionIds = new HashSet<>();
        JsonObject bins = fetch(client, AUCTIONS).getAsJsonObject();
        for (String apiId : bins.keySet()) {
            String id = apiId, name;
            if (apiId.startsWith("LVL_")) {
                String[] petParts = apiId.split("_", 4);
                if (petParts.length != 4) continue;
                name = friendly(petParts[3]);
                String lower = name.toLowerCase(Locale.ROOT);
                pets.add(lower);
                if (petParts[1].equals("200")) level200.add(lower);
                ensureCatalog(catalog, apiId, name);
            } else {
                if (apiId.contains("_POTION_")) {
                    String[] potion = apiId.split("_POTION_", 2);
                    id = "POTION_" + potion[0] + ";" + potion[1].split("_", 2)[0];
                }
                String[] parts = id.split("[+-]", 2);
                id = parts[0];
                name = names.get(id);
                if (name == null) name = friendly(apiId);
                ensureCatalog(catalog, id, name);
            }
            if (name.isBlank()) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            auctions.add(lower);
            auctionIds.add(id);
        }

        Set<String> starable = new HashSet<>();
        for (String id : fetch(client, ESSENCE_COSTS).getAsJsonObject().keySet()) {
            String name = names.get(id);
            if (name != null && auctionIds.contains(id)) starable.add(name.toLowerCase(Locale.ROOT));
        }

        Set<String> museum = new HashSet<>();
        JsonObject museumData = fetch(client, MUSEUM).getAsJsonObject();
        JsonObject categories = museumData.getAsJsonObject("items");
        JsonObject armor = museumData.getAsJsonObject("armor_to_id");
        for (String category : categories.keySet()) {
            if (category.equals("special")) continue;
            for (var element : categories.getAsJsonArray(category)) {
            String id = element.getAsString(), name = names.get(id);
            if (armor.has(id)) continue;
            if (name == null) name = friendly(id);
            museum.add(name.toLowerCase(Locale.ROOT));
            ensureCatalog(catalog, id, name);
            }
        }
        JsonObject components = museumData.getAsJsonObject("sets_to_items");
        JsonObject exceptions = museumData.getAsJsonObject("set_exceptions");
        for (String id : armor.keySet()) {
            String realId = id;
            for (String candidate : exceptions.keySet()) if (exceptions.get(candidate).getAsString().equals(id)) { realId = candidate; break; }
            boolean equipment = true;
            JsonArray pieces = components.getAsJsonArray(id);
            if (pieces != null) for (var piece : pieces) if (!isEquipment(piece.getAsString())) { equipment = false; break; }
            String name = formatMuseumSet(realId, equipment);
            museum.add(name.toLowerCase(Locale.ROOT));
            ensureCatalog(catalog, "MUSEUM_SET_" + id, name);
        }

        synchronized (CATALOG) { CATALOG.putAll(catalog); }
        bazaarItems = Set.copyOf(bazaar);
        auctionItems = Set.copyOf(auctions);
        museumItems = Set.copyOf(museum);
        auctionPets = Set.copyOf(pets);
        level200Pets = Set.copyOf(level200);
        starableItems = Set.copyOf(starable);
    }

    private static JsonElement fetch(HttpClient client, URI uri) throws Exception {
        Exception failure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent", "Constellation/0.9").GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode() + " from " + uri.getHost());
                return JsonParser.parseString(response.body());
            } catch (Exception exception) { failure = exception; }
        }
        throw failure == null ? new IllegalStateException("No response from " + uri.getHost()) : failure;
    }

    private static void ensureCatalog(Map<String, CatalogItem> catalog, String id, String name) {
        if (name != null && !name.isBlank()) catalog.putIfAbsent(id, new CatalogItem(name, id, Location.NONE));
    }

    private static String friendly(String id) {
        String value = id.replace(';', '_').replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(value.length());
        boolean upper = true;
        for (char c : value.toCharArray()) { result.append(upper ? Character.toUpperCase(c) : c); upper = c == ' '; }
        return result.toString().replaceAll(" \\d+$", "").strip();
    }

    private static String enchantmentName(String product) {
        String value = product.substring("ENCHANTMENT_".length());
        int split = value.lastIndexOf('_');
        if (split < 1) return friendly(value);
        int level;
        try { level = Integer.parseInt(value.substring(split + 1)); } catch (NumberFormatException ignored) { return friendly(value); }
        return friendly(value.substring(0, split)) + " " + roman(level);
    }

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/museum/MuseumUtils.java formatArmorName
    private static String formatMuseumSet(String id, boolean equipment) {
        String name = friendly(id);
        if (equipment) return name + " Equipment";
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.matches(".*(armor|outfit|suit|tuxedo).*") ? name : name + " Armor";
    }

    private static boolean isEquipment(String id) {
        String upper = id.toUpperCase(Locale.ROOT);
        return List.of("BELT", "GLOVES", "CLOAK", "GAUNTLET", "NECKLACE", "BRACELET", "HAT", "LOCKET", "VINE", "GRIPPERS").stream().anyMatch(upper::contains);
    }

    // derived from NotEnoughUpdates-REPO (MIT): constants/bazaarstocks.json and items/*.json
    private static void loadBundledNames() {
        if (!SHARD_NAMES.isEmpty()) return;
        try (var stream = LyraMarketSearch.class.getResourceAsStream("/assets/constellation/market-search-names.json")) {
            if (stream == null) return;
            JsonObject shards = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("shards");
            for (String id : shards.keySet()) SHARD_NAMES.put(id, shards.get(id).getAsString());
        } catch (Exception exception) { ConstellationClient.LOGGER.warn("Failed to load bundled market names", exception); }
    }

    private static String roman(int value) {
        if (value <= 0 || value > 20) return Integer.toString(value);
        String[] tens = {"", "X", "XX"}, ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[value / 10] + ones[value % 10];
    }

    private static void addObserved(Location at, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        switch (at) {
            case AUCTION -> { Set<String> next = new HashSet<>(auctionItems); if (next.add(lower)) { auctionItems = Set.copyOf(next); revision++; } }
            case BAZAAR -> { Set<String> next = new HashSet<>(bazaarItems); if (next.add(lower)) { bazaarItems = Set.copyOf(next); revision++; } }
            case MUSEUM -> { Set<String> next = new HashSet<>(museumItems); if (next.add(lower)) { museumItems = Set.copyOf(next); revision++; } }
            default -> { }
        }
    }

    private static void clearTransaction() { sign = null; signFront = false; command = false; location = Location.NONE; maxPet = false; stars = 0; }

    private static ItemStack icon(CatalogItem item) { ItemStack stack = ICONS.get(item.id); return stack == null ? ItemStack.EMPTY : stack.copy(); }
    private static int rank(String name, String needle) { String value = name.toLowerCase(Locale.ROOT); return value.equals(needle) ? 0 : value.startsWith(needle) ? 1 : wordStarts(value, needle) ? 2 : 3; }
    private static boolean wordStarts(String value, String needle) { for (String word : value.split(" ")) if (word.startsWith(needle)) return true; return false; }
    private static String decoratedSearch() {
        String value = search.strip();
        String lower = value.toLowerCase(Locale.ROOT);
        if (location == Location.AUCTION && auctionPets.contains(lower) && !value.isBlank()) {
            if (maxPet) value = "[Lvl " + Math.min(Math.clamp(cfg.marketSearchPetLevel, 1, 200), level200Pets.contains(lower) ? 200 : 100) + "] " + value;
            else value = "] " + value;
        }
        if (location == Location.AUCTION && stars > 0 && starableItems.contains(lower) && !value.isBlank()) {
            value += " " + "\u272A".repeat(Math.min(5, stars));
            if (stars > 5) value += switch (stars) { case 6 -> "\u278A"; case 7 -> "\u278B"; case 8 -> "\u278C"; case 9 -> "\u278D"; default -> "\u278E"; };
        }
        if (!command && value.toLowerCase(Locale.ROOT).startsWith("null")) value = '"' + value + '"';
        return value;
    }
    private static String[] split(String value) { if (value.length() <= 15) return new String[]{value, ""}; int at = value.lastIndexOf(' ', 15); if (at <= 0 || value.length() - at - 1 > 15) at = 15; int next = at < value.length() && value.charAt(at) == ' ' ? at + 1 : at; return new String[]{value.substring(0, at), value.substring(next)}; }
    private static String join(String first, String second) { return first.isBlank() ? second : second.isBlank() ? first : first + " " + second; }
    private static String text(JsonObject object, String key) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : ""; }
    private static String skyblockId(ItemStack stack) { CustomData data = stack.get(DataComponents.CUSTOM_DATA); return data == null ? "" : data.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("id", ""); }
    private static String plain(Component component) { String value = ChatFormatting.stripFormatting(component.getString()); return value == null ? component.getString() : value; }
    private static boolean scope() { Minecraft mc = Minecraft.getInstance(); return cfg != null && cfg.enabled && cfg.marketSearchOverlay && (ConstellationClient.loc().onHypixel() || cfg.marketSearchLocalWorlds && mc.hasSingleplayerServer()); }
    private static void saveHistory(String value) { normalize(); if (!cfg.marketSearchHistory) return; List<String> list = historyList(); list.removeIf(entry -> entry.equalsIgnoreCase(value)); list.addFirst(value); while (list.size() > cfg.marketSearchHistoryLength) list.removeLast(); ConstellationClient.saveConfig(); }
    private static List<String> historyList() { return switch (location) { case AUCTION -> cfg.marketSearchAuctionHistory; case BAZAAR -> cfg.marketSearchBazaarHistory; case MUSEUM -> cfg.marketSearchMuseumHistory; default -> new ArrayList<>(); }; }
    private static void normalize() { if (cfg.marketSearchBazaarHistory == null) cfg.marketSearchBazaarHistory = new ArrayList<>(); if (cfg.marketSearchAuctionHistory == null) cfg.marketSearchAuctionHistory = new ArrayList<>(); if (cfg.marketSearchMuseumHistory == null) cfg.marketSearchMuseumHistory = new ArrayList<>(); cfg.marketSearchMaxSuggestions = Math.clamp(cfg.marketSearchMaxSuggestions, 0, 12); cfg.marketSearchHistoryLength = Math.clamp(cfg.marketSearchHistoryLength, 0, 12); cfg.marketSearchPetLevel = Math.clamp(cfg.marketSearchPetLevel, 1, 200); cfg.marketSearchDefaultStars = Math.clamp(cfg.marketSearchDefaultStars, 0, 10); }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ahs")
            .executes(context -> open(Location.AUCTION, ""))
            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", StringArgumentType.greedyString()).executes(context -> open(Location.AUCTION, StringArgumentType.getString(context, "item")))));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bzs")
            .executes(context -> open(Location.BAZAAR, ""))
            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", StringArgumentType.greedyString()).executes(context -> open(Location.BAZAAR, StringArgumentType.getString(context, "item")))));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("marketsearch")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("suggestions").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("count", IntegerArgumentType.integer(0, 12)).executes(context -> setSuggestions(IntegerArgumentType.getInteger(context, "count")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("history").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("count", IntegerArgumentType.integer(0, 12)).executes(context -> setHistory(IntegerArgumentType.getInteger(context, "count")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("petlevel").then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("level", IntegerArgumentType.integer(1, 200)).executes(context -> setPetLevel(IntegerArgumentType.getInteger(context, "level")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word()).executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }
    private static int open(Location at, String value) { if (!scope() || !cfg.marketSearchCommands || at == Location.AUCTION && !cfg.marketSearchAuction || at == Location.BAZAAR && !cfg.marketSearchBazaar) { local("This search mode is disabled or unavailable here."); return 0; } startCommand(at, value); return 1; }
    private static int status() { local("Market search " + on(cfg.marketSearchOverlay) + ", catalogue " + catalogueSize() + (loading ? " loading" : " loaded") + ", suggestions " + cfg.marketSearchMaxSuggestions + ", history " + cfg.marketSearchHistoryLength + "."); return 1; }
    private static int setSuggestions(int value) { cfg.marketSearchMaxSuggestions = value; ConstellationClient.saveConfig(); local("Suggestion count updated."); return 1; }
    private static int setHistory(int value) { cfg.marketSearchHistoryLength = value; normalize(); ConstellationClient.saveConfig(); local("History length updated."); return 1; }
    private static int setPetLevel(int value) { cfg.marketSearchPetLevel = value; ConstellationClient.saveConfig(); local("Maximum pet level updated."); return 1; }
    private static int option(String name, String state) { Boolean value = parse(state); if (value == null) { local("State must be on or off."); return 0; } switch (name.toLowerCase(Locale.ROOT)) { case "enabled" -> cfg.marketSearchOverlay = value; case "bazaar" -> cfg.marketSearchBazaar = value; case "auction" -> cfg.marketSearchAuction = value; case "museum" -> cfg.marketSearchMuseum = value; case "commands" -> cfg.marketSearchCommands = value; case "previous" -> cfg.marketSearchKeepPrevious = value; case "suggestions" -> cfg.marketSearchSuggestions = value; case "history" -> cfg.marketSearchHistory = value; case "icons" -> cfg.marketSearchItemIcons = value; case "maxpet" -> cfg.marketSearchMaxPet = value; case "stars" -> cfg.marketSearchDungeonStars = value; case "local" -> cfg.marketSearchLocalWorlds = value; default -> { local("Unknown market-search option."); return 0; } } ConstellationClient.saveConfig(); local("Market-search option updated."); return 1; }
    private static Boolean parse(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void local(String value) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f" + value)); }
    private record CatalogItem(String name, String id, Location observed) {}
}
