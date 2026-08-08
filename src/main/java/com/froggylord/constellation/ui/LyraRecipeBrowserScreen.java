package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.constellation.LyraRecipeRepository;
import com.froggylord.constellation.constellation.LyraRecipeRepository.Ingredient;
import com.froggylord.constellation.constellation.LyraRecipeRepository.Item;
import com.froggylord.constellation.constellation.LyraRecipeRepository.RecipeView;
import com.froggylord.constellation.constellation.LyraRecipeRepository.Filter;
import com.froggylord.constellation.render.ConstellationTheme;
import io.github.moulberry.repo.data.NEURecipe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/itemlist/recipebook/SkyblockRecipeBookComponent.java, SkyblockRecipeResults.java
public final class LyraRecipeBrowserScreen extends Screen {
    private final Screen parent;
    private final String initialQuery;
    private EditBox search;
    private List<Item> results = List.of();
    private Item selected;
    private List<NEURecipe> recipes = List.of();
    private boolean usages;
    private boolean info;
    private int recipeIndex;
    private int listTop;
    private int resultOffset;
    private int detailScroll;
    private int detailContentHeight;
    private int detailViewportHeight;
    private int detailTop;
    private int detailBottom;
    private Filter filter = Filter.ALL;
    private long repositoryRevision;
    private final List<Link> links = new ArrayList<>();
    private ItemStack hovered = ItemStack.EMPTY;

    public LyraRecipeBrowserScreen(Screen parent, String query) {
        super(Component.literal("SkyBlock Recipes"));
        this.parent = parent;
        this.initialQuery = query == null ? "" : query;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        boolean firstInit = search == null;
        String retainedSearch = firstInit ? "" : search.getValue();
        boolean retainedFocus = !firstInit && search.isFocused();
        int retainedOffset = resultOffset;
        int retainedDetailScroll = detailScroll;
        LyraRecipeRepository.init();
        int x = panelX();
        search = new EditBox(font, x + 13, panelY() + 37, Math.min(245, Math.max(120, panelWidth() - 120)), 20, Component.literal("Search SkyBlock items"));
        search.setHint(Component.literal("item name or SkyBlock ID"));
        search.setMaxLength(80);
        String remembered = ConstellationClient.cfg().lyra.recipeBrowserRememberSearch ? ConstellationClient.cfg().lyra.recipeBrowserLastSearch : "";
        search.setValue(firstInit ? initialQuery.isBlank() ? remembered : initialQuery : retainedSearch);
        search.setResponder(value -> { resultOffset = 0; refresh(); });
        addRenderableWidget(search);
        search.setFocused(firstInit || retainedFocus);
        if (firstInit || retainedFocus) setFocused(search);
        refresh();
        resultOffset = retainedOffset;
        detailScroll = retainedDetailScroll;
        repositoryRevision = LyraRecipeRepository.revision();
        if (firstInit && ConstellationClient.cfg().lyra.recipeBrowserRememberSelection && !ConstellationClient.cfg().lyra.recipeBrowserLastItem.isBlank()) {
            Item rememberedItem = LyraRecipeRepository.item(ConstellationClient.cfg().lyra.recipeBrowserLastItem);
            if (rememberedItem != null) select(rememberedItem);
        }
    }

    @Override public void tick() {
        super.tick();
        if (repositoryRevision != LyraRecipeRepository.revision()) {
            repositoryRevision = LyraRecipeRepository.revision();
            refresh();
            if (selected != null) loadRecipes();
        }
    }

    private void refresh() {
        results = LyraRecipeRepository.search(search == null ? initialQuery : search.getValue(), filter, Math.clamp(ConstellationClient.cfg().lyra.recipeBrowserResultLimit, 8, 200));
        resultOffset = Math.clamp(resultOffset, 0, Math.max(0, results.size() - visibleRows()));
        if (results.isEmpty()) { selected = null; recipes = List.of(); }
        else if (selected == null || results.stream().noneMatch(item -> item.id().equals(selected.id()))) select(results.getFirst());
    }

    private void select(Item item) {
        selected = item;
        if (ConstellationClient.cfg().lyra.recipeBrowserRememberSelection) { ConstellationClient.cfg().lyra.recipeBrowserLastItem = item.id(); ConstellationClient.saveConfig(); }
        recipeIndex = 0;
        detailScroll = 0;
        loadRecipes();
    }

    private void loadRecipes() {
        recipes = selected == null || info ? List.of() : usages ? LyraRecipeRepository.usages(selected.id()) : LyraRecipeRepository.recipes(selected.id());
        recipeIndex = Math.clamp(recipeIndex, 0, Math.max(0, recipes.size() - 1));
        detailScroll = 0;
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ConstellationUi.background(graphics, width, height, delta);
        graphics.fill(0, 0, width, height, 0x70000000);
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        ConstellationTheme.surface(graphics, x, y, w, h, ConstellationClient.cfg().lyra.recipeBrowserBackground, ConstellationTheme.BORDER_SOFT);
        graphics.fill(x, y, x + w, y + 2, ConstellationTheme.ACCENT);
        graphics.text(font, "SkyBlock Recipe Browser", x + 13, y + 13, ConstellationTheme.ACCENT_BRIGHT, false);
        String status = switch (LyraRecipeRepository.snapshot().state()) {
            case LOADING -> "loading item repository";
            case ERROR -> "repository unavailable";
            case READY -> LyraRecipeRepository.snapshot().items().size() + " items"
                    + (LyraRecipeRepository.snapshot().updating() ? "  |  updating" : !LyraRecipeRepository.snapshot().error().isBlank() ? "  |  update failed" : "");
        };
        graphics.text(font, status, x + w - 13 - font.width(status), y + 13, ConstellationTheme.TEXT_MUTED, false);
        ConstellationTheme.search(graphics, search.getX() - 2, search.getY() - 2, search.getWidth() + 4, search.getHeight() + 4, search.isFocused());
        int filterX = search.getX() + search.getWidth() + 8;
        ConstellationUi.button(graphics, font, filterX, search.getY(), Math.max(65, panelX() + panelWidth() - 13 - filterX), 20,
                filter.name().toLowerCase(Locale.ROOT), inside(mouseX, mouseY, filterX, search.getY(), Math.max(65, panelX() + panelWidth() - 13 - filterX), 20), filter != Filter.ALL);
        listTop = y + 68;
        int split = x + Math.min(270, Math.max(180, w * 2 / 5));
        links.clear();
        hovered = ItemStack.EMPTY;
        graphics.fill(split, listTop, split + 1, y + h - 25, ConstellationTheme.BORDER_SOFT);
        drawResults(graphics, x + 12, split - 12, mouseX, mouseY);
        drawRecipe(graphics, split + 13, x + w - 13, mouseX, mouseY);
        graphics.text(font, "R recipes  |  U usages  |  I info  |  Escape back", x + 13, y + h - 17, ConstellationTheme.TEXT_MUTED, false);
        if (!hovered.isEmpty() && ConstellationClient.cfg().lyra.recipeBrowserTooltips)
            graphics.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(minecraft, hovered), mouseX, mouseY);
    }

    private void drawResults(GuiGraphicsExtractor graphics, int left, int right, int mouseX, int mouseY) {
        int rowY = listTop;
        if (LyraRecipeRepository.snapshot().state() == LyraRecipeRepository.State.ERROR) {
            graphics.text(font, ConstellationUi.fit(font, LyraRecipeRepository.snapshot().error(), right - left), left, rowY, 0xFFFF8A80, false);
            ConstellationUi.button(graphics, font, left, rowY + 20, 82, 20, "Retry", inside(mouseX, mouseY, left, rowY + 20, 82, 20), false);
            return;
        }
        if (results.isEmpty()) {
            graphics.text(font, LyraRecipeRepository.snapshot().state() == LyraRecipeRepository.State.LOADING ? "Loading items..." : "No matching items", left, rowY + 5, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        int end = Math.min(results.size(), resultOffset + visibleRows());
        for (Item item : results.subList(Math.min(resultOffset, end), end)) {
            boolean active = selected != null && selected.id().equals(item.id());
            boolean hover = inside(mouseX, mouseY, left, rowY, right - left, 21);
            ConstellationTheme.surface(graphics, left, rowY, right - left, 21, active ? ConstellationClient.cfg().lyra.recipeBrowserSelectedColor : ConstellationClient.cfg().lyra.recipeBrowserRowColor, active ? ConstellationTheme.ACCENT_DIM : ConstellationTheme.BORDER_SOFT);
            int textX = left + 6;
            if (ConstellationClient.cfg().lyra.recipeBrowserItemIcons) {
                ItemStack stack = LyraRecipeRepository.stack(item.id(), 1);
                graphics.item(stack, left + 2, rowY + 2);
                textX = left + 21;
                if (inside(mouseX, mouseY, left + 2, rowY + 2, 16, 16)) hovered = stack;
            }
            graphics.text(font, ConstellationUi.fit(font, item.name(), right - textX - 6), textX, rowY + 4, active ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);
            graphics.text(font, ConstellationUi.fit(font, item.id(), right - textX - 6), textX, rowY + 12, ConstellationTheme.TEXT_MUTED, false);
            rowY += 23;
        }
    }

    private void drawRecipe(GuiGraphicsExtractor graphics, int left, int right, int mouseX, int mouseY) {
        if (selected == null) return;
        graphics.text(font, ConstellationUi.fit(font, selected.name(), right - left), left, listTop, ConstellationTheme.TEXT, false);
        int tabY = listTop + 18, tab = Math.max(45, (right - left - 10) / 3);
        ConstellationUi.button(graphics, font, left, tabY, tab, 20, "Recipes", inside(mouseX, mouseY, left, tabY, tab, 20), !usages && !info);
        ConstellationUi.button(graphics, font, left + tab + 5, tabY, tab, 20, "Usages", inside(mouseX, mouseY, left + tab + 5, tabY, tab, 20), usages && !info);
        ConstellationUi.button(graphics, font, left + (tab + 5) * 2, tabY, right - left - (tab + 5) * 2, 20, "Info", inside(mouseX, mouseY, left + (tab + 5) * 2, tabY, right - left - (tab + 5) * 2, 20), info);
        if (info) { drawInfo(graphics, left, right, tabY + 31); return; }
        if (recipes.isEmpty()) {
            graphics.text(font, usages ? "No known usages" : "No known recipe", left, tabY + 32, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        RecipeView view = LyraRecipeRepository.view(recipes.get(recipeIndex));
        int rowY = tabY + 31;
        String heading = view.type() + "  " + (recipeIndex + 1) + "/" + recipes.size();
        graphics.text(font, heading, left, rowY, ConstellationTheme.ACCENT_BRIGHT, false);
        if (!view.note().isBlank()) graphics.text(font, ConstellationUi.fit(font, view.note(), right - left), left, rowY + 12, ConstellationTheme.TEXT_MUTED, false);
        rowY += view.note().isBlank() ? 20 : 32;
        detailTop = rowY;
        detailBottom = panelY() + panelHeight() - 55;
        detailViewportHeight = Math.max(1, detailBottom - detailTop);
        graphics.enableScissor(left, detailTop, right, detailBottom);
        int contentY = rowY - detailScroll;
        contentY = drawIngredients(graphics, "Inputs", view.inputs(), left, right, contentY, mouseX, mouseY);
        contentY = drawIngredients(graphics, "Outputs", view.outputs(), left, right, contentY + 4, mouseX, mouseY);
        detailContentHeight = contentY - (rowY - detailScroll);
        graphics.disableScissor();
        detailScroll = Math.clamp(detailScroll, 0, Math.max(0, detailContentHeight - detailViewportHeight));
        ConstellationUi.scrollbar(graphics, right - 3, detailTop, detailViewportHeight, detailViewportHeight, Math.max(detailViewportHeight, detailContentHeight), detailScroll);
        if (recipes.size() > 1) {
            ConstellationUi.button(graphics, font, right - 93, panelY() + panelHeight() - 48, 42, 20, "<", inside(mouseX, mouseY, right - 93, panelY() + panelHeight() - 48, 42, 20), false);
            ConstellationUi.button(graphics, font, right - 46, panelY() + panelHeight() - 48, 42, 20, ">", inside(mouseX, mouseY, right - 46, panelY() + panelHeight() - 48, 42, 20), false);
        }
    }

    private void drawInfo(GuiGraphicsExtractor graphics, int left, int right, int top) {
        Item item = selected;
        if (item == null) return;
        detailTop = top;
        detailBottom = panelY() + panelHeight() - 28;
        detailViewportHeight = Math.max(1, detailBottom - detailTop);
        graphics.enableScissor(left, detailTop, right, detailBottom);
        int y = top - detailScroll;
        graphics.text(font, "SkyBlock ID", left, y, ConstellationTheme.TEXT_MUTED, false);
        graphics.text(font, ConstellationUi.fit(font, item.id(), right - left), left, y + 12, ConstellationTheme.TEXT, false);
        y += 31;
        graphics.text(font, "Item information", left, y, ConstellationTheme.TEXT_MUTED, false);
        y += 13;
        for (String line : item.lore()) {
            String clean = line.replaceAll("§.", "").trim();
            graphics.text(font, clean.isBlank() ? " " : ConstellationUi.fit(font, clean, right - left), left, y, clean.isBlank() ? ConstellationTheme.TEXT_MUTED : ConstellationTheme.TEXT, false);
            y += 12;
        }
        detailContentHeight = y - (top - detailScroll);
        graphics.disableScissor();
        detailScroll = Math.clamp(detailScroll, 0, Math.max(0, detailContentHeight - detailViewportHeight));
        ConstellationUi.scrollbar(graphics, right - 3, detailTop, detailViewportHeight, detailViewportHeight, Math.max(detailViewportHeight, detailContentHeight), detailScroll);
    }

    private int drawIngredients(GuiGraphicsExtractor graphics, String title, List<Ingredient> ingredients, int left, int right, int y, int mouseX, int mouseY) {
        graphics.text(font, title, left, y, ConstellationTheme.TEXT_MUTED, false);
        int rowY = y + 13;
        for (Ingredient ingredient : ingredients) {
            String amount = ingredient.amount() == 1 ? "" : " x" + NumberFormat.getNumberInstance(Locale.US).format(ingredient.amount());
            Item item = LyraRecipeRepository.item(ingredient.id());
            String label = (item == null ? ingredient.id() : item.name()) + amount;
            boolean hover = inside(mouseX, mouseY, left + 2, rowY - 2, right - left, 18);
            if (hover && ConstellationClient.cfg().lyra.recipeBrowserClickableChains) graphics.fill(left + 2, rowY - 2, right, rowY + 16, 0x503D527C);
            int textX = left + 6;
            if (ConstellationClient.cfg().lyra.recipeBrowserItemIcons) {
                ItemStack stack = LyraRecipeRepository.stack(ingredient.id(), ingredient.amount());
                graphics.item(stack, left + 2, rowY - 2);
                textX = left + 22;
                if (rowY + 16 >= detailTop && rowY - 2 < detailBottom && inside(mouseX, mouseY, left + 2, rowY - 2, 16, 16)) hovered = stack;
            }
            graphics.text(font, ConstellationUi.fit(font, label, right - textX), textX, rowY + 2, hover ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);
            if (item != null && rowY + 16 >= detailTop && rowY - 2 < detailBottom) links.add(new Link(left + 2, rowY - 2, right - left, 18, item));
            rowY += 19;
        }
        return rowY;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mx = (int) event.x(), my = (int) event.y(), x = panelX(), y = panelY(), w = panelWidth();
        int split = x + Math.min(270, Math.max(180, w * 2 / 5));
        int filterX = search.getX() + search.getWidth() + 8, filterWidth = Math.max(65, x + w - 13 - filterX);
        if (inside(mx, my, filterX, search.getY(), filterWidth, 20)) { filter = filter.next(); resultOffset = 0; refresh(); return true; }
        if (LyraRecipeRepository.snapshot().state() == LyraRecipeRepository.State.ERROR && inside(mx, my, x + 12, listTop + 20, 82, 20)) { LyraRecipeRepository.reload(ConstellationClient.cfg().lyra.recipeBrowserUpdateOnRequest); return true; }
        int rowY = listTop;
        int end = Math.min(results.size(), resultOffset + visibleRows());
        for (Item item : results.subList(Math.min(resultOffset, end), end)) { if (inside(mx, my, x + 12, rowY, split - x - 24, 21)) { select(item); return true; } rowY += 23; }
        int left = split + 13, right = x + w - 13, tabY = listTop + 18, tab = Math.max(45, (right - left - 10) / 3);
        if (inside(mx, my, left, tabY, tab, 20)) { usages = false; info = false; loadRecipes(); return true; }
        if (inside(mx, my, left + tab + 5, tabY, tab, 20)) { usages = true; info = false; loadRecipes(); return true; }
        if (inside(mx, my, left + (tab + 5) * 2, tabY, right - left - (tab + 5) * 2, 20)) { usages = false; info = true; loadRecipes(); return true; }
        if (recipes.size() > 1 && inside(mx, my, right - 93, y + panelHeight() - 48, 42, 20)) { recipeIndex = Math.floorMod(recipeIndex - 1, recipes.size()); return true; }
        if (recipes.size() > 1 && inside(mx, my, right - 46, y + panelHeight() - 48, 42, 20)) { recipeIndex = (recipeIndex + 1) % recipes.size(); return true; }
        if (ConstellationClient.cfg().lyra.recipeBrowserClickableChains) for (Link link : links) if (inside(mx, my, link.x(), link.y(), link.width(), link.height())) { select(link.item()); search.setValue(link.item().name()); return true; }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int x = panelX(), split = x + Math.min(270, Math.max(180, panelWidth() * 2 / 5));
        if (mouseX >= x + 12 && mouseX < split - 12 && mouseY >= listTop && mouseY < panelY() + panelHeight() - 25) {
            resultOffset = Math.clamp(resultOffset + (vertical < 0 ? 1 : -1), 0, Math.max(0, results.size() - visibleRows()));
            return true;
        }
        if (mouseX >= split + 13 && mouseX < x + panelWidth() - 13 && mouseY >= detailTop && mouseY < detailBottom) {
            detailScroll = Math.clamp(detailScroll + (vertical < 0 ? 19 : -19), 0, Math.max(0, detailContentHeight - detailViewportHeight));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_R && !search.isFocused()) { usages = false; info = false; loadRecipes(); return true; }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_U && !search.isFocused()) { usages = true; info = false; loadRecipes(); return true; }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_I && !search.isFocused()) { usages = false; info = true; loadRecipes(); return true; }
        return super.keyPressed(event);
    }

    @Override public void onClose() { saveSearch(); minecraft.setScreenAndShow(parent); }
    @Override public void removed() { saveSearch(); super.removed(); }
    private void saveSearch() {
        if (!ConstellationClient.cfg().lyra.recipeBrowserRememberSearch || search == null) return;
        String value = search.getValue();
        if (!value.equals(ConstellationClient.cfg().lyra.recipeBrowserLastSearch)) {
            ConstellationClient.cfg().lyra.recipeBrowserLastSearch = value;
            ConstellationClient.saveConfig();
        }
    }
    private int panelWidth() { return Math.max(340, Math.min(760, width - 16)); }
    private int panelHeight() { return Math.max(240, Math.min(440, height - 16)); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private int visibleRows() { return Math.clamp(ConstellationClient.cfg().lyra.recipeBrowserRows, 4, Math.max(4, (panelHeight() - 100) / 23)); }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private record Link(int x, int y, int width, int height, Item item) {}
}
