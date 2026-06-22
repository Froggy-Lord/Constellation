package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
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

    public static void init(LyraConfig config) {
        cfg = config;
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (cfg == null || stack.isEmpty()) return;
            if (!ConstellationClient.loc().onHypixel()) return;
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
            if (cfg.tooltipSkyblockId) {
                String id = extra.getStringOr("id", "");
                if (!id.isEmpty()) lines.add(Component.literal("§8" + id));
            }
        });
    }

    private static String money(double n) {
        if (n < 1000) return String.format("%.1f", n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        if (n < 1_000_000_000) return String.format("%.2fM", n / 1_000_000.0);
        return String.format("%.2fB", n / 1_000_000_000.0);
    }

    private static String cap(String s) {
        s = s.replace('_', ' ');
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
