package com.froggylord.constellation.compat.rei;

import com.froggylord.constellation.constellation.LyraRecipeRepository;
import com.froggylord.constellation.constellation.LyraRecipeRepository.Ingredient;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import io.github.moulberry.repo.data.NEUCraftingRecipe;
import io.github.moulberry.repo.data.NEUIngredient;

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/recipe/SkyblockRecipeCategory.java
public final class LyraReiCategory implements DisplayCategory<LyraReiDisplay> {
    private final CategoryIdentifier<LyraReiDisplay> id;
    private final Component title;
    private final Item icon;

    public LyraReiCategory(CategoryIdentifier<LyraReiDisplay> id, String title, Item icon) {
        this.id = id;
        this.title = Component.literal(title);
        this.icon = icon;
    }

    @Override public CategoryIdentifier<? extends LyraReiDisplay> getCategoryIdentifier() { return id; }
    @Override public Component getTitle() { return title; }
    @Override public Renderer getIcon() { return EntryStacks.of(icon); }
    @Override public int getDisplayHeight() { return 102; }

    @Override public List<Widget> setupDisplay(LyraReiDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        List<Ingredient> inputs = display.view().inputs();
        int startX = bounds.getX() + 9;
        int startY = bounds.getY() + 13;
        int shown = 0;
        if (display.recipe() instanceof NEUCraftingRecipe crafting) {
            NEUIngredient[] grid = crafting.getInputs();
            for (int index = 0; index < Math.min(9, grid.length); index++) {
                NEUIngredient value = grid[index];
                if (value == NEUIngredient.SENTINEL_EMPTY) continue;
                widgets.add(Widgets.createSlot(new Point(startX + index % 3 * 18, startY + index / 3 * 18))
                        .markInput().entry(EntryStacks.of(LyraRecipeRepository.stack(value.getItemId(), value.getAmount()))));
                shown++;
            }
        } else {
            int columns = Math.min(3, Math.max(1, inputs.size()));
            for (int index = 0; index < Math.min(12, inputs.size()); index++) {
                Ingredient value = inputs.get(index);
                widgets.add(Widgets.createSlot(new Point(startX + index % columns * 18, startY + index / columns * 18))
                        .markInput().entry(EntryStacks.of(LyraRecipeRepository.stack(value.id(), value.amount()))));
                shown++;
            }
        }
        int arrowX = bounds.getCenterX() + 2;
        widgets.add(Widgets.createArrow(new Point(arrowX - 12, bounds.getCenterY() - 9)));
        List<Ingredient> outputs = display.view().outputs();
        int shownOutputs = Math.min(4, outputs.size());
        for (int index = 0; index < shownOutputs; index++) {
            Ingredient value = outputs.get(index);
            widgets.add(Widgets.createSlot(new Point(bounds.getX() + bounds.getWidth() - 45 + index % 2 * 18,
                    bounds.getCenterY() - 9 + index / 2 * 18)).markOutput()
                    .entry(EntryStacks.of(LyraRecipeRepository.stack(value.id(), value.amount()))));
        }
        if (!display.view().note().isBlank())
            widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(), bounds.getY() + bounds.getHeight() - 10), Component.literal(display.view().note())));
        if (inputs.size() > shown)
            widgets.add(Widgets.createLabel(new Point(startX + 27, bounds.getY() + bounds.getHeight() - 10), Component.literal("+" + (inputs.size() - shown) + " inputs")));
        if (outputs.size() > shownOutputs)
            widgets.add(Widgets.createLabel(new Point(bounds.getX() + bounds.getWidth() - 28, bounds.getY() + bounds.getHeight() - 10), Component.literal("+" + (outputs.size() - shownOutputs) + " outputs")));
        return widgets;
    }
}
