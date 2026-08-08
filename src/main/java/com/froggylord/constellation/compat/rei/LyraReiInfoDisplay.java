package com.froggylord.constellation.compat.rei;

import com.froggylord.constellation.constellation.LyraRecipeRepository;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/info/SkyblockInfoDisplay.java
public record LyraReiInfoDisplay(String itemId) implements Display {
    public static final CategoryIdentifier<LyraReiInfoDisplay> ID = CategoryIdentifier.of(Identifier.fromNamespaceAndPath("constellation", "skyblock_item_info"));
    @Override public List<EntryIngredient> getInputEntries() { return List.of(EntryIngredient.of(EntryStacks.of(LyraRecipeRepository.stack(itemId, 1)))); }
    @Override public List<EntryIngredient> getOutputEntries() { return getInputEntries(); }
    @Override public CategoryIdentifier<?> getCategoryIdentifier() { return ID; }
    @Override public Optional<Identifier> getDisplayLocation() { return Optional.empty(); }
    @Override public DisplaySerializer<? extends Display> getSerializer() { return null; }
}
