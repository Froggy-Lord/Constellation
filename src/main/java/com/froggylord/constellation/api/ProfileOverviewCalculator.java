package com.froggylord.constellation.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ported from SkyBlockPv (modified MIT): data/api/Currency.kt, data/api/Maxwell.kt, api/data/profile/SkyBlockProfile.kt, screens/windowed/tabs/MainScreen.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileOverviewCalculator {
    private static final List<String> ESSENCE_ORDER = List.of("WITHER", "SPIDER", "UNDEAD", "DRAGON",
        "GOLD", "DIAMOND", "ICE", "CRIMSON");

    private ProfileOverviewCalculator() {}

    public static Result calculate(JsonObject profile, JsonObject member) {
        JsonObject memberProfile = object(member, "profile");
        JsonObject currencies = object(member, "currencies");
        long skyBlockExperience = whole(path(member, "leveling.experience"));
        long firstJoin = whole(first(memberProfile.get("first_join"), member.get("first_join")));
        long purse = whole(first(currencies.get("coin_purse"), member.get("coin_purse")));
        long motes = whole(currencies.get("motes_purse"));
        long soloBank = whole(memberProfile.get("bank_account"));
        JsonObject banking = object(profile, "banking");
        long profileBank = whole(banking.get("balance"));
        boolean cookie = bool(memberProfile.get("cookie_buff_active"));
        long fairySouls = whole(first(path(member, "fairy_soul.total_collected"),
            member.get("fairy_souls_collected")));

        List<Essence> essence = new ArrayList<>();
        JsonObject rawEssence = object(currencies, "essence");
        for (var entry : rawEssence.entrySet()) {
            JsonObject state = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
            essence.add(new Essence(entry.getKey(), title(entry.getKey()), whole(state.get("current"))));
        }
        essence.sort(Comparator.<Essence>comparingInt(value -> {
            int index = ESSENCE_ORDER.indexOf(value.id().toUpperCase(Locale.ROOT));
            return index < 0 ? Integer.MAX_VALUE : index;
        }).thenComparing(Essence::name));

        List<Transaction> transactions = new ArrayList<>();
        JsonArray rawTransactions = array(banking.get("transactions"));
        for (JsonElement element : rawTransactions) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            transactions.add(new Transaction(whole(value.get("amount")), whole(value.get("timestamp")),
                string(value.get("action"), "UNKNOWN"), string(value.get("initiator_name"), "Unknown")));
        }
        transactions.sort(Comparator.comparingLong(Transaction::timestamp).reversed());

        List<ProfileSkillCalculator.Skill> skills = ProfileSkillCalculator.calculate(member);
        double skillAverage = ProfileSkillCalculator.average(skills, true, true, false);
        double skillXp = skills.stream().filter(ProfileSkillCalculator.Skill::available)
            .mapToDouble(ProfileSkillCalculator.Skill::xp).sum();
        ProfileMobCalculator.Result mobs = ProfileMobCalculator.calculate(member, true,
            "ALL", "KILLS", "", 0, 0);
        ProfilePetCalculator.Pet activePet = ProfilePetCalculator.calculate(member, "DEFAULT", true)
            .pets().stream().filter(ProfilePetCalculator.Pet::active).findFirst().orElse(null);

        JsonObject maxwell = object(member, "accessory_bag_storage");
        JsonObject rawTunings = object(object(maxwell, "tuning"), "slot_0");
        Map<String, Integer> tunings = new LinkedHashMap<>();
        for (var entry : rawTunings.entrySet()) tunings.put(entry.getKey(), integer(entry.getValue()));
        int activeMembers = 0;
        for (JsonElement value : object(profile, "members").asMap().values()) {
            if (!value.isJsonObject() || path(value.getAsJsonObject(), "profile.deletion_notice") == null)
                activeMembers++;
        }
        int contacts = array(path(member, "nether_island_player_data.abiphone.active_contacts")).size();
        boolean consumedPrism = bool(path(member, "rift.access.consumed_prism"));

        return new Result(skyBlockExperience, purse, motes, soloBank, profileBank, cookie, fairySouls,
            firstJoin, string(profile.get("game_mode"), "normal"), activeMembers, skillAverage, skillXp,
            mobs.totalKills(), mobs.totalDeaths(), activePet, List.copyOf(essence),
            List.copyOf(transactions), string(maxwell.get("selected_power"), ""),
            integer(maxwell.get("highest_magical_power")), integer(maxwell.get("bag_upgrades_purchased")),
            Map.copyOf(tunings), consumedPrism, contacts);
    }

    private static JsonElement first(JsonElement first, JsonElement second) {
        return first != null && !first.isJsonNull() ? first : second;
    }
    private static JsonElement path(JsonObject root, String path) {
        JsonElement value = root;
        for (String part : path.split("\\.")) {
            if (value == null || !value.isJsonObject()) return null;
            value = value.getAsJsonObject().get(part);
        }
        return value;
    }
    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static JsonArray array(JsonElement value) {
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    private static long whole(JsonElement value) {
        try { return value == null ? 0 : value.getAsLong(); } catch (RuntimeException ignored) { return 0; }
    }
    private static int integer(JsonElement value) {
        try { return value == null ? 0 : value.getAsInt(); } catch (RuntimeException ignored) { return 0; }
    }
    private static boolean bool(JsonElement value) {
        try { return value != null && value.getAsBoolean(); } catch (RuntimeException ignored) { return false; }
    }
    private static String string(JsonElement value, String fallback) {
        try { return value == null ? fallback : value.getAsString(); } catch (RuntimeException ignored) { return fallback; }
    }
    private static String title(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.replace('_', ' ').toLowerCase(Locale.ROOT).split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    public record Essence(String id, String name, long amount) {}
    public record Transaction(long amount, long timestamp, String action, String initiator) {}
    public record Result(long skyBlockExperience, long purse, long motes, long soloBank,
                         long profileBank, boolean cookieBuffActive, long fairySouls, long firstJoin,
                         String profileType, int coopMembers, double skillAverage, double totalSkillXp,
                         long mobKills, long mobDeaths, ProfilePetCalculator.Pet activePet,
                         List<Essence> essence, List<Transaction> transactions, String selectedPower,
                         int highestMagicalPower, int bagUpgrades, Map<String, Integer> tunings,
                         boolean consumedRiftPrism, int abiphoneContacts) {}
}
