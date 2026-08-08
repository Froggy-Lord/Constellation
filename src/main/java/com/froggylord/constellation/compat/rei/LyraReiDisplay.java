package com.froggylord.constellation.compat.rei;

import com.froggylord.constellation.constellation.LyraRecipeRepository;
import com.froggylord.constellation.constellation.LyraRecipeRepository.RecipeView;
import io.github.moulberry.repo.data.NEUCraftingRecipe;
import io.github.moulberry.repo.data.NEUForgeRecipe;
import io.github.moulberry.repo.data.NEUKatUpgradeRecipe;
import io.github.moulberry.repo.data.NEUNpcShopRecipe;
import io.github.moulberry.repo.data.NEURecipe;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/recipe/SkyblockRecipeDisplay.java
public final class LyraReiDisplay implements Display {
    public static final CategoryIdentifier<LyraReiDisplay> CRAFTING = id("crafting");
    public static final CategoryIdentifier<LyraReiDisplay> FORGE = id("forge");
    public static final CategoryIdentifier<LyraReiDisplay> NPC_SHOP = id("npc_shop");
    public static final CategoryIdentifier<LyraReiDisplay> KAT = id("kat_upgrade");
    private final NEURecipe recipe;
    private final RecipeView view;
    private final CategoryIdentifier<LyraReiDisplay> category;

    public LyraReiDisplay(NEURecipe recipe) {
        this.recipe = recipe;
        this.view = LyraRecipeRepository.view(recipe);
        this.category = category(recipe);
    }

    private static CategoryIdentifier<LyraReiDisplay> id(String path) {
        return CategoryIdentifier.of(Identifier.fromNamespaceAndPath("constellation", "skyblock_" + path));
    }

    public static CategoryIdentifier<LyraReiDisplay> category(NEURecipe recipe) {
        if (recipe instanceof NEUCraftingRecipe) return CRAFTING;
        if (recipe instanceof NEUForgeRecipe) return FORGE;
        if (recipe instanceof NEUNpcShopRecipe) return NPC_SHOP;
        if (recipe instanceof NEUKatUpgradeRecipe) return KAT;
        return CRAFTING;
    }

    public NEURecipe recipe() { return recipe; }
    public RecipeView view() { return view; }

    @Override public List<EntryIngredient> getInputEntries() {
        return view.inputs().stream().map(value -> EntryIngredient.of(EntryStacks.of(LyraRecipeRepository.stack(value.id(), value.amount())))).toList();
    }

    @Override public List<EntryIngredient> getOutputEntries() {
        return view.outputs().stream().map(value -> EntryIngredient.of(EntryStacks.of(LyraRecipeRepository.stack(value.id(), value.amount())))).toList();
    }

    @Override public CategoryIdentifier<?> getCategoryIdentifier() { return category; }
    @Override public Optional<Identifier> getDisplayLocation() { return Optional.empty(); }
    @Override public DisplaySerializer<? extends Display> getSerializer() { return null; }
}
