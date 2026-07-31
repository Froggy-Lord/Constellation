package com.froggylord.constellation.api;

import com.froggylord.constellation.constellation.LyraTooltips;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ported from SkyHanni (LGPL-3.0-or-later): features/misc/items/EstimatedItemValueCalculator.kt
// ported from SkyHanni (LGPL-3.0-or-later): utils/SkyBlockItemModifierUtils.kt
public final class ItemValueCalculator {
    private static final String[] MASTER_STARS = {
        "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"
    };

    private ItemValueCalculator() {}

    public record ComponentValue(String id, int count, double unitPrice) {
        public double total() { return count * unitPrice; }
    }

    public record Result(double total, double base, List<ComponentValue> components, boolean complete) {
        public static final Result EMPTY = new Result(0, 0, List.of(), false);
        public double additions() { return Math.max(0, total - base); }
    }

    /** Conservative market-backed estimate for one item. Unknown modifiers are never guessed. */
    public static Result estimate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Result.EMPTY;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Result.EMPTY;
        CompoundTag extra = data.copyTag().getCompoundOrEmpty("ExtraAttributes");
        String rawId = extra.getStringOr("id", "");
        if (rawId.isBlank()) return Result.EMPTY;

        String marketId = LyraTooltips.marketId(stack);
        double base = marketId.isBlank() ? 0 : PriceProvider.sellValue(marketId);
        if (base <= 0 && !marketId.isBlank()) PriceProvider.warm(marketId);
        boolean[] complete = {base > 0};
        List<ComponentValue> parts = new ArrayList<>();

        add(parts, complete, "HOT_POTATO_BOOK", Math.min(10, positive(extra, "hot_potato_count")));
        add(parts, complete, "FUMING_POTATO_BOOK", Math.max(0, positive(extra, "hot_potato_count") - 10));
        add(parts, complete, "RECOMBOBULATOR_3000", positive(extra, "rarity_upgrades"));
        add(parts, complete, "FARMING_FOR_DUMMIES", positive(extra, "farming_for_dummies_count"));
        add(parts, complete, "OVERCLOCKER_3000", positive(extra, "levelable_overclocks"));
        add(parts, complete, "POLARVOID_BOOK", positive(extra, "polarvoid"));
        add(parts, complete, "BOOKWORM_BOOK", positive(extra, "bookworm_books"));
        add(parts, complete, "TRANSMISSION_TUNER", positive(extra, "tuned_transmission"));
        add(parts, complete, "MANA_DISINTEGRATOR", positive(extra, "mana_disintegrator_count"));
        add(parts, complete, "JALAPENO_BOOK", positive(extra, "jalapeno_count"));
        add(parts, complete, "WOOD_SINGULARITY", positive(extra, "wood_singularity_count"));
        add(parts, complete, "THE_ART_OF_WAR", positive(extra, "art_of_war_count"));
        add(parts, complete, "WET_BOOK", positive(extra, "wet_book_count"));
        add(parts, complete, "POCKET_SACK_IN_A_SACK", positive(extra, "sack_pss"));
        add(parts, complete, "DIVAN_POWDER_COATING", present(extra, "divan_powder_coating"));
        add(parts, complete, "BOOK_OF_STATS", present(extra, "stats_book"));
        add(parts, complete, "THE_ART_OF_PEACE", present(extra, "artOfPeaceApplied"));
        add(parts, complete, "ETHERWARP_MERGER", present(extra, "ethermerge"));
        add(parts, complete, "SIL_EX", Math.max(positive(extra, "silex"), positive(extra, "silex_count")));
        add(parts, complete, "MITHRIL_INFUSION", positiveByte(extra, "mithril_infusion"));
        add(parts, complete, "FREE_WILL", positiveByte(extra, "free_will"));

        int upgrades = Math.max(positive(extra, "upgrade_level"), positive(extra, "dungeon_item_level"));
        for (int i = 0; i < Math.min(5, Math.max(0, upgrades - 5)); i++) add(parts, complete, MASTER_STARS[i], 1);
        addStringComponent(parts, complete, extra, "skin");
        addStringComponent(parts, complete, extra, "dye_item");
        addStringComponent(parts, complete, extra, "power_ability_scroll");
        addStringComponent(parts, complete, extra, "drill_part_upgrade_module");
        addStringComponent(parts, complete, extra, "drill_part_engine");
        addStringComponent(parts, complete, extra, "drill_part_fuel_tank");

        if (!rawId.equals("ENCHANTED_BOOK")) {
            CompoundTag enchants = extra.getCompoundOrEmpty("enchantments");
            for (String key : enchants.keySet()) {
                int level = enchants.getIntOr(key, 0);
                if (level > 0) add(parts, complete, "ENCHANTMENT_" + key.toUpperCase(Locale.ROOT) + "_" + level, 1);
            }
        }
        if (!rawId.equals("RUNE") && !rawId.equals("UNIQUE_RUNE")) {
            CompoundTag runes = extra.getCompoundOrEmpty("runes");
            for (String key : runes.keySet()) {
                int level = runes.getIntOr(key, 0);
                if (level > 0) add(parts, complete, key.toUpperCase(Locale.ROOT) + "_" + level + "_RUNE", 1);
            }
        }
        CompoundTag gems = extra.getCompoundOrEmpty("gems");
        for (String key : gems.keySet()) {
            String quality = gems.getStringOr(key, "").toUpperCase(Locale.ROOT);
            if (quality.matches("ROUGH|FLAWED|FINE|FLAWLESS|PERFECT")) {
                String type = key.replaceAll("_[0-9]+$", "").toUpperCase(Locale.ROOT);
                add(parts, complete, quality + "_" + type + "_GEM", 1);
            } else complete[0] = false;
        }

        // Regular essence stars and reforges need repository-specific costs. Mark them incomplete instead of inventing a value.
        if (upgrades > 0 && upgrades <= 5 || !extra.getStringOr("modifier", "").isBlank()) complete[0] = false;
        if (rawId.equals("PET") || !extra.getCompoundOrEmpty("attributes").isEmpty()
            || extra.contains("art_of_control_count") || extra.contains("talisman_enrichment")
            || extra.contains("unlocked_slots") || extra.contains("gemstone_slots")) complete[0] = false;
        for (Tag tag : extra.getListOrEmpty("ability_scroll")) {
            String scroll = tag.asString().orElse("").toUpperCase(Locale.ROOT);
            if (scroll.equals("ULTIMATE_WITHER_SCROLL")) {
                add(parts, complete, "IMPLOSION_SCROLL", 1);
                add(parts, complete, "WITHER_SHIELD_SCROLL", 1);
                add(parts, complete, "SHADOW_WARP_SCROLL", 1);
            } else if (!scroll.isBlank()) add(parts, complete, scroll, 1);
        }
        double total = base;
        for (ComponentValue part : parts) total += part.total();
        return new Result(total, base, List.copyOf(parts), complete[0]);
    }

    public static double value(ItemStack stack) { return estimate(stack).total(); }

    private static int positive(CompoundTag extra, String key) { return Math.max(0, extra.getIntOr(key, 0)); }
    private static int positiveByte(CompoundTag extra, String key) { return extra.getByteOr(key, (byte) 0) > 0 ? 1 : 0; }
    private static int present(CompoundTag extra, String key) {
        return extra.getByteOr(key, (byte) 0) > 0 || extra.getIntOr(key, 0) > 0 ? 1 : 0;
    }

    private static void addStringComponent(List<ComponentValue> parts, boolean[] complete, CompoundTag extra, String key) {
        String id = extra.getStringOr(key, "").trim();
        if (!id.isBlank()) add(parts, complete, id.toUpperCase(Locale.ROOT), 1);
    }

    private static void add(List<ComponentValue> parts, boolean[] complete, String id, int count) {
        if (count <= 0 || id.isBlank()) return;
        double price = PriceProvider.purchaseValue(id);
        if (price <= 0) {
            PriceProvider.warm(id);
            complete[0] = false;
            return;
        }
        parts.add(new ComponentValue(id, count, price));
    }
}
