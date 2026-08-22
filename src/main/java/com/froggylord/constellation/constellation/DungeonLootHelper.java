package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.ItemValueCalculator;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.OrionConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0): skyblock/dungeon/SalvageHelper.java
// ported from Skyblocker (LGPL-3.0): skyblock/dungeon/SellableItemsHighlighter.java
public final class DungeonLootHelper {
    private static final Pattern SALVAGEABLE = Pattern.compile("DUNGEON(?! ITEM)");
    private static final Set<String> SELLABLE_IDS = Set.of(
        "DEFUSE_KIT", "TRAINING_WEIGHTS", "DUNGEON_LORE_PAPER", "REVIVE_STONE");
    private static final String HEALING_POTION = "Healing VIII Splash Potion";

    private enum Mark { NONE, SALVAGE_SAFE, SALVAGE_UNKNOWN, SELLABLE }

    private DungeonLootHelper() {}

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (slot == null || slot.getItem().isEmpty()) return;
        OrionConfig cfg = config();
        if (cfg == null || !cfg.enabled || !ConstellationClient.loc().onHypixel()) return;
        Mark mark = classify(screen, slot, cfg);
        if (mark == Mark.NONE) return;
        int colour = switch (mark) {
            case SALVAGE_SAFE -> cfg.salvageSafeColour;
            case SALVAGE_UNKNOWN -> cfg.salvageUnknownColour;
            case SELLABLE -> cfg.sellableDungeonLootColour;
            default -> 0;
        };
        border(graphics, slot.x, slot.y, colour);
        String label = switch (mark) {
            case SALVAGE_SAFE -> "L";
            case SALVAGE_UNKNOWN -> "?";
            case SELLABLE -> "$";
            default -> "";
        };
        graphics.text(Minecraft.getInstance().font, label, slot.x + 10, slot.y + 1, colour | 0xFF000000, true);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, ItemStack stack, List<Component> original) {
        OrionConfig cfg = config();
        if (cfg == null || !cfg.enabled || !ConstellationClient.loc().onHypixel() || stack.isEmpty()) return original;
        Mark mark = classifyStack(screen, stack, cfg);
        if (mark == Mark.NONE) return original;
        ArrayList<Component> result = new ArrayList<>(original);
        if (mark == Mark.SALVAGE_SAFE) {
            double value = ItemValueCalculator.value(stack);
            result.add(Component.literal("§6Low-value salvage candidate §7(estimated " + coins(value) + " coins)"));
            result.add(Component.literal("§8Below your configured " + coins(cfg.salvageMaxValue) + " coin limit"));
            result.add(Component.literal("§8Museum donation status is not verified"));
        } else if (mark == Mark.SALVAGE_UNKNOWN) {
            result.add(Component.literal("§cPrice unknown - not marked safe to salvage"));
        } else {
            result.add(Component.literal("§eSellable dungeon loot"));
        }
        return result;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dungeonloot")
            .executes(ctx -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("limit")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("coins",
                        IntegerArgumentType.integer(0, 100_000_000))
                    .executes(ctx -> setLimit(IntegerArgumentType.getInteger(ctx, "coins"))))));
    }

    private static Mark classify(AbstractContainerScreen<?> screen, Slot slot, OrionConfig cfg) {
        String title = screen.getTitle().getString();
        if (cfg.salvageHelper && title.startsWith("Salvage Items")) return salvageMark(slot.getItem(), cfg);
        if (!cfg.sellableDungeonLoot || !(title.equals("Ophelia") || title.equals("Booster Cookie"))) return Mark.NONE;
        int index = screen.getMenu().slots.indexOf(slot);
        int inventoryStart = screen.getMenu().slots.size() - 36;
        boolean mainInventory = index >= inventoryStart && index < inventoryStart + 27;
        boolean hotbar = index >= inventoryStart + 27 && index < inventoryStart + 36;
        return (mainInventory || cfg.sellableIncludeHotbar && hotbar) && isSellable(slot.getItem()) ? Mark.SELLABLE : Mark.NONE;
    }

    private static Mark classifyStack(AbstractContainerScreen<?> screen, ItemStack stack, OrionConfig cfg) {
        String title = screen.getTitle().getString();
        if (cfg.salvageHelper && title.startsWith("Salvage Items")) return salvageMark(stack, cfg);
        if (cfg.sellableDungeonLoot && (title.equals("Ophelia") || title.equals("Booster Cookie")) && isSellable(stack))
            return Mark.SELLABLE;
        return Mark.NONE;
    }

    private static Mark salvageMark(ItemStack stack, OrionConfig cfg) {
        if (!hasSalvageLore(stack)) return Mark.NONE;
        if (cfg.salvageExcludeProtected && ItemProtection.isConfiguredProtected(stack)) return Mark.NONE;
        CompoundTag extra = extra(stack);
        if (cfg.salvageExcludeModified && isModified(extra)) return Mark.NONE;
        double value = ItemValueCalculator.value(stack);
        if (value <= 0) {
            String id = extra.getStringOr("id", "");
            if (!id.isEmpty()) PriceProvider.warm(id);
            return cfg.salvageMarkUnknown ? Mark.SALVAGE_UNKNOWN : Mark.NONE;
        }
        return value < Math.max(0, cfg.salvageMaxValue) ? Mark.SALVAGE_SAFE : Mark.NONE;
    }

    private static boolean hasSalvageLore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) if (SALVAGEABLE.matcher(line.getString()).find()) return true;
        return false;
    }

    private static boolean isSellable(ItemStack stack) {
        String id = extra(stack).getStringOr("id", "");
        return SELLABLE_IDS.contains(id)
            || id.equals("POTION") && stack.getHoverName().getString().contains(HEALING_POTION);
    }

    private static boolean isModified(CompoundTag extra) {
        return extra.getIntOr("upgrade_level", 0) > 0
            || extra.getIntOr("dungeon_item_level", 0) > 0
            || extra.getIntOr("rarity_upgrades", 0) > 0
            || extra.getIntOr("hot_potato_count", 0) > 0
            || !extra.getStringOr("modifier", "").isEmpty()
            || !extra.getCompoundOrEmpty("enchantments").isEmpty()
            || !extra.getCompoundOrEmpty("ultimate_enchantments").isEmpty()
            || !extra.getCompoundOrEmpty("gems").isEmpty()
            || hasCollectibleOrUpgradeMetadata(extra);
    }

    private static boolean hasCollectibleOrUpgradeMetadata(CompoundTag extra) {
        for (String key : extra.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("skin") || lower.contains("dye") || lower.contains("rune")
                || lower.contains("attribute") || lower.contains("edition") || lower.contains("shiny")
                || lower.contains("ability_scroll") || lower.contains("art_of_war") || lower.contains("art_of_peace")
                || lower.contains("ethermerge") || lower.contains("wood_singularity")
                || lower.contains("farming_for_dummies") || lower.contains("mana_disintegrator")
                || lower.contains("jalapeno") || lower.contains("book_of_stats")
                || lower.contains("champion_combat_xp") || lower.contains("compact_blocks")
                || lower.contains("expertise_kills") || lower.contains("hecatomb_s_runs")) return true;
        }
        return false;
    }

    private static CompoundTag extra(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes");
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        int c = colour | 0xFF000000;
        graphics.fill(x, y, x + 16, y + 1, c);
        graphics.fill(x, y + 15, x + 16, y + 16, c);
        graphics.fill(x, y, x + 1, y + 16, c);
        graphics.fill(x + 15, y, x + 16, y + 16, c);
    }

    private static int status() {
        OrionConfig cfg = config();
        if (cfg == null) return 0;
        local("salvage " + onOff(cfg.salvageHelper) + ", limit " + coins(cfg.salvageMaxValue)
            + ", sellable loot " + onOff(cfg.sellableDungeonLoot));
        return 1;
    }

    private static int setLimit(int coins) {
        OrionConfig cfg = config();
        if (cfg == null) return 0;
        cfg.salvageMaxValue = coins;
        ConstellationClient.saveConfig();
        local("salvage limit set to " + coins(coins) + " coins");
        return 1;
    }

    private static OrionConfig config() {
        return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().orion;
    }

    private static String coins(double value) { return String.format(Locale.US, "%,.0f", value); }
    private static String onOff(boolean value) { return value ? "on" : "off"; }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§bDungeon Loot §8> §f" + text));
    }
}
