package com.froggylord.constellation.compat.rei;

import com.froggylord.constellation.constellation.LyraRecipeRepository;
import com.froggylord.constellation.ui.LyraRecipeBrowserScreen;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/info/SkyblockInfoCategory.java
public final class LyraReiInfoCategory implements DisplayCategory<LyraReiInfoDisplay> {
    @Override public CategoryIdentifier<? extends LyraReiInfoDisplay> getCategoryIdentifier() { return LyraReiInfoDisplay.ID; }
    @Override public Component getTitle() { return Component.literal("SkyBlock Item Info"); }
    @Override public Renderer getIcon() { return EntryStacks.of(Items.CHEST); }
    @Override public int getDisplayHeight() { return 70; }
    @Override public List<Widget> setupDisplay(LyraReiInfoDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createSlot(new Point(bounds.getX() + 8, bounds.getY() + 8)).entry(EntryStacks.of(LyraRecipeRepository.stack(display.itemId(), 1))));
        var item = LyraRecipeRepository.item(display.itemId());
        widgets.add(Widgets.createLabel(new Point(bounds.getX() + 31, bounds.getY() + 10), Component.literal(item == null ? display.itemId() : item.name())).leftAligned());
        String counts = LyraRecipeRepository.recipes(display.itemId()).size() + " recipes  |  " + LyraRecipeRepository.usages(display.itemId()).size() + " usages";
        widgets.add(Widgets.createLabel(new Point(bounds.getX() + 31, bounds.getY() + 23), Component.literal(counts)).leftAligned());
        widgets.add(Widgets.createClickableLabel(new Point(bounds.getCenterX(), bounds.getY() + 49), Component.literal("Open Constellation browser"), label -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreenAndShow(new LyraRecipeBrowserScreen(minecraft.gui.screen(), item == null ? display.itemId() : item.name()));
        }));
        return widgets;
    }
}
