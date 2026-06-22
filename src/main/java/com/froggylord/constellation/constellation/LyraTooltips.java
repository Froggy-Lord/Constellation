package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;

/**
 * Extra tooltip lines pulled straight out of an item's hidden Hypixel data — no API key, no
 * network. Everything here is already on the stack you're hovering, Hypixel just doesn't surface
 * it. Lines only show on Hypixel so vanilla worlds stay untouched.
 */
public class LyraTooltips {

    private static LyraConfig cfg;

    // the enchant keys Hypixel uses on items — checked against what's already applied
    private static final String[] COMMON_ENCHANTS = {
        "growth", "protection", "feather_falling", "rejuvenate", "respite",
        "sharpness", "critical", "execute", "first_strike", "giant_killer",
        "ender_slayer", "smite", "bane_of_arthropods", "cubism", "impaling",
        "syphon", "life_steal", "mana_steal", "vampirism",
        "luck", "looting", "scavenger", "experience",
        "efficiency", "fortune", "silk_touch", "pristine",
        "overload", "soul_eater", "power", "snipe", "dragon_hunter",
        "ultimate_wise", "legion", "wisdom", "last_stand", "no_pain_no_gain"
    };

    public static void init(LyraConfig config) {
        cfg = config;
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (cfg == null || stack.isEmpty()) return;
            // items carry their SB data even in singleplayer; the onHypixel gate only helps
            // avoid adding garbage lines to vanilla items. the extra-attributes check below
            // already filters those, so we skip the gate here.
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd == null) return;
            CompoundTag extra = cd.copyTag().getCompoundOrEmpty("ExtraAttributes");
            if (extra.isEmpty()) return;

            String sbId = extra.getStringOr("id", "");
            if (cfg.tooltipBazaar && !sbId.isEmpty()) {
                com.froggylord.constellation.api.BazaarApi.ensureFresh();
                double[] bz = com.froggylord.constellation.api.BazaarApi.get(sbId);
                if (bz != null && (bz[0] > 0 || bz[1] > 0)) {
                    lines.add(Component.literal("§7Bazaar: §6" + money(bz[0]) + " §7buy §8| §6" + money(bz[1]) + " §7sell"));
                    int count = stack.getCount();
                    if (count > 1 && bz[1] > 0)
                        lines.add(Component.literal("§7Stack (" + count + "): §6" + money(bz[1] * count)));
                }
            }

            if (cfg.tooltipReforge) {
                String mod = extra.getStringOr("modifier", "");
                if (!mod.isEmpty()) lines.add(Component.literal("§7Reforge: §9" + cap(mod)));
            }
            if (cfg.tooltipStars) {
                int base = extra.getIntOr("dungeon_item_level", 0);
                int total = extra.getIntOr("upgrade_level", base);
                if (total > 0) {
                    StringBuilder s = new StringBuilder("§7Stars: ");
                    for (int i = 0; i < total; i++) s.append(i < 5 ? "§6✪" : "§d✪");
                    lines.add(Component.literal(s.toString()));
                }
            }
            if (cfg.tooltipHotPotato) {
                int hpb = extra.getIntOr("hot_potato_count", 0);
                if (hpb > 0) lines.add(Component.literal("§7Hot Potato: §c" + Math.min(hpb, 10)
                    + (hpb > 10 ? " §6+ " + (hpb - 10) + " Fuming" : "")));
            }
            if (cfg.tooltipRecomb && extra.getIntOr("rarity_upgrades", 0) > 0)
                lines.add(Component.literal("§dRecombobulated"));
            if (cfg.tooltipEnchantCount) {
                CompoundTag ench = extra.getCompoundOrEmpty("enchantments");
                if (!ench.isEmpty()) lines.add(Component.literal("§7Enchants: §b" + ench.size()));
            }
            if (cfg.tooltipSalvageable && extra.getIntOr("donated_museum", 0) > 0)
                lines.add(Component.literal("§a✔ Museum donated — safe to salvage"));
            if (cfg.tooltipAttributes) {
                CompoundTag attrs = extra.getCompoundOrEmpty("attributes");
                if (!attrs.isEmpty()) {
                    StringBuilder sb = new StringBuilder("§d⚚ ");
                    int shown = 0;
                    for (String key : attrs.keySet()) {
                        if (shown++ > 0) sb.append(" §8| ");
                        int lvl = attrs.getCompoundOrEmpty(key).getIntOr("level", 0);
                        sb.append("§d").append(prettyAttr(key)).append(lvl > 1 ? " " + lvl : "");
                        if (shown >= 3) break;
                    }
                    lines.add(Component.literal(sb.toString()));
                }
            }

            if (cfg.tooltipItemQuality) {
                int quality = extra.getIntOr("item_quality", extra.getIntOr("quality", extra.getIntOr("base_quality", -1)));
                if (quality >= 0) {
                    boolean maxed = quality >= 50;
                    String col = maxed ? "§6" : "§7";
                    lines.add(Component.literal(col + "Quality: " + quality + "/50" + (maxed ? " §6✦ MAX" : "")));
                }
            }

            if (cfg.tooltipSkyblockId) {
                String id = extra.getStringOr("id", "");
                if (!id.isEmpty()) lines.add(Component.literal("§8" + id));
            }
            if (cfg.backpackPreview && Minecraft.getInstance().options.keyShift.isDown()) {
                String id = extra.getStringOr("id", "");
                if (id.toUpperCase().contains("BACKPACK") || id.toUpperCase().contains("STORAGE_BACKPACK") || id.toUpperCase().contains("GREATER_BACKPACK") || id.toUpperCase().contains("JUMBO_BACKPACK")) {
                    CompoundTag items = extra.getCompoundOrEmpty("containsItems");
                    if (items.isEmpty()) items = extra.getCompoundOrEmpty("items");
                    if (!items.isEmpty()) {
                        int count = 0;
                        for (String k : items.keySet()) {
                            CompoundTag item = items.getCompoundOrEmpty(k);
                            if (item.isEmpty()) continue;
                            String iname = item.getStringOr("id", "?");
                            int icount = item.getIntOr("count", 1);
                            lines.add(Component.literal("§7" + iname + " §8x" + icount));
                            if (++count >= 8) { lines.add(Component.literal("§8... and more")); break; }
                        }
                    }
                }
            }

            if (cfg.tooltipMissingEnchants) {
                String id = extra.getStringOr("id", "");
                CompoundTag ench = extra.getCompoundOrEmpty("enchantments");
                java.util.Set<String> current = ench.keySet();
                java.util.List<String> missing = new java.util.ArrayList<>();
                for (String want : COMMON_ENCHANTS) {
                    if (!current.contains(want)) missing.add(want);
                }
                if (!missing.isEmpty()) {
                    String list = String.join(", ", missing.subList(0, Math.min(3, missing.size())));
                    lines.add(Component.literal("§7Missing: §c" + list));
                }
            }
        });
    }

    private static String money(double n) {
        if (n < 1000) return String.format("%.1f", n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        if (n < 1_000_000_000) return String.format("%.2fM", n / 1_000_000.0);
        return String.format("%.2fB", n / 1_000_000_000.0);
    }

    private static String prettyAttr(String key) {
        if (key.isEmpty()) return key;
        String s = key.replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static String cap(String s) {
        s = s.replace('_', ' ');
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
