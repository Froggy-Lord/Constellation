package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.constellation.LyraStorageValue;
import com.froggylord.constellation.constellation.LyraStorageValue.StoragePage;
import com.froggylord.constellation.constellation.LyraTooltips;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ported from Enhanced Storage (GPL-3.0): screen/StorageContainerScreen.java, gui/StorageOverlayLayout.java, and gui/component/PageCardComponent.java
public final class LyraStorageBrowserScreen extends Screen {
    private static String retainedSearch = "";
    private static double retainedScroll;
    private final Screen parent;
    private EditBox search;
    private EditBox rename;
    private String editing = "";
    private String renameDraft = "";
    private double scroll;
    private ItemStack hovered = ItemStack.EMPTY;
    private int contentHeight;

    public LyraStorageBrowserScreen(Screen parent) {
        super(Component.literal("Storage Browser"));
        this.parent = parent instanceof AbstractContainerScreen<?> ? null : parent;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        LyraConfig cfg = config();
        search = new EditBox(font, 12, 32, Math.max(40, Math.min(220, width - 116)), 18, Component.literal("Search storage"));
        search.setHint(Component.literal("search every cached page"));
        search.setMaxLength(80);
        search.setValue(cfg.storageBrowserRetainSearch ? retainedSearch : "");
        search.setResponder(value -> { retainedSearch = value; scroll = 0; });
        addRenderableWidget(search);
        rename = new EditBox(font, Math.max(12, width / 2 - 110), height / 2 - 5, Math.min(220, width - 24), 18, Component.literal("Storage name"));
        rename.setHint(Component.literal("storage name"));
        rename.setMaxLength(32);
        rename.visible = !editing.isBlank();
        addRenderableWidget(rename);
        if (!editing.isBlank()) {
            rename.setValue(renameDraft);
            rename.setFocused(true);
            setFocused(rename);
        } else {
            search.setFocused(true);
            setFocused(search);
        }
        scroll = cfg.storageBrowserRetainScroll ? retainedScroll : 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        LyraConfig cfg = config();
        ConstellationUi.background(graphics, width, height, delta);
        graphics.fill(0, 27, width, height, cfg.storageBrowserBackground);
        List<StoragePage> pages = visiblePages();
        int allPages = LyraStorageValue.storagePages().size();
        String detail = query().isBlank() ? allPages + " cached pages" : pages.size() + " of " + allPages + " pages";
        ConstellationUi.header(graphics, font, "Storage Browser", detail, width);
        ConstellationTheme.search(graphics, search.getX() - 2, search.getY() - 2, search.getWidth() + 4, search.getHeight() + 4, search.isFocused());
        ConstellationUi.button(graphics, font, width - 92, 32, 80, 18, "Done", inside(mouseX, mouseY, width - 92, 32, 80, 18), false);

        int top = 58;
        int bottom = height - 10;
        int columns = columns();
        int cardWidth = cardWidth(columns);
        int cardHeight = cardHeight();
        int gap = 8;
        int gridWidth = columns * cardWidth + (columns - 1) * gap;
        int startX = Math.max(8, (width - gridWidth) / 2);
        int rows = (pages.size() + columns - 1) / columns;
        contentHeight = rows * (cardHeight + gap) - (rows == 0 ? 0 : gap);
        clampScroll(bottom - top);
        hovered = ItemStack.EMPTY;
        graphics.enableScissor(0, top, width, bottom);
        for (int i = 0; i < pages.size(); i++) {
            int x = startX + (i % columns) * (cardWidth + gap);
            int y = top + (i / columns) * (cardHeight + gap) - (int) scroll;
            if (y + cardHeight >= top && y < bottom) drawCard(graphics, pages.get(i), x, y, cardWidth, cardHeight, mouseX, mouseY);
        }
        graphics.disableScissor();
        ConstellationUi.scrollbar(graphics, width - 6, top, bottom - top, bottom - top, contentHeight, (int) scroll);
        if (pages.isEmpty()) {
            String text = LyraStorageValue.storagePages().isEmpty() ? "Open storage pages once to cache them." : "No cached items match this search.";
            graphics.text(font, text, (width - font.width(text)) / 2, Math.max(top + 20, height / 2), ConstellationTheme.TEXT_MUTED, false);
        }
        if (!hovered.isEmpty() && cfg.storageBrowserShowTooltips && editing.isBlank())
            graphics.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Minecraft.getInstance(), hovered), mouseX, mouseY);
        if (!editing.isBlank()) drawRename(graphics, mouseX, mouseY);
    }

    private void drawCard(GuiGraphicsExtractor graphics, StoragePage page, int x, int y, int width, int height, int mouseX, int mouseY) {
        LyraConfig cfg = config();
        boolean over = inside(mouseX, mouseY, x, y, width, height);
        ConstellationTheme.surface(graphics, x, y, width, height, over ? lighten(cfg.storageBrowserCardBackground) : cfg.storageBrowserCardBackground,
            over ? cfg.storageBrowserActiveColor : ConstellationTheme.BORDER_SOFT);
        graphics.fill(x, y, x + width, y + 2, cfg.storageBrowserActiveColor);
        graphics.text(font, ConstellationUi.fit(font, page.name(), width - 65), x + 8, y + 8, ConstellationTheme.TEXT, false);
        String type = page.index() < 9 ? "EC " + (page.index() + 1) : "BP " + (page.index() - 8);
        graphics.text(font, type, x + width - font.width(type) - 8, y + 8, ConstellationTheme.TEXT_MUTED, false);
        smallButton(graphics, x + width - 57, y + 22, 16, "<", mouseX, mouseY);
        smallButton(graphics, x + width - 38, y + 22, 16, ">", mouseX, mouseY);
        smallButton(graphics, x + width - 19, y + 22, 16, "E", mouseX, mouseY);

        String query = query();
        List<ItemStack> allItems = contentItems(page);
        List<ItemStack> items = query.isBlank() ? allItems : allItems.stream().filter(stack -> matches(stack, query)).toList();
        int shown = Math.min(items.size(), config().storageBrowserRowsPerCard * 9);
        for (int i = 0; i < shown; i++) {
            int slotX = x + 7 + (i % 9) * 18;
            int slotY = y + 39 + (i / 9) * 18;
            if (slotX + 18 > x + width - 3) continue;
            ItemStack stack = items.get(i);
            boolean slotOver = inside(mouseX, mouseY, slotX, slotY, 18, 18);
            boolean match = query.isBlank() || matches(stack, query);
            int slotColor = slotOver ? 0xB0554168 : 0x8011101B;
            if (!query.isBlank() && match) slotColor = cfg.storageBrowserMatchColor;
            else if (!query.isBlank() && cfg.storageBrowserDimUnmatched) slotColor = 0x40101018;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, slotColor);
            if (stack.isEmpty()) continue;
            graphics.item(stack, slotX + 1, slotY + 1);
            if (cfg.storageBrowserShowDecorations) graphics.itemDecorations(font, stack, slotX + 1, slotY + 1);
            if (slotOver && slotY >= 58 && slotY + 18 <= height - 10) hovered = stack;
        }
        int nonEmpty = (int) allItems.stream().filter(stack -> !stack.isEmpty()).count();
        String count = query.isBlank() ? nonEmpty + " items" : items.size() + " matches";
        graphics.text(font, count, x + 8, y + height - 13, ConstellationTheme.TEXT_MUTED, false);
        graphics.text(font, "click to open", x + width - font.width("click to open") - 8, y + height - 13, ConstellationTheme.ACCENT_BRIGHT, false);
    }

    private void drawRename(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelWidth = Math.min(270, width - 24), panelHeight = 78;
        int x = (width - panelWidth) / 2, y = (height - panelHeight) / 2;
        graphics.fill(0, 27, width, height, 0xA0000000);
        ConstellationTheme.surface(graphics, x, y, panelWidth, panelHeight, 0xFF171522, ConstellationTheme.ACCENT_DIM);
        graphics.text(font, "Rename storage", x + 12, y + 10, ConstellationTheme.TEXT, false);
        graphics.text(font, "Leave blank to restore the default name.", x + 12, y + 23, ConstellationTheme.TEXT_MUTED, false);
        rename.setX(x + 12); rename.setY(y + 38); rename.setWidth(panelWidth - 104);
        ConstellationTheme.search(graphics, rename.getX() - 2, rename.getY() - 2, rename.getWidth() + 4, rename.getHeight() + 4, true);
        ConstellationUi.button(graphics, font, x + panelWidth - 86, y + 38, 34, 18, "Save", inside(mouseX, mouseY, x + panelWidth - 86, y + 38, 34, 18), false);
        ConstellationUi.button(graphics, font, x + panelWidth - 48, y + 38, 36, 18, "Cancel", inside(mouseX, mouseY, x + panelWidth - 48, y + 38, 36, 18), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mouseX = (int) event.x(), mouseY = (int) event.y();
        if (!editing.isBlank()) {
            int panelWidth = Math.min(270, width - 24), x = (width - panelWidth) / 2, y = (height - 78) / 2;
            if (inside(mouseX, mouseY, x + panelWidth - 86, y + 38, 34, 18)) return saveRename();
            if (inside(mouseX, mouseY, x + panelWidth - 48, y + 38, 36, 18)) { closeRename(); return true; }
            if (inside(mouseX, mouseY, rename.getX() - 2, rename.getY() - 2, rename.getWidth() + 4, rename.getHeight() + 4))
                return super.mouseClicked(event, doubled);
            return true;
        }
        if (inside(mouseX, mouseY, width - 92, 32, 80, 18)) { onClose(); return true; }
        StorageHit hit = hit(mouseX, mouseY);
        if (hit == null) return super.mouseClicked(event, doubled);
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT || hit.control == 3) { openRename(hit.page); return true; }
        if (hit.control == 1) { LyraStorageValue.moveStorage(hit.page.id(), -1); return true; }
        if (hit.control == 2) { LyraStorageValue.moveStorage(hit.page.id(), 1); return true; }
        LyraStorageValue.openStoragePage(hit.page);
        return true;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= vertical * config().storageBrowserScrollSpeed;
        clampScroll(height - 68);
        retainedScroll = scroll;
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (!editing.isBlank()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) return saveRename();
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) { closeRename(); return true; }
        } else if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (!search.getValue().isBlank()) search.setValue(""); else onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private List<StoragePage> visiblePages() {
        String query = query();
        return LyraStorageValue.storagePages().stream()
            .filter(page -> config().storageBrowserShowEmptyPages || contentItems(page).stream().anyMatch(stack -> !stack.isEmpty()))
            .filter(page -> query.isBlank() || page.name().toLowerCase(Locale.ROOT).contains(query)
                || contentItems(page).stream().anyMatch(stack -> matches(stack, query)))
            .toList();
    }

    private List<ItemStack> contentItems(StoragePage page) {
        int first = Math.min(9, page.items().size());
        return page.items().subList(first, page.items().size());
    }

    private boolean matches(ItemStack stack, String query) {
        if (stack.isEmpty()) return false;
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        String id = LyraTooltips.marketId(stack);
        if (id != null && id.toLowerCase(Locale.ROOT).contains(query)) return true;
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore != null && lore.lines().stream().anyMatch(line -> line.getString().toLowerCase(Locale.ROOT).contains(query));
    }

    private StorageHit hit(int mouseX, int mouseY) {
        List<StoragePage> pages = visiblePages();
        int columns = columns(), cardWidth = cardWidth(columns), cardHeight = cardHeight(), gap = 8;
        int startX = Math.max(8, (width - (columns * cardWidth + (columns - 1) * gap)) / 2);
        for (int i = 0; i < pages.size(); i++) {
            int x = startX + (i % columns) * (cardWidth + gap);
            int y = 58 + (i / columns) * (cardHeight + gap) - (int) scroll;
            if (mouseY < 58 || mouseY >= height - 10 || !inside(mouseX, mouseY, x, y, cardWidth, cardHeight)) continue;
            int control = inside(mouseX, mouseY, x + cardWidth - 57, y + 22, 16, 16) ? 1
                : inside(mouseX, mouseY, x + cardWidth - 38, y + 22, 16, 16) ? 2
                : inside(mouseX, mouseY, x + cardWidth - 19, y + 22, 16, 16) ? 3 : 0;
            return new StorageHit(pages.get(i), control);
        }
        return null;
    }

    private void openRename(StoragePage page) {
        editing = page.id();
        rename.visible = true;
        rename.setValue(page.name());
        renameDraft = page.name();
        rename.setFocused(true);
        setFocused(rename);
    }

    private boolean saveRename() { renameDraft = rename.getValue(); LyraStorageValue.renameStorage(editing, renameDraft); closeRename(); return true; }
    private void closeRename() { editing = ""; renameDraft = ""; rename.visible = false; rename.setFocused(false); search.setFocused(true); setFocused(search); }
    private void clampScroll(int viewport) { scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - Math.max(1, viewport))); retainedScroll = scroll; }
    private String query() { return search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT); }
    private int columns() { return Math.max(1, Math.min(Math.clamp(config().storageBrowserCardsPerRow, 1, 6), Math.max(1, (width - 8) / 180))); }
    private int cardWidth(int columns) { return Math.min(190, Math.max(Math.min(172, width - 16), (width - 16 - (columns - 1) * 8) / columns)); }
    private int cardHeight() { return 61 + Math.clamp(config().storageBrowserRowsPerCard, 1, 6) * 18; }
    private LyraConfig config() { return ConstellationClient.cfg().lyra; }
    private static int lighten(int color) { return (color & 0xFF000000) | Math.min(0xFFFFFF, (color & 0xFFFFFF) + 0x090909); }
    private void smallButton(GuiGraphicsExtractor graphics, int x, int y, int width, String label, int mouseX, int mouseY) { ConstellationUi.button(graphics, font, x, y, width, 16, label, inside(mouseX, mouseY, x, y, width, 16), false); }
    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }

    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
    private record StorageHit(StoragePage page, int control) {}
}
