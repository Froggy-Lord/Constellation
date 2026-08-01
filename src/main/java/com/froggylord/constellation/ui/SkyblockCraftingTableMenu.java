package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.constellation.LyraTooltips;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/SkyblockCraftingTableScreenHandler.java
public final class SkyblockCraftingTableMenu extends ChestMenu {
    private static final int[] NORMAL_SLOTS = {10, 11, 12, 19, 20, 21, 23, 28, 29, 30};
    private static final int[] QUICK_CRAFT_SLOTS = {16, 25, 34};
    private static final int[] MIRRORVERSE_SLOTS = {11, 12, 13, 20, 21, 22, 24, 29, 30, 31};

    private final boolean mirrorverse;
    private final boolean quickCrafts;

    public SkyblockCraftingTableMenu(MenuType<?> type, int id, Inventory playerInventory,
                                     Container container, int rows, boolean mirrorverse, boolean quickCrafts) {
        super(type, id, playerInventory, container, rows);
        this.mirrorverse = mirrorverse;
        this.quickCrafts = !mirrorverse && quickCrafts;
        int[] active = mirrorverse ? MIRRORVERSE_SLOTS : NORMAL_SLOTS;
        for (int index = 0; index < rows * 9; index++) {
            Slot original = slots.get(index);
            Slot replacement;
            if (Arrays.binarySearch(active, index) >= 0
                || this.quickCrafts && Arrays.binarySearch(QUICK_CRAFT_SLOTS, index) >= 0) {
                int[] position = position(index);
                replacement = new MappedSlot(original.container, original.getContainerSlot(), position[0], position[1],
                    index == (mirrorverse ? 24 : 23));
            } else {
                replacement = new DisabledSlot(original.container, original.getContainerSlot());
            }
            replacement.index = index;
            slots.set(index, replacement);
        }
        int inventoryOffset = (rows - 4) * 18 + 19;
        for (int index = rows * 9; index < slots.size(); index++) {
            Slot original = slots.get(index);
            Slot replacement = new Slot(original.container, original.getContainerSlot(),
                original.x, original.y - inventoryOffset);
            replacement.index = index;
            slots.set(index, replacement);
        }
    }

    public static SkyblockCraftingTableMenu from(ChestMenu menu, Inventory inventory,
                                                  boolean mirrorverse, boolean quickCrafts) {
        return new SkyblockCraftingTableMenu(menu.getType(), menu.containerId, inventory,
            menu.getContainer(), menu.getRowCount(), mirrorverse, quickCrafts);
    }

    public boolean mirrorverse() { return mirrorverse; }
    public boolean quickCrafts() { return quickCrafts; }
    public int resultSlot() { return mirrorverse ? 24 : 23; }
    public int moreCraftsSlot() { return 26; }

    private int[] position(int slot) {
        if (mirrorverse) {
            if (slot == 24) return new int[]{124, 35};
            int gridX = slot % 9 - 2;
            int gridY = slot / 9 - 1;
            return new int[]{30 + gridX * 18, 17 + gridY * 18};
        }
        if (slot == 23) return new int[]{124, 35};
        if (slot == 16 || slot == 25 || slot == 34) {
            int row = slot / 9 - 1;
            return new int[]{152, row * 18 + 8};
        }
        int gridX = slot % 9 - 1;
        int gridY = slot / 9 - 1;
        return new int[]{30 + gridX * 18, 17 + gridY * 18};
    }

    public static final class DisabledSlot extends Slot {
        private DisabledSlot(Container container, int index) { super(container, index, -20, -20); }
        @Override public boolean isActive() { return false; }
    }

    private static final class MappedSlot extends Slot {
        private final boolean result;
        private MappedSlot(Container container, int index, int x, int y, boolean result) {
            super(container, index, x, y);
            this.result = result;
        }
        @Override public boolean isActive() {
            if (!hideFillers()) return true;
            ItemStack stack = getItem();
            if (result && stack.is(Items.BARRIER)) return false;
            return !stack.is(Blocks.STAINED_GLASS_PANE.gray().asItem()) || !LyraTooltips.marketId(stack).isEmpty();
        }
    }

    private static boolean hideFillers() {
        try { return ConstellationClient.cfg().visual.skyblockCraftingTableHideFillers; }
        catch (RuntimeException ignored) { return false; }
    }
}
