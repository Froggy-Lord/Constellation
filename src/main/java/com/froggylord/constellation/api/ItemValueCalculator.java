package com.froggylord.constellation.api;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * full item-value calculator — reads every Hypixel SkyBlock NBT component
 * that affects an item's market value and builds a total estimate.
 * powers chest profit, inventory value, and any future flip/stats helper.
 *
 * each component evaluator returns a COST CONTRIBUTION (how much the buyer
 * paid to add this to the item). bazaar/LBIN base price is layered on top.
 */
public final class ItemValueCalculator {

    private ItemValueCalculator() {}

    // ---- public api ----

    /** total estimated value of one item stack (before stack count) */
    public static double value(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        CompoundTag extra = cd.copyTag().getCompoundOrEmpty("ExtraAttributes");
        if (extra.isEmpty()) return 0;
        String id = extra.getStringOr("id", "");
        if (id.isEmpty()) return 0;

        double base = PriceProvider.value(id);
        double bonus = 0;

        bonus += reforgeValue(extra);
        bonus += starsValue(extra);
        bonus += masterStarsValue(extra);
        bonus += hotPotatoValue(extra);
        bonus += recombValue(extra);
        bonus += gemstoneValue(extra);
        bonus += enchantValue(stack, extra);
        bonus += artOfWarValue(extra);
        bonus += scrollValue(extra);
        bonus += recombsOnArmor(extra);

        return Math.max(base + bonus, base); // bonus is additive, never negative
    }

    // ---- individual evaluators ----

    private static double reforgeValue(CompoundTag extra) {
        String mod = extra.getStringOr("modifier", "");
        if (mod.isEmpty()) return 0;
        // rough: any reforge adds a reforge stone's worth
        return switch (mod) {
            case "withered", "fabled", "suspicious", "precise" -> 2_000_000;
            case "ancient", "renowned" -> 5_000_000;
            case "jolly", "fruitful" -> 500_000;
            default -> 1_000_000; // average stone cost
        };
    }

    private static double starsValue(CompoundTag extra) {
        int stars = extra.getIntOr("upgrade_level", 0);
        if (stars <= 0) return 0;
        // essence cost per star varies but ~200k-500k per star avg
        return stars * 300_000;
    }

    private static double masterStarsValue(CompoundTag extra) {
        int ms = extra.getIntOr("dungeon_item_level", 0);
        if (ms <= 5) return 0; // first 5 are regular stars
        int master = ms - 5;
        // master stars are ~5m each
        return master * 5_000_000;
    }

    private static double hotPotatoValue(CompoundTag extra) {
        int hpb = extra.getIntOr("hot_potato_count", 0);
        if (hpb <= 0) return 0;
        // hot potato books ~100k, fuming ~1m each
        int normal = Math.min(hpb, 10);
        int fuming = Math.max(0, hpb - 10);
        return normal * 70_000 + fuming * 1_200_000;
    }

    private static double recombValue(CompoundTag extra) {
        int upgrades = extra.getIntOr("rarity_upgrades", 0);
        return upgrades > 0 ? 8_000_000 * upgrades : 0; // ~8m per recomb
    }

    private static double gemstoneValue(CompoundTag extra) {
        CompoundTag gems = extra.getCompoundOrEmpty("gems");
        if (gems.isEmpty()) return 0;
        // each perfect gemstone slot is worth 15-30m, fine ~500k, flawless ~5m
        double total = 0;
        for (String key : gems.keySet()) {
            String quality = gems.getStringOr(key, "");
            total += switch (quality) {
                case "PERFECT" -> 20_000_000;
                case "FLAWLESS" -> 4_000_000;
                case "FINE" -> 500_000;
                case "ROUGH" -> 50_000;
                default -> 0;
            };
        }
        return total;
    }

    private static double enchantValue(ItemStack stack, CompoundTag extra) {
        CompoundTag enchants = extra.getCompoundOrEmpty("enchantments");
        if (enchants.isEmpty()) return 0;
        // tier 6/7 enchants are the biggest cost drivers
        double total = 0;
        for (String key : enchants.keySet()) {
            int lvl = enchants.getIntOr(key, 0);
            if (lvl < 5) continue; // only tier 5+ add significant value
            total += switch (lvl) {
                case 5 -> 200_000;
                case 6 -> 5_000_000;
                case 7 -> 30_000_000;
                default -> 0;
            };
        }
        // ultimate enchants are separate
        CompoundTag ult = extra.getCompoundOrEmpty("ultimate_enchantments");
        for (String k : ult.keySet()) {
            int ulvl = ult.getIntOr(k, 0);
            if (ulvl >= 5) total += 15_000_000 * (ulvl - 4);
        }
        return total;
    }

    private static double artOfWarValue(CompoundTag extra) {
        // art of war = +1 damage stat (~10m), art of peace = +1 health (~5m)
        boolean war = extra.getIntOr("art_of_war_count", 0) > 0;
        boolean peace = extra.getIntOr("art_of_peace_count", 0) > 0;
        return (war ? 10_000_000 : 0) + (peace ? 5_000_000 : 0);
    }

    private static double scrollValue(CompoundTag extra) {
        // ability scrolls applied to wither blades
        double total = 0;
        if (!extra.getStringOr("ability_scroll_0", "").isEmpty()) total += 150_000_000; // wither shield
        if (!extra.getStringOr("ability_scroll_1", "").isEmpty()) total += 100_000_000; // implosion
        if (!extra.getStringOr("ability_scroll_2", "").isEmpty()) total += 80_000_000;  // shadow warp
        return total;
    }

    private static double recombsOnArmor(CompoundTag extra) {
        // some armor pieces have multiple recombs from dungeon chests
        int recombCount = extra.getIntOr("recombobulated", 0);
        return recombCount > 1 ? (recombCount - 1) * 8_000_000 : 0;
    }
}
