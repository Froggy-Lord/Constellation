package com.froggylord.constellation.ui;

import com.froggylord.constellation.constellation.LyraMarketSearch;
import com.froggylord.constellation.constellation.LyraMarketSearch.Suggestion;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/searchoverlay/OverlayScreen.java
public final class LyraMarketSearchScreen extends Screen {
    private EditBox search;
    private List<Suggestion> suggestions = List.of();
    private List<String> history = List.of();
    private ItemStack hovered = ItemStack.EMPTY;
    private boolean submitted;
    private double scroll;
    private int listTop;
    private int listBottom;
    private int contentHeight;
    private long revision;
    private final long transactionId;

    public LyraMarketSearchScreen() { super(Component.literal("Market Search")); transactionId = LyraMarketSearch.transactionId(); }
    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        int panelWidth = panelWidth();
        int x = (width - panelWidth) / 2;
        search = new EditBox(font, x + 12, panelTop() + 39, Math.max(40, panelWidth - 72), 20, Component.literal("Search SkyBlock items"));
        search.setHint(Component.literal("type an item name"));
        search.setMaxLength(30);
        search.setValue(LyraMarketSearch.search());
        search.setResponder(value -> { scroll = 0; LyraMarketSearch.select(value); refresh(); });
        addRenderableWidget(search);
        search.setFocused(true);
        setFocused(search);
        refresh();
        revision = LyraMarketSearch.revision();
    }

    @Override public void tick() {
        super.tick();
        if (revision != LyraMarketSearch.revision()) { revision = LyraMarketSearch.revision(); refresh(); }
    }

    private void refresh() {
        suggestions = LyraMarketSearch.suggestions();
        history = LyraMarketSearch.config().marketSearchHistory ? LyraMarketSearch.history() : List.of();
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ConstellationUi.background(graphics, width, height, delta);
        int panelWidth = panelWidth(), panelHeight = panelHeight(), x = (width - panelWidth) / 2, y = panelTop();
        graphics.fill(0, 0, width, height, 0x70000000);
        ConstellationTheme.surface(graphics, x, y, panelWidth, panelHeight, LyraMarketSearch.config().marketSearchPanelColor, ConstellationTheme.BORDER_SOFT);
        graphics.fill(x, y, x + panelWidth, y + 2, ConstellationTheme.ACCENT);
        String title = switch (LyraMarketSearch.location()) { case AUCTION -> "Auction House Search"; case BAZAAR -> "Bazaar Search"; case MUSEUM -> "Museum Search"; default -> "SkyBlock Search"; };
        graphics.text(font, title, x + 12, y + 12, ConstellationTheme.ACCENT_BRIGHT, false);
        String state = LyraMarketSearch.loading() ? "loading market data" : !LyraMarketSearch.loadError().isBlank() ? LyraMarketSearch.loadError() : LyraMarketSearch.catalogueSize() + " known items";
        if (font.width(title) + font.width(state) + 36 < panelWidth) graphics.text(font, state, x + panelWidth - font.width(state) - 12, y + 12, ConstellationTheme.TEXT_MUTED, false);
        ConstellationTheme.search(graphics, search.getX() - 2, search.getY() - 2, search.getWidth() + 4, search.getHeight() + 4, search.isFocused());
        button(graphics, x + panelWidth - 52, y + 39, 40, "Go", mouseX, mouseY, false);
        int rowY = y + 68;
        hovered = ItemStack.EMPTY;
        if (LyraMarketSearch.location() == LyraMarketSearch.Location.AUCTION) {
            if (LyraMarketSearch.config().marketSearchMaxPet) {
                button(graphics, x + 12, rowY, (panelWidth - 28) / 2, "Max pet: " + (LyraMarketSearch.maxPet() ? "on" : "off"), mouseX, mouseY, LyraMarketSearch.maxPet());
            }
            if (LyraMarketSearch.config().marketSearchDungeonStars) {
                int bx = x + panelWidth / 2 + 2;
                button(graphics, bx, rowY, panelWidth - (bx - x) - 12, "Dungeon stars: " + LyraMarketSearch.stars(), mouseX, mouseY, LyraMarketSearch.stars() > 0);
            }
            rowY += 27;
        }
        listTop = rowY;
        listBottom = y + panelHeight - 25;
        rowY -= (int) scroll;
        boolean listVisible = listBottom > listTop;
        if (!listVisible) {
            contentHeight = 0;
            scroll = 0;
            graphics.text(font, "Increase the window height to show results.", x + 12, Math.min(y + panelHeight - 31, listTop), ConstellationTheme.TEXT_MUTED, false);
            String footer = LyraMarketSearch.validationMessage().isBlank() ? "Enter searches  |  Escape cancels" : LyraMarketSearch.validationMessage();
            graphics.text(font, ConstellationUi.fit(font, footer, panelWidth - 24), x + 12, y + panelHeight - 18, LyraMarketSearch.validationMessage().isBlank() ? ConstellationTheme.TEXT_MUTED : 0xFFFF8A80, false);
            return;
        }
        if (listVisible) graphics.enableScissor(x + 5, listTop, x + panelWidth - 5, listBottom);
        if (!suggestions.isEmpty()) {
            graphics.text(font, "Suggestions", x + 12, rowY, ConstellationTheme.TEXT_MUTED, false);
            rowY += 14;
            for (Suggestion suggestion : suggestions) {
                drawRow(graphics, x + 12, rowY, panelWidth - 24, suggestion.name(), suggestion.icon(), false, mouseX, mouseY);
                rowY += 22;
            }
        }
        if (!history.isEmpty()) {
            rowY += suggestions.isEmpty() ? 0 : 5;
            graphics.text(font, "Recent searches", x + 12, rowY, ConstellationTheme.TEXT_MUTED, false);
            rowY += 14;
            for (String value : history) {
                drawRow(graphics, x + 12, rowY, panelWidth - 24, value, ItemStack.EMPTY, true, mouseX, mouseY);
                rowY += 22;
            }
        }
        if (suggestions.isEmpty() && history.isEmpty()) {
            String empty = LyraMarketSearch.search().isBlank() ? "Start typing to search the live SkyBlock item catalogue." : "No matching items yet. You can still search this text.";
            graphics.text(font, empty, x + 12, rowY + 6, ConstellationTheme.TEXT_MUTED, false);
        }
        contentHeight = rowY + (suggestions.isEmpty() && history.isEmpty() ? 30 : 0) - (listTop - (int) scroll);
        if (listVisible) graphics.disableScissor();
        clampScroll();
        ConstellationUi.scrollbar(graphics, x + panelWidth - 7, listTop, Math.max(1, listBottom - listTop),
            Math.max(1, listBottom - listTop), contentHeight, (int) scroll);
        String footer = LyraMarketSearch.validationMessage().isBlank() ? "Enter searches  |  Escape cancels" : LyraMarketSearch.validationMessage();
        graphics.text(font, ConstellationUi.fit(font, footer, panelWidth - 24), x + 12, y + panelHeight - 18, LyraMarketSearch.validationMessage().isBlank() ? ConstellationTheme.TEXT_MUTED : 0xFFFF8A80, false);
        if (!hovered.isEmpty() && LyraMarketSearch.config().marketSearchItemIcons)
            graphics.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Minecraft.getInstance(), hovered), mouseX, mouseY);
    }

    private void drawRow(GuiGraphicsExtractor graphics, int x, int y, int width, String text, ItemStack icon, boolean deletable, int mouseX, int mouseY) {
        boolean hover = mouseY >= listTop && mouseY < listBottom && inside(mouseX, mouseY, x, y, width, 20);
        ConstellationTheme.surface(graphics, x, y, width, 20, hover ? LyraMarketSearch.config().marketSearchSelectedColor : LyraMarketSearch.config().marketSearchRowColor,
            hover ? ConstellationTheme.ACCENT_DIM : ConstellationTheme.BORDER_SOFT);
        int textX = x + 7;
        if (!icon.isEmpty() && LyraMarketSearch.config().marketSearchItemIcons) {
            graphics.item(icon, x + 2, y + 2);
            textX = x + 22;
            if (mouseY >= listTop && mouseY < listBottom && inside(mouseX, mouseY, x + 2, y + 2, 16, 16)) hovered = icon;
        }
        graphics.text(font, ConstellationUi.fit(font, text, width - (textX - x) - (deletable ? 28 : 8)), textX, y + 6, ConstellationTheme.TEXT, false);
        if (deletable) {
            boolean deleteHover = inside(mouseX, mouseY, x + width - 24, y + 2, 20, 16);
            ConstellationUi.button(graphics, font, x + width - 24, y + 2, 20, 16, "x", deleteHover, false);
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mouseX = (int) event.x(), mouseY = (int) event.y();
        int panelWidth = panelWidth(), panelHeight = panelHeight(), x = (width - panelWidth) / 2, y = panelTop();
        if (inside(mouseX, mouseY, x + panelWidth - 52, y + 39, 40, 20)) return submit();
        int rowY = y + 68;
        if (LyraMarketSearch.location() == LyraMarketSearch.Location.AUCTION) {
            int half = (panelWidth - 28) / 2;
            if (LyraMarketSearch.config().marketSearchMaxPet && inside(mouseX, mouseY, x + 12, rowY, half, 20)) { LyraMarketSearch.toggleMaxPet(); return true; }
            int bx = x + panelWidth / 2 + 2;
            if (LyraMarketSearch.config().marketSearchDungeonStars && inside(mouseX, mouseY, bx, rowY, panelWidth - (bx - x) - 12, 20)) { LyraMarketSearch.cycleStars(event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1); return true; }
            rowY += 27;
        }
        int clickTop = rowY;
        rowY -= (int) scroll;
        if (!suggestions.isEmpty()) {
            rowY += 14;
            for (Suggestion suggestion : suggestions) {
                if (mouseY >= clickTop && mouseY < y + panelHeight - 25 && inside(mouseX, mouseY, x + 12, rowY, panelWidth - 24, 20)) { LyraMarketSearch.select(suggestion.name()); search.setValue(suggestion.name()); return submit(); }
                rowY += 22;
            }
        }
        if (!history.isEmpty()) {
            rowY += suggestions.isEmpty() ? 14 : 19;
            for (int i = 0; i < history.size(); i++) {
                if (mouseY >= clickTop && mouseY < y + panelHeight - 25 && inside(mouseX, mouseY, x + panelWidth - 36, rowY + 2, 20, 16)) { LyraMarketSearch.removeHistory(i); refresh(); return true; }
                if (mouseY >= clickTop && mouseY < y + panelHeight - 25 && inside(mouseX, mouseY, x + 12, rowY, panelWidth - 24, 20)) { String value = history.get(i); LyraMarketSearch.select(value); search.setValue(value); return submit(); }
                rowY += 22;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int panelWidth = panelWidth(), x = (width - panelWidth) / 2;
        if (mouseX < x || mouseX >= x + panelWidth || mouseY < listTop || mouseY >= listBottom) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        scroll -= vertical * 22;
        clampScroll();
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) return submit();
        if (event.isEscape()) { onClose(); return true; }
        if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN) {
            if (!suggestions.isEmpty()) {
                String value = event.key() == GLFW.GLFW_KEY_UP ? suggestions.getLast().name() : suggestions.getFirst().name();
                LyraMarketSearch.select(value); search.setValue(value); search.moveCursorToEnd(false); return true;
            }
        }
        return super.keyPressed(event);
    }

    private boolean submit() { if (submitted || !LyraMarketSearch.canSubmit()) return true; if (!LyraMarketSearch.submit(transactionId)) return true; submitted = true; Minecraft.getInstance().setScreenAndShow(null); return true; }
    private void cancelOnce() { if (!submitted) { submitted = true; LyraMarketSearch.cancel(transactionId); } }
    @Override public void onClose() { cancelOnce(); super.onClose(); }
    @Override public void removed() { cancelOnce(); super.removed(); }
    private void clampScroll() { scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - Math.max(1, listBottom - listTop))); }
    private int panelWidth() { return Math.max(1, Math.min(430, width - 16)); }
    private int panelHeight() { int rows = LyraMarketSearch.config().marketSearchMaxSuggestions + (LyraMarketSearch.config().marketSearchHistory ? LyraMarketSearch.config().marketSearchHistoryLength : 0); return Math.max(1, Math.min(height - 16, 116 + rows * 22 + (LyraMarketSearch.location() == LyraMarketSearch.Location.AUCTION ? 27 : 0))); }
    private int panelTop() { return Math.max(8, (height - panelHeight()) / 2); }
    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, String label, int mouseX, int mouseY, boolean active) { ConstellationUi.button(graphics, font, x, y, width, 20, label, inside(mouseX, mouseY, x, y, width, 20), active); }
    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }
}
