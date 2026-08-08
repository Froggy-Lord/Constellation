package com.froggylord.constellation.ui;

import com.froggylord.constellation.constellation.PartyMessages;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PartyMessageScreen extends Screen {
    private enum Sort { NAME, CATEGORY, ENABLED }
    private enum Filter { ALL, ENABLED, DISABLED }

    private final Screen parent;
    private EditBox search;
    private EditBox template;
    private PartyMessages.Definition selected;
    private Sort sort = Sort.CATEGORY;
    private Filter filter = Filter.ALL;
    private int scroll;
    private int maxScroll;
    private boolean changingTemplate;

    public PartyMessageScreen(Screen parent) {
        super(Component.literal("Party Messages"));
        this.parent = parent;
    }

    // search/editor layout ported from Skyblocker (LGPL-3.0): utils/render/gui/SearchableGridWidget.java
    @Override
    protected void init() {
        String retainedSearch = search == null ? "" : search.getValue();
        boolean searchFocused = search != null && search.isFocused();
        boolean templateFocused = template != null && template.isFocused();
        int retainedScroll = scroll;
        search = new EditBox(font, 18, 28, Math.min(240, width / 3), 18, Component.literal("Search messages"));
        search.setMaxLength(64);
        search.setHint(Component.literal("Search name, category, variable"));
        search.setResponder(value -> scroll = 0);
        search.setValue(retainedSearch);
        addRenderableWidget(search);

        template = new EditBox(font, width / 2 + 10, height - 54, width / 2 - 28, 18, Component.literal("Message template"));
        template.setMaxLength(120);
        template.setHint(Component.literal("Select a message to edit"));
        template.setResponder(value -> {
            if (!changingTemplate && selected != null) PartyMessages.setTemplate(selected.id(), value);
        });
        if (selected != null) {
            changingTemplate = true;
            template.setValue(PartyMessages.template(selected.id()));
            changingTemplate = false;
        }
        addRenderableWidget(template);
        scroll = retainedScroll;
        if (templateFocused) { template.setFocused(true); setFocused(template); }
        else if (searchFocused) { search.setFocused(true); setFocused(search); }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        String summary = enabledCount() + "/" + PartyMessages.definitions().size() + " enabled";
        ConstellationUi.background(g, width, height, delta);
        ConstellationUi.header(g, font, "Party Messages", summary, width);

        ConstellationTheme.search(g, 16, 26, Math.min(244, width / 3 + 4), 22, search.isFocused());
        button(g, 18, 53, 104, 18, "Sort: " + label(sort.name()), mx, my, false);
        button(g, 126, 53, 104, 18, "Filter: " + label(filter.name()), mx, my, filter != Filter.ALL);

        int split = width / 2;
        List<PartyMessages.Definition> shown = visible();
        int listTop = 77, rowH = 24, viewBottom = height - 13;
        maxScroll = Math.max(0, shown.size() * rowH - (viewBottom - listTop));
        scroll = Math.clamp(scroll, 0, maxScroll);
        ConstellationUi.panel(g, 8, listTop - 4, split - 16, viewBottom - listTop + 8);
        ConstellationUi.panel(g, split + 6, 31, width - split - 14, height - 44);
        g.enableScissor(8, listTop, split - 7, viewBottom);
        for (int i = 0; i < shown.size(); i++) {
            PartyMessages.Definition definition = shown.get(i);
            int y = listTop + i * rowH - scroll;
            boolean enabled = PartyMessages.enabled(definition.id());
            boolean selectedNow = selected != null && selected.id().equals(definition.id());
            boolean hover = mx >= 10 && mx < split - 10 && my >= y && my < y + 21;
            ConstellationTheme.surface(g, 10, y, split - 20, 21,
                selectedNow ? 0xFF29294A : hover ? 0xFF222232 : 0xD8171722,
                selectedNow ? ConstellationTheme.ACCENT_DIM : ConstellationTheme.BORDER_SOFT);
            g.fill(14, y + 6, 23, y + 15, enabled ? ConstellationTheme.ACCENT : 0xFF44444F);
            g.text(font, ConstellationUi.fit(font, definition.name(), split - 49), 29, y + 3,
                enabled ? ConstellationTheme.TEXT : ConstellationTheme.TEXT_MUTED, false);
            g.text(font, definition.category(), 29, y + 12, ConstellationTheme.TEXT_MUTED, false);
        }
        g.disableScissor();
        ConstellationUi.scrollbar(g, split - 13, listTop, viewBottom - listTop,
            viewBottom - listTop, shown.size() * rowH, scroll);

        int rx = split + 12;
        if (selected == null) {
            g.text(font, "Select a message", rx, 34, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        g.text(font, selected.name(), rx, 34, ConstellationTheme.ACCENT_BRIGHT, false);
        g.text(font, "Category: " + selected.category(), rx, 49, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, PartyMessages.enabled(selected.id()) ? "Enabled" : "Disabled", rx, 64,
            PartyMessages.enabled(selected.id()) ? 0xFF55FF55 : 0xFFFF5555, false);
        g.text(font, "Variables", rx, 88, ConstellationTheme.TEXT, false);
        String vars = selected.variables().isEmpty() ? "none" : selected.variables().stream()
            .map(value -> "{" + value + "}").reduce((a, b) -> a + "  " + b).orElse("none");
        g.text(font, vars, rx, 102, ConstellationTheme.ACCENT_BRIGHT, false);
        g.text(font, "Default", rx, 126, ConstellationTheme.TEXT, false);
        g.text(font, ConstellationUi.fit(font, selected.defaultTemplate(), width - rx - 18),
            rx, 140, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "Current preview", rx, 164, ConstellationTheme.TEXT, false);
        g.text(font, ConstellationUi.fit(font, preview(selected, PartyMessages.template(selected.id())),
            width - rx - 18), rx, 178, 0xFFFFFFFF, false);
        button(g, rx, height - 82, 70, 18, "Toggle", mx, my, PartyMessages.enabled(selected.id()));
        button(g, rx + 76, height - 82, 70, 18, "Reset", mx, my, false);
        g.text(font, "Template", rx, height - 66, ConstellationTheme.TEXT_MUTED, false);
        ConstellationTheme.search(g, width / 2 + 8, height - 56, width / 2 - 24, 22, template.isFocused());
        g.text(font, "esc to return", width - font.width("esc to return") - 12, height - 12, ConstellationTheme.TEXT_MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        if (inside(mx, my, 18, 51, 104, 18)) { sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length]; return true; }
        if (inside(mx, my, 126, 51, 104, 18)) { filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length]; scroll = 0; return true; }
        int split = width / 2;
        List<PartyMessages.Definition> shown = visible();
        if (mx >= 10 && mx < split - 10 && my >= 75 && my < height - 12) {
            int index = (my - 75 + scroll) / 24;
            if (index >= 0 && index < shown.size()) {
                PartyMessages.Definition clicked = shown.get(index);
                if (mx < 26) PartyMessages.setEnabled(clicked.id(), !PartyMessages.enabled(clicked.id()));
                else select(clicked);
                return true;
            }
        }
        int rx = split + 12;
        if (selected != null && inside(mx, my, rx, height - 82, 70, 18)) {
            PartyMessages.setEnabled(selected.id(), !PartyMessages.enabled(selected.id())); return true;
        }
        if (selected != null && inside(mx, my, rx + 76, height - 82, 70, 18)) {
            PartyMessages.resetTemplate(selected.id()); select(selected); return true;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx < width / 2) scroll = Math.clamp(scroll - (int) (scrollY * 30), 0, maxScroll);
        return true;
    }

    private void select(PartyMessages.Definition definition) {
        selected = definition;
        changingTemplate = true;
        template.setValue(PartyMessages.template(definition.id()));
        changingTemplate = false;
    }

    private List<PartyMessages.Definition> visible() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<PartyMessages.Definition> result = new ArrayList<>();
        for (PartyMessages.Definition definition : PartyMessages.definitions()) {
            boolean enabled = PartyMessages.enabled(definition.id());
            if (filter == Filter.ENABLED && !enabled || filter == Filter.DISABLED && enabled) continue;
            String haystack = (definition.name() + " " + definition.category() + " "
                + String.join(" ", definition.variables())).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || haystack.contains(query)) result.add(definition);
        }
        Comparator<PartyMessages.Definition> comparator = switch (sort) {
            case NAME -> Comparator.comparing(PartyMessages.Definition::name);
            case CATEGORY -> Comparator.comparing(PartyMessages.Definition::category).thenComparing(PartyMessages.Definition::name);
            case ENABLED -> Comparator.<PartyMessages.Definition, Boolean>comparing(d -> !PartyMessages.enabled(d.id()))
                .thenComparing(PartyMessages.Definition::name);
        };
        result.sort(comparator);
        return result;
    }

    private String preview(PartyMessages.Definition definition, String value) {
        String preview = value;
        for (String variable : definition.variables()) preview = preview.replace("{" + variable + "}", "<" + variable + ">");
        return preview;
    }

    private int enabledCount() {
        int count = 0;
        for (PartyMessages.Definition definition : PartyMessages.definitions()) if (PartyMessages.enabled(definition.id())) count++;
        return count;
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, int mx, int my, boolean active) {
        ConstellationUi.button(g, font, x, y, w, h, text, inside(mx, my, x, y, w, h), active);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String label(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
}
