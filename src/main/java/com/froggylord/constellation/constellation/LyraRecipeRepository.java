package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import io.github.moulberry.repo.NEURecipeCache;
import io.github.moulberry.repo.NEURepository;
import io.github.moulberry.repo.data.NEUIngredient;
import io.github.moulberry.repo.data.NEUItem;
import io.github.moulberry.repo.data.NEURecipe;
import net.fabricmc.loader.api.FabricLoader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import io.github.moulberry.repo.data.NEUCraftingRecipe;
import io.github.moulberry.repo.data.NEUForgeRecipe;
import io.github.moulberry.repo.data.NEUKatUpgradeRecipe;
import io.github.moulberry.repo.data.NEUNpcShopRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.TagParser;
import com.froggylord.constellation.api.ProfileItemDecoder;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

// ported from Skyblocker (LGPL-3.0-or-later): utils/NEURepoManager.java, skyblock/itemlist/ItemRepository.java
public final class LyraRecipeRepository {
    private static final String REMOTE = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO.git";
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("constellation/item-repo");
    private static final AtomicReference<Snapshot> SNAPSHOT = new AtomicReference<>(Snapshot.loading());
    private static final AtomicLong REVISION = new AtomicLong();
    private static volatile CompletableFuture<Void> loading;
    private static final Map<String, ItemStack> STACK_CACHE = new ConcurrentHashMap<>();

    private LyraRecipeRepository() {}

    public static void init() {
        if (loading == null) reload(false);
    }

    public static synchronized void reload(boolean update) {
        if (loading != null && !loading.isDone()) return;
        Snapshot previous = SNAPSHOT.get();
        if (previous.state() == State.READY) publish(previous.withRefresh(true, ""));
        else publish(Snapshot.loading());
        loading = CompletableFuture.runAsync(() -> load(update), Executors.newVirtualThreadPerTaskExecutor());
    }

    private static void load(boolean update) {
        Snapshot previous = SNAPSHOT.get();
        Path staging = null;
        try {
            Files.createDirectories(DIRECTORY.getParent());
            if (!Files.isDirectory(DIRECTORY.resolve(".git"))) {
                if (Files.exists(DIRECTORY)) quarantine(DIRECTORY, "incomplete");
                staging = DIRECTORY.resolveSibling("item-repo.staging-" + UUID.randomUUID());
                Git.cloneRepository().setURI(REMOTE).setDirectory(staging.toFile())
                        .setBranchesToClone(List.of("refs/heads/master")).setBranch("refs/heads/master")
                        .setDepth(1).call().close();
                try { Files.move(staging, DIRECTORY, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(staging, DIRECTORY); }
                staging = null;
            } else if (update) {
                try (Git git = Git.open(DIRECTORY.toFile())) {
                    git.pull().setRebase(false).setFastForward(MergeCommand.FastForwardMode.FF_ONLY).call();
                }
            }

            NEURepository repository = NEURepository.of(DIRECTORY);
            NEURecipeCache cache = NEURecipeCache.forRepo(repository);
            repository.reload();
            List<Item> items = repository.getItems().getItems().values().stream()
                    .map(item -> new Item(item.getSkyblockItemId(), clean(item.getDisplayName()), item.getMinecraftItemId(), item.getDamage(),
                            item.getNbttag() == null ? "{}" : item.getNbttag(), List.copyOf(item.getLore())))
                    .filter(item -> !item.id().isBlank() && !item.name().isBlank())
                    .sorted(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Item::id))
                    .toList();
            Map<String, Item> index = items.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Item::id, item -> item, (first, ignored) -> first));
            Map<String, Set<NEURecipe>> recipes = supported(cache.getRecipes());
            Map<String, Set<NEURecipe>> usages = supported(cache.getUsages());
            STACK_CACHE.clear();
            publish(new Snapshot(State.READY, items, index, recipes, usages,
                    Map.copyOf(repository.getConstants().getParents().getParents()), false, ""));
            ConstellationClient.LOGGER.info("Recipe repository ready: {} items, {} outputs, {} usage keys", items.size(), recipes.size(), usages.size());
            if (FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
                try { Class.forName("com.froggylord.constellation.compat.rei.LyraReiReloadBridge").getMethod("repositoryReady").invoke(null); }
                catch (ReflectiveOperationException exception) { ConstellationClient.LOGGER.debug("REI reload bridge is not ready", exception); }
            }
        } catch (Exception exception) {
            if (staging != null && Files.exists(staging)) {
                try { quarantine(staging, "failed-clone"); } catch (Exception moveException) { exception.addSuppressed(moveException); }
            }
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            if (previous.state() == State.READY) publish(previous.withRefresh(false, message));
            else publish(new Snapshot(State.ERROR, List.of(), Map.of(), Map.of(), Map.of(), Map.of(), false, message));
            ConstellationClient.LOGGER.warn("Recipe repository could not be loaded: {}", message);
        }
    }

    private static Map<String, Set<NEURecipe>> supported(Map<String, Set<NEURecipe>> source) {
        return source.entrySet().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue().stream()
                        .filter(recipe -> recipe instanceof NEUCraftingRecipe || recipe instanceof NEUForgeRecipe
                                || recipe instanceof NEUNpcShopRecipe || recipe instanceof NEUKatUpgradeRecipe)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet())))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static void quarantine(Path path, String reason) throws java.io.IOException {
        Path base = Path.of(System.getProperty("user.home"), "Desktop", "To-Delete", "constellation-item-repo");
        Files.createDirectories(base);
        String name = reason + "-" + System.currentTimeMillis() + "-" + path.getFileName();
        Files.move(path, base.resolve(name));
    }

    private static void publish(Snapshot snapshot) { SNAPSHOT.set(snapshot); REVISION.incrementAndGet(); }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("§.", "").trim();
    }

    public static Snapshot snapshot() { return SNAPSHOT.get(); }
    public static long revision() { return REVISION.get(); }

    public static List<Item> search(String query, Filter filter, int limit) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return snapshot().items().stream().filter(item -> filter.test(item.name().toLowerCase(Locale.ROOT)))
                .filter(item -> needle.isBlank() || item.name().toLowerCase(Locale.ROOT).contains(needle)
                || item.id().toLowerCase(Locale.ROOT).contains(needle)
                || item.lore().stream().anyMatch(line -> clean(line).toLowerCase(Locale.ROOT).contains(needle)))
                .limit(Math.max(1, limit)).toList();
    }

    public static List<Item> search(String query, int limit) { return search(query, Filter.ALL, limit); }

    public static List<NEURecipe> recipes(String id) { return List.copyOf(snapshot().recipes().getOrDefault(id, Set.of())); }
    public static List<NEURecipe> usages(String id) { return List.copyOf(snapshot().usages().getOrDefault(id, Set.of())); }

    public static Item item(String id) { return snapshot().itemIndex().get(id); }

    public static ItemStack stack(String id, double amount) {
        if (id == null || id.isBlank() || id.equals(NEUIngredient.NEU_SENTINEL_EMPTY)) return ItemStack.EMPTY;
        if (id.equals(NEUIngredient.NEU_SENTINEL_COINS) || id.equals("SKYBLOCK_COIN")) {
            ItemStack coins = new ItemStack(Items.GOLD_NUGGET);
            coins.set(DataComponents.ITEM_NAME, Component.literal("SkyBlock Coins"));
            coins.setCount(1);
            return coins;
        }
        Item item = item(id);
        ItemStack decoded = STACK_CACHE.get(id);
        if (decoded == null && Minecraft.getInstance().player != null) {
            decoded = decode(item);
            if (!decoded.isEmpty()) {
                ensureSkyblockId(decoded, id);
                STACK_CACHE.put(id, decoded);
            }
        }
        if (decoded == null) decoded = ItemStack.EMPTY;
        if (!decoded.isEmpty() && !decoded.is(Items.BARRIER)) {
            ItemStack copy = decoded.copy();
            copy.setCount(Math.clamp((int) Math.round(amount), 1, 99));
            return copy;
        }
        String raw = item == null ? "minecraft:barrier" : modernId(item.minecraftId());
        Identifier vanillaId = Identifier.tryParse(raw);
        ItemStack stack = new ItemStack(vanillaId == null ? Items.BARRIER : BuiltInRegistries.ITEM.getOptional(vanillaId).orElse(Items.BARRIER));
        stack.set(DataComponents.ITEM_NAME, Component.literal(item == null ? id : item.name()));
        CompoundTag extra = new CompoundTag();
        extra.putString("id", id);
        CompoundTag root = new CompoundTag();
        root.put("ExtraAttributes", extra);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.setCount(Math.clamp((int) Math.round(amount), 1, 99));
        return stack;
    }

    private static void ensureSkyblockId(ItemStack stack, String id) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = data == null ? new CompoundTag() : data.copyTag();
        CompoundTag extra = root.getCompoundOrEmpty("ExtraAttributes").copy();
        extra.putString("id", id);
        root.put("ExtraAttributes", extra);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/itemlist/ItemStackBuilder.java, utils/datafixer/LegacyStringNbtReader.java
    private static ItemStack decode(Item item) {
        if (item == null) return ItemStack.EMPTY;
        try {
            CompoundTag legacy = new CompoundTag();
            legacy.put("tag", TagParser.parseCompoundFully(normalizeLegacyLists(item.nbt())));
            legacy.putString("id", item.minecraftId());
            legacy.putShort("Damage", (short) item.damage());
            legacy.putInt("Count", 1);
            ItemStack stack = ProfileItemDecoder.fixLegacyStack(legacy);
            if (!stack.isEmpty()) return stack;
        } catch (Exception exception) {
            ConstellationClient.LOGGER.debug("Recipe icon decode failed for {}", item.id(), exception);
        }
        return ItemStack.EMPTY;
    }

    private static String normalizeLegacyLists(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean quoted = false, escaped = false, afterSeparator = false;
        for (int index = 0; index < input.length();) {
            char c = input.charAt(index);
            if (quoted) {
                out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                index++;
                continue;
            }
            if (c == '"') { quoted = true; out.append(c); index++; continue; }
            if (c == '[' || c == ',') { afterSeparator = true; out.append(c); index++; continue; }
            if (afterSeparator && Character.isWhitespace(c)) { out.append(c); index++; continue; }
            if (afterSeparator && Character.isDigit(c)) {
                int end = index;
                while (end < input.length() && Character.isDigit(input.charAt(end))) end++;
                if (end < input.length() && input.charAt(end) == ':') { index = end + 1; afterSeparator = false; continue; }
            }
            afterSeparator = false;
            out.append(c);
            index++;
        }
        return out.toString();
    }

    private static String modernId(String raw) {
        if (raw == null || raw.isBlank()) return "minecraft:barrier";
        String id = raw.contains(":") ? raw : "minecraft:" + raw;
        return switch (id) {
            case "minecraft:skull" -> "minecraft:player_head";
            case "minecraft:golden_rail" -> "minecraft:powered_rail";
            case "minecraft:stained_hardened_clay" -> "minecraft:terracotta";
            case "minecraft:fireworks" -> "minecraft:firework_rocket";
            case "minecraft:firework_charge" -> "minecraft:firework_star";
            case "minecraft:wooden_door" -> "minecraft:oak_door";
            default -> id;
        };
    }

    public static RecipeView view(NEURecipe recipe) {
        String type;
        String note = "";
        if (recipe instanceof NEUCraftingRecipe crafting) {
            type = "Crafting";
            note = crafting.getExtraText() == null ? "" : crafting.getExtraText();
        } else if (recipe instanceof NEUForgeRecipe forge) {
            type = "Forge";
            note = duration(forge.getDuration());
        } else if (recipe instanceof NEUNpcShopRecipe shop) {
            type = "NPC Shop";
            note = shop.getIsSoldBy() == null ? "" : clean(shop.getIsSoldBy().getDisplayName());
        } else if (recipe instanceof NEUKatUpgradeRecipe kat) {
            type = "Kat Upgrade";
            note = duration(kat.getSeconds());
        } else {
            type = "Item Info";
        }
        List<Ingredient> inputs = grouped(recipe.getAllInputs().stream().filter(value -> value != NEUIngredient.SENTINEL_EMPTY).map(LyraRecipeRepository::ingredient).toList());
        List<Ingredient> outputs = grouped(recipe.getAllOutputs().stream().filter(value -> value != NEUIngredient.SENTINEL_EMPTY).map(LyraRecipeRepository::ingredient).toList());
        return new RecipeView(type, inputs, outputs, note);
    }

    private static List<Ingredient> grouped(List<Ingredient> values) {
        Map<String, Double> amounts = new java.util.LinkedHashMap<>();
        values.forEach(value -> amounts.merge(value.id(), value.amount(), Double::sum));
        return amounts.entrySet().stream().map(entry -> new Ingredient(entry.getKey(), entry.getValue())).toList();
    }

    private static String duration(long seconds) {
        long days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }

    public static Ingredient ingredient(NEUIngredient ingredient) {
        return new Ingredient(ingredient.getItemId(), ingredient.getAmount());
    }

    public enum State { LOADING, READY, ERROR }
    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/itemlist/recipebook/FilterOption.java
    public enum Filter {
        ALL, ITEMS, ENTITIES, NPCS, MAYORS;
        public boolean test(String name) {
            boolean entity = name.endsWith("(monster)") || name.endsWith("(miniboss)") || name.endsWith("(boss)")
                    || name.endsWith("(animal)") || name.endsWith("(pest)") || name.endsWith("(sea creature)");
            boolean npc = name.endsWith("(npc)") || name.endsWith("(rift npc)");
            boolean mayor = name.endsWith("(mayor)") || name.endsWith("(retired mayor)");
            return switch (this) { case ALL -> true; case ITEMS -> !entity && !npc && !mayor; case ENTITIES -> entity; case NPCS -> npc; case MAYORS -> mayor; };
        }
        public Filter next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public record Item(String id, String name, String minecraftId, int damage, String nbt, List<String> lore) {}
    public record Ingredient(String id, double amount) {}
    public record RecipeView(String type, List<Ingredient> inputs, List<Ingredient> outputs, String note) {}
    public record Snapshot(State state, List<Item> items, Map<String, Item> itemIndex, Map<String, Set<NEURecipe>> recipes,
                           Map<String, Set<NEURecipe>> usages, Map<String, List<String>> parents, boolean updating, String error) {
        private static Snapshot loading() { return new Snapshot(State.LOADING, List.of(), Map.of(), Map.of(), Map.of(), Map.of(), true, ""); }
        private Snapshot withRefresh(boolean refreshing, String message) { return new Snapshot(state, items, itemIndex, recipes, usages, parents, refreshing, message); }
    }

    public static void requestReiReload() {
        if (!FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) return;
        try { Class.forName("com.froggylord.constellation.compat.rei.LyraReiReloadBridge").getMethod("repositoryReady").invoke(null); }
        catch (ReflectiveOperationException exception) { ConstellationClient.LOGGER.debug("REI reload request skipped", exception); }
    }
}
