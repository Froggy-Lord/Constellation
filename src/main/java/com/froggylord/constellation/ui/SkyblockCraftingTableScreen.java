package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.VisualConfig;
import com.froggylord.constellation.constellation.LyraTooltips;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Duration;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/SkyblockCraftingTableScreen.java
public final class SkyblockCraftingTableScreen extends AbstractContainerScreen<SkyblockCraftingTableMenu> {
    // ported from Skyblocker (LGPL-3.0-or-later): assets/skyblocker/textures/gui/sprites/quick_craft/more_button*.png
    private static final WidgetSprites MORE_CRAFTS = new WidgetSprites(
        sprite("more_button"), sprite("more_button_disabled"), sprite("more_button_highlighted"));
    private ImageButton moreCrafts;

    public SkyblockCraftingTableScreen(SkyblockCraftingTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        titleLabelX = 29;
    }

    @Override protected void init() {
        super.init();
        titleLabelX = 29;
        VisualConfig config = config();
        if (!menu.mirrorverse() && menu.quickCrafts() && config != null
            && config.skyblockCraftingTableMoreCrafts) {
            moreCrafts = new ImageButton(leftPos + 152, topPos + 63, 16, 16, MORE_CRAFTS,
                button -> clickMoreCrafts());
            moreCrafts.setTooltip(Tooltip.create(Component.literal("More Crafts")));
            moreCrafts.setTooltipDelay(Duration.ofMillis(250));
            addRenderableWidget(moreCrafts);
        }
    }

    @Override public void containerTick() {
        super.containerTick();
        if (moreCrafts == null) return;
        ItemStack stack = menu.getSlot(menu.moreCraftsSlot()).getItem();
        moreCrafts.active = stack.isEmpty() || stack.is(Items.PLAYER_HEAD);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        VisualConfig config = config();
        int x = leftPos;
        int y = topPos;
        ConstellationTheme.panel(graphics, x, y, imageWidth, imageHeight);
        if (config != null && config.skyblockCraftingTableGridStage) {
            ConstellationTheme.surface(graphics, x + 20, y + 14, 74, 59,
                config.skyblockCraftingTableGridColor, ConstellationTheme.BORDER_SOFT);
        }
        if (config != null && config.skyblockCraftingTableOutputStage) {
            ConstellationTheme.surface(graphics, x + 114, y + 24, 34, 40,
                config.skyblockCraftingTableOutputColor, ConstellationTheme.BORDER_SOFT);
            arrow(graphics, x + 96, y + 40);
        }
        if (config != null && menu.quickCrafts()) {
            ConstellationTheme.surface(graphics, x + 143, y + 5, 33, 78,
                config.skyblockCraftingTableQuickCraftColor, ConstellationTheme.BORDER_SOFT);
        }
        if (config != null && config.skyblockCraftingTableSlotFrames) drawSlots(graphics, config);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, getTitle(), titleLabelX, titleLabelY, ConstellationTheme.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
            ConstellationTheme.TEXT_DIM, false);
    }

    @Override protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        VisualConfig config = config();
        ItemStack stack = slot.getItem();
        if (config != null && config.skyblockCraftingTableHideFillers) {
            if (slot.index == menu.resultSlot() && stack.is(Items.BARRIER)) return;
            if (stack.is(Blocks.STAINED_GLASS_PANE.gray().asItem()) && LyraTooltips.marketId(stack).isEmpty()) return;
        }
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    @Override public boolean isPauseScreen() { return false; }

    private void clickMoreCrafts() {
        Slot slot = menu.getSlot(menu.moreCraftsSlot());
        slotClicked(slot, slot.index, 0, ContainerInput.PICKUP);
    }

    private void drawSlots(GuiGraphicsExtractor graphics, VisualConfig config) {
        for (Slot slot : menu.slots) {
            if (!slot.isActive() || slot.x < 0 || slot.y < 0 || slot.x + 16 > imageWidth || slot.y + 16 > imageHeight) continue;
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, config.inventorySlotColor);
            graphics.fill(x, y, x + 18, y + 1, config.inventorySlotEdgeColor);
            graphics.fill(x, y, x + 1, y + 18, config.inventorySlotEdgeColor);
        }
    }

    private static void arrow(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 12, y + 4, ConstellationTheme.ACCENT);
        graphics.fill(x + 9, y - 3, x + 13, y + 7, ConstellationTheme.BORDER);
        graphics.fill(x + 11, y - 1, x + 15, y + 5, ConstellationTheme.ACCENT);
    }

    private static VisualConfig config() {
        try { return ConstellationClient.cfg().visual; }
        catch (RuntimeException ignored) { return null; }
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath("constellation", "skyblock_crafting/" + name);
    }
}
