package com.froggylord.constellation.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext;
import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/SkyblockItemComparator.java
public final class LyraReiItemComparator implements EntryComparator<ItemStack> {
    @Override public long hash(ComparisonContext context, ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return EntryComparator.<ItemStack>noop().hash(context, stack);
        CompoundTag root = data.copyTag(), extra = root.getCompoundOrEmpty("ExtraAttributes");
        String id = (extra.isEmpty() ? root : extra).getStringOr("id", "");
        return id.isBlank() ? EntryComparator.<ItemStack>noop().hash(context, stack) : id.hashCode();
    }
}
