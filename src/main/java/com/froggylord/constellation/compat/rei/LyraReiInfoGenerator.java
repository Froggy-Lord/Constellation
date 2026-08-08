package com.froggylord.constellation.compat.rei;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.constellation.LyraRecipeRepository;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Optional;

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/info/SkyblockInfoDisplayGenerator.java
public final class LyraReiInfoGenerator implements DynamicDisplayGenerator<LyraReiInfoDisplay> {
    @Override public Optional<List<LyraReiInfoDisplay>> getRecipeFor(EntryStack<?> entry) { return display(entry); }
    @Override public Optional<List<LyraReiInfoDisplay>> getUsageFor(EntryStack<?> entry) { return display(entry); }
    private static Optional<List<LyraReiInfoDisplay>> display(EntryStack<?> entry) {
        if (!ConstellationClient.cfg().lyra.enabled || !ConstellationClient.cfg().lyra.recipeBrowser || !ConstellationClient.cfg().lyra.recipeBrowserReiIntegration
                || !(entry.getValue() instanceof ItemStack stack)) return Optional.empty();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        CompoundTag root = data.copyTag(), extra = root.getCompoundOrEmpty("ExtraAttributes");
        String id = (extra.isEmpty() ? root : extra).getStringOr("id", "");
        return id.isBlank() || LyraRecipeRepository.item(id) == null ? Optional.empty() : Optional.of(List.of(new LyraReiInfoDisplay(id)));
    }
}
