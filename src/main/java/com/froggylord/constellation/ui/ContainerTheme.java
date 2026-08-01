package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.VisualConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

public final class ContainerTheme {
    private static final String[] PUZZLE_TITLES = {
        "click in order", "select all", "starts with", "change all to same color",
        "correct all the panes", "what starts with", "click the button on time", "navigate the maze", "melody", "harp", "chronomatron",
        "ultrasequencer", "superpairs", "fossil excavator", "hacking", "simon says", "spirit leap"
    };
    private static final String[] MARKET_TITLES = {
        "auction", "bazaar", "trades", "trade", "salvage", "museum"
    };

    private ContainerTheme() {}

    public static boolean playerInventory(InventoryScreen screen) {
        VisualConfig config = config();
        return config != null && config.enabled && config.playerInventoryTheme
            && screen.getClass() == InventoryScreen.class;
    }

    public static boolean basicContainer(AbstractContainerScreen<?> screen) {
        VisualConfig config = config();
        if (config == null || !config.enabled || !config.basicContainerTheme
            || screen.getClass() != ContainerScreen.class) return false;
        if (ConstellationClient.loc().onHypixel() && !config.basicContainersOnHypixel) return false;
        if (ConstellationClient.loc().inDungeons() && !config.basicContainersInDungeons) return false;
        String title = clean(screen.getTitle().getString());
        if (config.protectPuzzleContainers && contains(title, PUZZLE_TITLES)) return false;
        if (config.protectMarketContainers && contains(title, MARKET_TITLES)) return false;
        String denylist = config.containerTitleDenylist == null ? "" : config.containerTitleDenylist;
        for (String entry : denylist.split(";")) {
            String denied = clean(entry);
            if (!denied.isBlank() && title.contains(denied)) return false;
        }
        return true;
    }

    public static boolean craftingTable(CraftingScreen screen) {
        VisualConfig config = config();
        return config != null && config.enabled && config.craftingTableTheme
            && screen.getClass() == CraftingScreen.class
            && (!ConstellationClient.loc().onHypixel() || config.craftingTablesOnHypixel);
    }

    public static boolean furnace(AbstractFurnaceScreen<?> screen) {
        VisualConfig config = config();
        Class<?> type = screen.getClass();
        return config != null && config.enabled && config.furnaceTheme
            && (type == FurnaceScreen.class || type == BlastFurnaceScreen.class || type == SmokerScreen.class)
            && (!ConstellationClient.loc().onHypixel() || config.furnacesOnHypixel);
    }

    public static boolean brewingStand(BrewingStandScreen screen) {
        VisualConfig config = config();
        return config != null && config.enabled && config.brewingStandTheme
            && screen.getClass() == BrewingStandScreen.class
            && (!ConstellationClient.loc().onHypixel() || config.brewingStandsOnHypixel);
    }

    public static boolean enchantmentTable(EnchantmentScreen screen) {
        VisualConfig config = config();
        return config != null && config.enabled && config.enchantmentTableTheme
            && screen.getClass() == EnchantmentScreen.class
            && (!ConstellationClient.loc().onHypixel() || config.enchantmentTablesOnHypixel);
    }

    public static boolean anvil(AnvilScreen screen) {
        VisualConfig config = config();
        return config != null && config.enabled && config.anvilTheme
            && screen.getClass() == AnvilScreen.class
            && (!ConstellationClient.loc().onHypixel() || config.anvilsOnHypixel);
    }

    public static boolean grindstone(GrindstoneScreen screen) {
        VisualConfig config = config();
        return config != null && config.enabled && config.grindstoneTheme
            && screen.getClass() == GrindstoneScreen.class
            && (!ConstellationClient.loc().onHypixel() || config.grindstonesOnHypixel);
    }

    // ported from CryptKit (GPL-3.0-only): mixin/InventoryButtonsMixin.java
    public static void drawPlayerInventory(GuiGraphicsExtractor graphics, InventoryScreen screen,
                                           int x, int y, int width, int height) {
        VisualConfig config = config();
        if (config == null) return;
        panel(graphics, x, y, width, height, config);
        if (config.playerInventoryModelFrame) {
            int left = 25, top = 7, right = 76, bottom = 79;
            graphics.fill(x + left, y + top, x + right, y + bottom, config.inventoryModelColor);
            frame(graphics, x + left, y + top, right - left, bottom - top,
                config.inventoryAccentColor, config.inventoryBorderColor);
        }
        if (config.playerInventorySlotFrames) slots(graphics, screen, x, y, config);
    }

    // ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    public static void drawBasicContainer(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen) {
        if (!basicContainer(screen)) return;
        VisualConfig config = config();
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        panel(graphics, 0, 0, accessor.constellation$imageWidth(), accessor.constellation$imageHeight(), config);
        if (config.basicContainerSlotFrames) slots(graphics, screen, 0, 0, config);
    }

    // ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    public static void drawCraftingTable(GuiGraphicsExtractor graphics, CraftingScreen screen,
                                         RenderPipeline pipeline, Identifier texture,
                                         int x, int y, int width, int height,
                                         int textureWidth, int textureHeight) {
        VisualConfig config = config();
        if (config == null) return;
        panel(graphics, x, y, width, height, config);
        if (config.craftingTableArrow) {
            graphics.blit(pipeline, texture, x + 90, y + 35, 90f, 35f,
                22, 15, textureWidth, textureHeight);
        }
        if (config.craftingTableSlotFrames) slots(graphics, screen, x, y, config);
    }

    // ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    public static void drawFurnace(GuiGraphicsExtractor graphics, AbstractFurnaceScreen<?> screen,
                                   RenderPipeline pipeline, Identifier texture,
                                   int x, int y, int width, int height,
                                   int textureWidth, int textureHeight) {
        VisualConfig config = config();
        if (config == null) return;
        panel(graphics, x, y, width, height, config);
        if (config.furnaceIndicatorBackplates) {
            graphics.blit(pipeline, texture, x + 56, y + 36, 56f, 36f,
                14, 14, textureWidth, textureHeight);
            graphics.blit(pipeline, texture, x + 79, y + 34, 79f, 34f,
                24, 16, textureWidth, textureHeight);
        }
        if (config.furnaceSlotFrames) slots(graphics, screen, x, y, config);
    }

    // panel and slot pattern ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    public static void drawBrewingStand(GuiGraphicsExtractor graphics, BrewingStandScreen screen,
                                        int x, int y, int width, int height) {
        VisualConfig config = config();
        if (config == null) return;
        panel(graphics, x, y, width, height, config);
        if (config.brewingStandApparatus) brewingApparatus(graphics, x, y, config);
        if (config.brewingStandProgressBackplates) brewingProgressBackplates(graphics, x, y, config);
        if (config.brewingStandSlotFrames) slots(graphics, screen, x, y, config);
    }

    // panel and slot pattern ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    public static void drawEnchantmentTable(GuiGraphicsExtractor graphics, EnchantmentScreen screen,
                                            int x, int y, int width, int height) {
        VisualConfig config = config();
        if (config == null) return;
        panel(graphics, x, y, width, height, config);
        if (config.enchantmentTableBookStage) {
            graphics.fill(x + 8, y + 16, x + 57, y + 77, config.enchantmentTableBookStageColor);
            frame(graphics, x + 8, y + 16, 49, 61,
                config.inventoryAccentColor, config.inventoryBorderColor);
        }
        if (config.enchantmentTableSlotFrames) slots(graphics, screen, x, y, config);
    }

    // panel and slot pattern ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    public static void drawAnvil(GuiGraphicsExtractor graphics, AnvilScreen screen,
                                 RenderPipeline pipeline, Identifier texture,
                                 int x, int y, int width, int height,
                                 int textureWidth, int textureHeight) {
        VisualConfig config = config();
        if (config == null) return;
        panel(graphics, x, y, width, height, config);
        if (config.anvilWorkStage) {
            graphics.fill(x + 18, y + 41, x + 158, y + 72, config.anvilWorkStageColor);
            frame(graphics, x + 18, y + 41, 140, 31,
                config.inventoryAccentColor, config.inventoryBorderColor);
        }
        graphics.blit(pipeline, texture, x + 18, y + 6, 18f, 6f,
            28, 21, textureWidth, textureHeight);
        if (config.anvilOperationSymbols) {
            graphics.blit(pipeline, texture, x + 52, y + 49, 52f, 49f,
                12, 12, textureWidth, textureHeight);
            graphics.blit(pipeline, texture, x + 99, y + 45, 99f, 45f,
                28, 21, textureWidth, textureHeight);
        }
        if (config.anvilSlotFrames) slots(graphics, screen, x, y, config);
    }

    // field pattern ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    public static void drawAnvilNameField(GuiGraphicsExtractor graphics, AnvilScreen screen,
                                          int x, int y, int width, int height) {
        VisualConfig config = config();
        if (config == null) return;
        graphics.fill(x, y, x + width, y + height, config.anvilNameFieldColor);
        int top = screen.getMenu().getSlot(0).hasItem()
            ? config.inventoryAccentColor : config.inventoryBorderColor;
        frame(graphics, x, y, width, height, top, config.inventoryBorderColor);
    }

    // panel and slot pattern ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    public static void drawGrindstone(GuiGraphicsExtractor graphics, GrindstoneScreen screen,
                                      RenderPipeline pipeline, Identifier texture,
                                      int x, int y, int width, int height,
                                      int textureWidth, int textureHeight) {
        VisualConfig config = config();
        if (config == null) return;
        panel(graphics, x, y, width, height, config);
        if (config.grindstoneWorkStage) {
            graphics.fill(x + 20, y + 14, x + 158, y + 74, config.grindstoneWorkStageColor);
            frame(graphics, x + 20, y + 14, 138, 60,
                config.inventoryAccentColor, config.inventoryBorderColor);
        }
        if (config.grindstoneApparatus) {
            graphics.blit(pipeline, texture, x + 29, y + 16, 29f, 16f,
                54, 54, textureWidth, textureHeight);
        }
        if (config.grindstoneArrowBackplate) {
            graphics.blit(pipeline, texture, x + 92, y + 31, 92f, 31f,
                28, 21, textureWidth, textureHeight);
        }
        if (config.grindstoneSlotFrames) slots(graphics, screen, x, y, config);
    }

    public static int labelColor(AbstractContainerScreen<?> screen, int original) {
        VisualConfig config = config();
        if (config == null) return original;
        boolean themed = screen instanceof InventoryScreen inventory && playerInventory(inventory)
            || screen instanceof CraftingScreen crafting && craftingTable(crafting)
            || screen instanceof AbstractFurnaceScreen<?> furnace && furnace(furnace)
            || screen instanceof BrewingStandScreen brewing && brewingStand(brewing)
            || screen instanceof EnchantmentScreen enchantment && enchantmentTable(enchantment)
            || screen instanceof AnvilScreen anvil && anvil(anvil)
            || screen instanceof GrindstoneScreen grindstone && grindstone(grindstone)
            || basicContainer(screen);
        return themed ? config.inventoryLabelColor : original;
    }

    private static void brewingApparatus(GuiGraphicsExtractor graphics, int x, int y,
                                         VisualConfig config) {
        int edge = config.brewingStandApparatusColor;
        graphics.fill(x + 34, y + 24, x + 43, y + 27, edge);
        graphics.fill(x + 40, y + 24, x + 43, y + 37, edge);
        graphics.fill(x + 40, y + 34, x + 53, y + 37, edge);
        graphics.fill(x + 50, y + 34, x + 53, y + 47, edge);
        graphics.fill(x + 50, y + 44, x + 60, y + 47, edge);
        graphics.fill(x + 86, y + 34, x + 89, y + 49, edge);
        graphics.fill(x + 64, y + 47, x + 111, y + 50, edge);
        graphics.fill(x + 63, y + 47, x + 66, y + 52, edge);
        graphics.fill(x + 86, y + 47, x + 89, y + 59, edge);
        graphics.fill(x + 109, y + 47, x + 112, y + 52, edge);
        graphics.fill(x + 65, y + 36, x + 68, y + 39, edge);
        graphics.fill(x + 69, y + 29, x + 72, y + 32, edge);
        graphics.fill(x + 66, y + 22, x + 69, y + 25, edge);
        graphics.fill(x + 70, y + 16, x + 73, y + 19, edge);
    }

    private static void brewingProgressBackplates(GuiGraphicsExtractor graphics, int x, int y,
                                                   VisualConfig config) {
        int well = config.inventorySlotColor;
        int edge = config.brewingStandApparatusColor;
        graphics.fill(x + 59, y + 43, x + 79, y + 49, well);
        graphics.fill(x + 59, y + 43, x + 79, y + 44, edge);
        graphics.fill(x + 59, y + 43, x + 60, y + 49, edge);
        graphics.fill(x + 96, y + 15, x + 107, y + 45, well);
        graphics.fill(x + 96, y + 15, x + 107, y + 16, edge);
        graphics.fill(x + 96, y + 15, x + 97, y + 45, edge);
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              VisualConfig config) {
        graphics.fill(x, y, x + width, y + height, config.inventoryPanelColor);
        frame(graphics, x, y, width, height, config.inventoryAccentColor, config.inventoryBorderColor);
    }

    private static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int accent, int border) {
        graphics.fill(x, y, x + width, y + 1, accent);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    private static void slots(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                              int offsetX, int offsetY, VisualConfig config) {
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive() || slot.x < 0 || slot.y < 0
                || slot.x + 16 > accessor.constellation$imageWidth()
                || slot.y + 16 > accessor.constellation$imageHeight()) continue;
            int x = offsetX + slot.x - 1;
            int y = offsetY + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, config.inventorySlotColor);
            graphics.fill(x, y, x + 18, y + 1, config.inventorySlotEdgeColor);
            graphics.fill(x, y, x + 1, y + 18, config.inventorySlotEdgeColor);
        }
    }

    private static VisualConfig config() {
        try { return ConstellationClient.cfg().visual; }
        catch (RuntimeException ignored) { return null; }
    }

    private static boolean contains(String value, String[] needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
