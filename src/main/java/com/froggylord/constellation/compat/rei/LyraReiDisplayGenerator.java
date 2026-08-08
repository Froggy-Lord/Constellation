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

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/recipe/SkyblockRecipeDisplayGenerator.java
public final class LyraReiDisplayGenerator implements DynamicDisplayGenerator<LyraReiDisplay> {
    @Override public Optional<List<LyraReiDisplay>> getRecipeFor(EntryStack<?> entry) {
        if (!ConstellationClient.cfg().lyra.enabled || !ConstellationClient.cfg().lyra.recipeBrowser || !ConstellationClient.cfg().lyra.recipeBrowserReiIntegration) return Optional.empty();
        String id = id(entry);
        return id.isBlank() ? Optional.empty() : Optional.of(LyraRecipeRepository.recipes(id).stream().map(LyraReiDisplay::new).toList());
    }

    @Override public Optional<List<LyraReiDisplay>> getUsageFor(EntryStack<?> entry) {
        if (!ConstellationClient.cfg().lyra.enabled || !ConstellationClient.cfg().lyra.recipeBrowser || !ConstellationClient.cfg().lyra.recipeBrowserReiIntegration) return Optional.empty();
        String id = id(entry);
        return id.isBlank() ? Optional.empty() : Optional.of(LyraRecipeRepository.usages(id).stream().map(LyraReiDisplay::new).toList());
    }

    private static String id(EntryStack<?> entry) {
        if (!(entry.getValue() instanceof ItemStack stack)) return "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        CompoundTag root = data.copyTag(), extra = root.getCompoundOrEmpty("ExtraAttributes");
        return (extra.isEmpty() ? root : extra).getStringOr("id", "");
    }
}
