package com.froggylord.constellation.api;

import com.froggylord.constellation.ConstellationClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

// ported from SkyBlockPv (modified MIT): data/api/CfData.kt, data/repo/CfCodecs.kt, screens/windowed/tabs/ChocolateFactoryScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileChocolateData {
    private static final URI SOURCE = URI.create(
        "https://raw.githubusercontent.com/meowdding/meowdding-repo/master/repo/pv/chocolate_factory/rabbits.json");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final List<EmployeeDefinition> EMPLOYEES = List.of(
        new EmployeeDefinition("rabbit_bro", "Rabbit Bro", 1),
        new EmployeeDefinition("rabbit_cousin", "Rabbit Cousin", 2),
        new EmployeeDefinition("rabbit_sis", "Rabbit Sis", 3),
        new EmployeeDefinition("rabbit_father", "Rabbit Daddy", 4),
        new EmployeeDefinition("rabbit_grandma", "Rabbit Granny", 5),
        new EmployeeDefinition("rabbit_uncle", "Rabbit Uncle", 6),
        new EmployeeDefinition("rabbit_dog", "Rabbit Dog", 7));
    private static final long[] PRESTIGE = {0, 150_000_000L, 1_000_000_000L, 4_000_000_000L, 10_000_000_000L, 30_000_000_000L};
    private static final long[] HITMAN_STEPS = {1_000, 10_000, 100_000, 200_000, 400_000, 600_000, 800_000,
        1_000_000, 1_500_000, 2_000_000, 3_000_000, 4_000_000, 5_000_000, 6_000_000, 7_000_000,
        8_500_000, 10_000_000, 12_000_000, 14_000_000, 16_000_000, 18_000_000, 20_000_000,
        22_000_000, 24_000_000, 26_000_000, 28_000_000, 30_000_000, 32_000_000};
    private static volatile Catalogue catalogue;
    private static volatile CompletableFuture<Catalogue> loading;

    private ProfileChocolateData() {}

    public static synchronized CompletableFuture<Catalogue> load(boolean refresh) {
        long ttl = Math.clamp(ConstellationClient.cfg().lyra.profileChocolateCacheHours, 1, 168) * 3_600_000L;
        if (!refresh && catalogue != null && System.currentTimeMillis() - catalogue.loadedAt() < ttl)
            return CompletableFuture.completedFuture(catalogue);
        if (loading != null && !loading.isDone()) return loading;
        loading = CompletableFuture.supplyAsync(() -> {
            Catalogue cached = readCache();
            if (!refresh && cached != null && System.currentTimeMillis() - cached.loadedAt() < ttl) {
                catalogue = cached;
                return cached;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(SOURCE).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Constellation/0.9/Minecraft-26.2").GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200)
                    throw new IllegalStateException("Rabbit catalogue request failed (" + response.statusCode() + ").");
                Catalogue loaded = parse(response.body(), System.currentTimeMillis(), false);
                Files.writeString(cachePath(), response.body(), StandardCharsets.UTF_8);
                catalogue = loaded;
                return loaded;
            } catch (Exception error) {
                if (cached != null) {
                    catalogue = cached;
                    return cached;
                }
                throw new RuntimeException("Rabbit catalogue is unavailable.", error);
            }
        });
        return loading;
    }

    public static Result calculate(JsonObject member, Catalogue catalogue, String employeeSort,
                                   boolean hideZeroEmployees) {
        JsonObject data = object(object(member, "events"), "easter");
        JsonObject rawEmployees = object(data, "employees");
        List<Employee> employees = new ArrayList<>();
        Set<String> knownEmployees = new HashSet<>();
        for (EmployeeDefinition definition : EMPLOYEES) {
            knownEmployees.add(definition.id());
            employees.add(new Employee(definition.id(), definition.name(), integer(rawEmployees.get(definition.id())),
                definition.multiplier(), false));
        }
        for (var entry : rawEmployees.entrySet())
            if (!knownEmployees.contains(entry.getKey()))
                employees.add(new Employee(entry.getKey(), title(entry.getKey()), integer(entry.getValue()), 0, true));
        employees.removeIf(value -> hideZeroEmployees && value.level() == 0);
        employees.sort(employeeComparator(employeeSort));

        JsonObject rabbits = object(data, "rabbits");
        Map<String, Integer> rarityCounts = new LinkedHashMap<>();
        int knownRabbits = 0;
        Set<String> knownIds = new HashSet<>();
        if (catalogue != null) {
            for (var entry : catalogue.rabbits().entrySet()) {
                int count = 0;
                for (String id : entry.getValue()) {
                    knownIds.add(id);
                    if (primitiveNumber(rabbits.get(id))) count++;
                }
                rarityCounts.put(title(entry.getKey()), count);
                knownRabbits += count;
            }
        }
        int unknownRabbits = 0;
        for (var entry : rabbits.entrySet())
            if (!knownIds.contains(entry.getKey()) && primitiveNumber(entry.getValue())) unknownRabbits++;

        JsonObject tower = object(data, "time_tower");
        JsonObject hitman = object(data, "rabbit_hitmen");
        int prestige = integer(data.get("chocolate_level"));
        long sincePrestige = whole(data.get("chocolate_since_prestige"));
        long nextPrestige = prestige >= PRESTIGE.length ? 0 : PRESTIGE[prestige];
        int hitmanSlots = integer(hitman.get("rabbit_hitmen_slots"));
        long[] hitmanCosts = cumulative(HITMAN_STEPS);
        long hitmanPaid = hitmanSlots <= 0 ? 0 : hitmanCosts[Math.min(hitmanCosts.length, hitmanSlots) - 1];
        int production = employees.stream().mapToInt(value -> value.level() * value.multiplier()).sum();

        return new Result(!data.isEmpty(), whole(data.get("chocolate")), whole(data.get("total_chocolate")),
            sincePrestige, prestige, nextPrestige == 0 ? 0 : Math.max(0, nextPrestige - sincePrestige),
            integer(data.get("rabbit_barn_capacity_level")) * 2 + 18,
            integer(data.get("click_upgrades")), integer(data.get("chocolate_multiplier_upgrades")),
            integer(data.get("rabbit_rarity_upgrades")), List.copyOf(employees), production,
            integer(tower.get("level")), integer(tower.get("charges")), whole(tower.get("activation_time")),
            hitmanSlots, integer(hitman.get("missed_uncollected_eggs")), hitmanPaid, hitmanCosts[hitmanCosts.length - 1],
            Map.copyOf(rarityCounts), knownRabbits, unknownRabbits, whole(data.get("last_viewed_chocolate_factory")),
            catalogue != null && catalogue.cached(), catalogue == null ? 0 : catalogue.total());
    }

    private static Catalogue readCache() {
        try {
            Path path = cachePath();
            if (!Files.isRegularFile(path)) return null;
            return parse(Files.readString(path), Files.getLastModifiedTime(path).toMillis(), true);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Catalogue parse(String body, long loadedAt, boolean cached) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        Map<String, List<String>> values = new LinkedHashMap<>();
        Set<String> unique = new HashSet<>();
        for (var entry : root.entrySet()) {
            if (!entry.getValue().isJsonArray()) continue;
            List<String> ids = new ArrayList<>();
            for (JsonElement value : entry.getValue().getAsJsonArray())
                if (value.isJsonPrimitive() && unique.add(value.getAsString())) ids.add(value.getAsString());
            values.put(entry.getKey(), List.copyOf(ids));
        }
        if (values.size() != 7 || unique.size() < 500)
            throw new IllegalArgumentException("Rabbit catalogue is incomplete.");
        return new Catalogue(Map.copyOf(values), unique.size(), loadedAt, cached);
    }

    private static Comparator<Employee> employeeComparator(String sort) {
        Comparator<Employee> canonical = Comparator.comparingInt(value -> {
            for (int i = 0; i < EMPLOYEES.size(); i++) if (EMPLOYEES.get(i).id().equals(value.id())) return i;
            return Integer.MAX_VALUE;
        });
        return switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "LEVEL" -> Comparator.comparingInt(Employee::level).reversed().thenComparing(Employee::name);
            case "PRODUCTION" -> Comparator.comparingInt((Employee value) -> value.level() * value.multiplier())
                .reversed().thenComparing(Employee::name);
            case "NAME" -> Comparator.comparing(Employee::name);
            default -> canonical.thenComparing(Employee::name);
        };
    }

    private static long[] cumulative(long[] steps) {
        long[] values = new long[steps.length];
        long total = 0;
        for (int i = 0; i < steps.length; i++) {
            total += steps[i];
            values[i] = total;
        }
        return values;
    }
    private static boolean primitiveNumber(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }
    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static int integer(JsonElement value) {
        try { return value == null ? 0 : value.getAsInt(); } catch (RuntimeException ignored) { return 0; }
    }
    private static long whole(JsonElement value) {
        try { return value == null ? 0 : value.getAsLong(); } catch (RuntimeException ignored) { return 0; }
    }
    private static String title(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.replace('_', ' ').toLowerCase(Locale.ROOT).split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
    private static Path cachePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("constellation-chocolate-rabbits.json");
    }

    private record EmployeeDefinition(String id, String name, int multiplier) {}
    public record Catalogue(Map<String, List<String>> rabbits, int total, long loadedAt, boolean cached) {}
    public record Employee(String id, String name, int level, int multiplier, boolean unknown) {}
    public record Result(boolean available, long chocolate, long totalChocolate, long chocolateSincePrestige,
                         int prestige, long prestigeRemaining, int barnCapacity, int clickUpgrades,
                         int multiplierUpgrades, int rarityUpgrades, List<Employee> employees,
                         int employeeProduction, int timeTowerLevel, int timeTowerCharges,
                         long timeTowerActivation, int hitmanSlots, int uncollectedEggs, long hitmanPaid,
                         long hitmanTotal, Map<String, Integer> rarityCounts, int knownRabbits,
                         int unknownRabbits, long lastViewed, boolean catalogueCached, int catalogueTotal) {}
}
