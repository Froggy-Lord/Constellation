package com.froggylord.constellation.api;

import com.froggylord.constellation.constellation.LyraTooltips;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// ported from SkyBlockPv (modified MIT): feature/networth/NetworthCalculator.kt, NetworthCategory.kt
// Portions of this code are from the SkyBlockPv mod.
// valuation ported from SkyHanni (LGPL-3.0-or-later): features/misc/items/EstimatedItemValueCalculator.kt
public final class ProfileWealthCalculator {
    private ProfileWealthCalculator() {}

    public static CompletableFuture<Void> calculate(JsonObject profile, JsonObject member, ProfileItemDecoder.Result storage,
                                                    int maxRequests, int intervalMs, Consumer<Result> update,
                                                    BooleanSupplier cancelled) {
        return CompletableFuture.runAsync(() -> {
            BazaarApi.ensureFresh();
            Result result = evaluate(profile, member, storage, false);
            update.accept(result);
            int sent = 0;
            for (String id : result.pendingIds()) {
                if (cancelled.getAsBoolean() || sent >= Math.clamp(maxRequests, 0, 200)) break;
                PriceProvider.warm(id);
                sent++;
                sleep(Math.clamp(intervalMs, 250, 5_000));
                if (cancelled.getAsBoolean()) break;
                update.accept(evaluate(profile, member, storage, true));
            }
            if (!cancelled.getAsBoolean() && sent > 0) {
                sleep(2_000);
                update.accept(evaluate(profile, member, storage, false));
            }
        });
    }

    private static Result evaluate(JsonObject profile, JsonObject member, ProfileItemDecoder.Result storage, boolean warming) {
        Map<String, MutableCategory> categories = new LinkedHashMap<>();
        Set<String> pending = new LinkedHashSet<>();
        for (ProfileItemDecoder.Container container : storage.containers()) {
            String category = category(container.name());
            MutableCategory values = categories.computeIfAbsent(category, MutableCategory::new);
            for (ItemStack stack : container.items()) add(values, stack, pending);
        }
        addSacks(categories.computeIfAbsent("Sacks", MutableCategory::new), member, pending);

        double currency = number(number(path(member, "currencies.coin_purse")), number(path(member, "coin_purse")))
            + number(path(profile, "banking.balance"));
        List<Category> complete = categories.values().stream().filter(category -> category.totalStacks > 0)
            .map(MutableCategory::finish).sorted((a, b) -> Double.compare(b.value, a.value)).toList();
        double itemValue = complete.stream().mapToDouble(Category::value).sum();
        int total = complete.stream().mapToInt(Category::totalStacks).sum();
        int priced = complete.stream().mapToInt(Category::pricedStacks).sum();
        int completeCount = complete.stream().mapToInt(Category::completeStacks).sum();
        return new Result(currency + itemValue, currency, itemValue, total, priced, completeCount,
            List.copyOf(complete), Set.copyOf(pending), warming, System.currentTimeMillis());
    }

    private static void add(MutableCategory category, ItemStack stack, Set<String> pending) {
        if (stack == null || stack.isEmpty()) return;
        int count = Math.max(1, stack.getCount());
        category.totalStacks++;
        ItemValueCalculator.Result estimate = ItemValueCalculator.estimateCached(stack);
        String id = LyraTooltips.marketId(stack);
        if (estimate.total() <= 0) {
            if (!id.isBlank()) pending.add(id);
            return;
        }
        double value = estimate.total() * count;
        category.pricedStacks++;
        if (estimate.complete()) category.completeStacks++;
        category.value += value;
        String name = stack.getHoverName().getString();
        if (name.isBlank()) name = id.isBlank() ? "Unknown item" : id;
        MutableItem item = category.items.get(name + "\u0000" + id);
        if (item == null) {
            item = new MutableItem(name, id);
            category.items.put(name + "\u0000" + id, item);
        }
        item.value += value;
        item.count += count;
        item.complete &= estimate.complete();
    }

    private static void addSacks(MutableCategory category, JsonObject member, Set<String> pending) {
        JsonObject counts = object(object(member, "inventory"), "sacks_counts");
        for (var entry : counts.entrySet()) {
            long amount;
            try { amount = entry.getValue().getAsLong(); } catch (Exception ignored) { continue; }
            if (amount <= 0) continue;
            category.totalStacks++;
            double price = PriceProvider.sellValue(entry.getKey());
            if (price <= 0) {
                pending.add(entry.getKey());
                continue;
            }
            double value = price * amount;
            category.pricedStacks++;
            category.completeStacks++;
            category.value += value;
            MutableItem item = new MutableItem(title(entry.getKey()), entry.getKey());
            item.value = value;
            item.count = amount;
            category.items.put(entry.getKey(), item);
        }
    }

    private static String category(String name) {
        if (name.startsWith("Ender Chest")) return "Ender Chest";
        if (name.startsWith("Backpack")) return "Backpacks";
        if (name.startsWith("Accessory Bag")) return "Accessory Bag";
        if (name.startsWith("Wardrobe")) return "Wardrobe";
        if (name.startsWith("Equipment Sets")) return "Equipment Sets";
        return name;
    }

    private static JsonElement path(JsonObject root, String path) {
        JsonElement value = root;
        for (String part : path.split("\\.")) {
            if (value == null || !value.isJsonObject() || !value.getAsJsonObject().has(part)) return null;
            value = value.getAsJsonObject().get(part);
        }
        return value;
    }
    private static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }
    private static double number(JsonElement value) { try { return value == null ? 0 : value.getAsDouble(); } catch (Exception ignored) { return 0; } }
    private static double number(double... values) { for (double value : values) if (value != 0) return value; return 0; }
    private static String title(String id) {
        String[] words = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
    private static void sleep(long millis) { try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    private static final class MutableCategory {
        final String name;
        final Map<String, MutableItem> items = new LinkedHashMap<>();
        double value;
        int totalStacks, pricedStacks, completeStacks;
        MutableCategory(String name) { this.name = name; }
        Category finish() {
            List<Item> sorted = items.values().stream().map(MutableItem::finish)
                .sorted((a, b) -> Double.compare(b.value, a.value)).toList();
            return new Category(name, value, totalStacks, pricedStacks, completeStacks, sorted);
        }
    }
    private static final class MutableItem {
        final String name, id;
        double value;
        long count;
        boolean complete = true;
        MutableItem(String name, String id) { this.name = name; this.id = id; }
        Item finish() { return new Item(name, id, value, count, complete); }
    }

    public record Item(String name, String id, double value, long count, boolean complete) {}
    public record Category(String name, double value, int totalStacks, int pricedStacks, int completeStacks, List<Item> items) {}
    public record Result(double total, double currency, double itemValue, int totalStacks, int pricedStacks,
                         int completeStacks, List<Category> categories, Set<String> pendingIds,
                         boolean warming, long updatedAt) {}
}
