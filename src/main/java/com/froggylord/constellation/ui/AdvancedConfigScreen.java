package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.BaseConfigGroup;
import com.froggylord.constellation.render.ConstellationIcons;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// ported from Athen (BSD-3-Clause): config/ui/SearchBar.kt, elements/TextInputElement.kt, elements/SliderElement.kt
public final class AdvancedConfigScreen extends Screen {
    private static final int HEADER = 27;
    private static final int SEARCH_Y = 33;
    private static final int LIST_Y = 58;
    private static final int ROW_H = 22;
    private final Screen parent;
    private final String constellationId;
    private final BaseConfigGroup config;
    private final Object defaults;
    private final List<Entry> entries = new ArrayList<>();
    private EditBox search;
    private EditBox editor;
    private Entry editing;
    private String error = "";
    private int scroll;
    private int maxScroll;
    private Filter filter = Filter.ALL;

    public AdvancedConfigScreen(Screen parent, String constellationId) {
        super(Component.literal("All settings"));
        this.parent = parent;
        this.constellationId = constellationId;
        this.config = ConstellationClient.instance().configManager().getGroup(constellationId);
        this.defaults = createDefaults(config);
        collect();
    }

    @Override protected void init() {
        search = new EditBox(font, 12, SEARCH_Y, Math.max(80, width - 116), 18, Component.literal("Search settings"));
        search.setHint(Component.literal("search name, type or value"));
        search.setMaxLength(80);
        search.setResponder(value -> scroll = 0);
        addRenderableWidget(search);

        editor = new EditBox(font, width / 2 - 138, height / 2 + 2, 276, 18, Component.literal("Value"));
        editor.setMaxLength(512);
        editor.visible = false;
        addRenderableWidget(editor);
    }

    private void collect() {
        if (config == null) return;
        for (Field field : config.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("version") || !supported(field.getType())) continue;
            entries.add(new Entry(field, kind(field)));
        }
        entries.sort(Comparator.comparing((Entry entry) -> entry.kind.ordinal()).thenComparing(entry -> entry.field.getName()));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        SpaceBackground.renderConfig(graphics, width, height, delta);
        graphics.fill(0, 0, width, height, 0xD9080814);
        graphics.fill(0, 0, width, HEADER, 0xF20E0E1A);
        graphics.fill(0, HEADER - 1, width, HEADER, ConstellationTheme.ACCENT);
        ConstellationIcons.draw(graphics, constellationId, 7, 3, 21);
        String name = ConstellationClient.featureManager().get(constellationId)
            .map(com.froggylord.constellation.core.BaseConstellation::displayName).orElse(constellationId);
        graphics.text(font, name + " settings", 33, 9, ConstellationTheme.ACCENT_BRIGHT, false);
        graphics.text(font, "left click edit  right click reset", width - font.width("left click edit  right click reset") - 8,
            9, ConstellationTheme.TEXT_MUTED, false);

        button(graphics, width - 98, SEARCH_Y, 86, filter.label, mouseX, mouseY);
        List<Entry> visible = visible();
        int view = Math.max(1, height - LIST_Y - 13);
        maxScroll = Math.max(0, visible.size() * ROW_H - view);
        scroll = Math.clamp(scroll, 0, maxScroll);
        graphics.enableScissor(8, LIST_Y, width - 8, height - 12);
        for (int i = 0; i < visible.size(); i++) {
            int y = LIST_Y + i * ROW_H - scroll;
            if (y + ROW_H < LIST_Y || y >= height - 12) continue;
            drawRow(graphics, visible.get(i), 10, y, width - 20, mouseX, mouseY);
        }
        graphics.disableScissor();

        String count = visible.size() + " of " + entries.size() + " settings";
        graphics.text(font, count, 10, height - 10, ConstellationTheme.TEXT_MUTED, false);
        if (maxScroll > 0) {
            int barH = Math.max(18, view * view / (view + maxScroll));
            int barY = LIST_Y + (view - barH) * scroll / maxScroll;
            graphics.fill(width - 5, LIST_Y, width - 2, LIST_Y + view, 0xFF181824);
            graphics.fill(width - 5, barY, width - 2, barY + barH, ConstellationTheme.ACCENT_DIM);
        }
        if (editing != null) drawEditor(graphics, mouseX, mouseY);
    }

    private void drawRow(GuiGraphicsExtractor graphics, Entry entry, int x, int y, int rowWidth, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, rowWidth, ROW_H - 2);
        graphics.fill(x, y, x + rowWidth, y + ROW_H - 2, hover ? 0xEE24243A : 0xDD171727);
        graphics.fill(x, y, x + 2, y + ROW_H - 2, color(entry.kind));
        graphics.text(font, fit(label(entry.field.getName()), rowWidth / 2 - 14), x + 8, y + 6,
            ConstellationTheme.TEXT, false);
        String value = value(entry);
        int valueWidth = Math.max(40, rowWidth / 2 - 18);
        int valueX = x + rowWidth - font.width(fit(value, valueWidth)) - 8;
        if (entry.kind == Kind.COLOR) {
            int color = colorValue(entry);
            graphics.fill(x + rowWidth / 2, y + 5, x + rowWidth / 2 + 10, y + 15, color);
            graphics.fill(x + rowWidth / 2, y + 5, x + rowWidth / 2 + 10, y + 6, 0xFFFFFFFF);
        }
        graphics.text(font, fit(value, valueWidth), valueX, y + 6,
            entry.kind == Kind.BOOLEAN && bool(entry) ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT_MUTED, false);
    }

    private void drawEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0x99000000);
        int x = width / 2 - 150, y = height / 2 - 48;
        graphics.fill(x, y, x + 300, y + 96, 0xFF171727);
        graphics.fill(x, y, x + 300, y + 2, ConstellationTheme.ACCENT);
        graphics.text(font, label(editing.field.getName()), x + 12, y + 10, ConstellationTheme.ACCENT_BRIGHT, false);
        graphics.text(font, editing.kind.label, x + 12, y + 24, ConstellationTheme.TEXT_MUTED, false);
        button(graphics, x + 12, y + 67, 132, "Save", mouseX, mouseY);
        button(graphics, x + 156, y + 67, 132, "Cancel", mouseX, mouseY);
        if (!error.isBlank()) graphics.text(font, fit(error, 276), x + 12, y + 55, 0xFFFF7777, false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mouseX = (int) event.x(), mouseY = (int) event.y();
        if (editing != null) {
            int x = width / 2 - 150, y = height / 2 - 48;
            if (inside(mouseX, mouseY, x + 12, y + 67, 132, 19)) return commit();
            if (inside(mouseX, mouseY, x + 156, y + 67, 132, 19)) { closeEditor(); return true; }
            return super.mouseClicked(event, doubled);
        }
        if (inside(mouseX, mouseY, width - 98, SEARCH_Y, 86, 18)) {
            filter = filter.next();
            scroll = 0;
            return true;
        }
        List<Entry> visible = visible();
        for (int i = 0; i < visible.size(); i++) {
            int y = LIST_Y + i * ROW_H - scroll;
            if (!inside(mouseX, mouseY, 10, y, width - 20, ROW_H - 2) || y < LIST_Y) continue;
            Entry entry = visible.get(i);
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) return reset(entry);
            if (entry.kind == Kind.BOOLEAN) return toggle(entry);
            openEditor(entry);
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (editing != null) return true;
        scroll = Math.clamp(scroll - (int) (vertical * ROW_H * 2), 0, maxScroll);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (editing != null) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) return commit();
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) { closeEditor(); return true; }
        } else if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void openEditor(Entry entry) {
        editing = entry;
        error = "";
        search.visible = false;
        editor.visible = true;
        editor.setValue(raw(entry));
        editor.setFocused(true);
        setFocused(editor);
    }

    private boolean commit() {
        try {
            set(editing.field, editor.getValue());
            ConstellationClient.saveConfig();
            closeEditor();
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        } catch (ReflectiveOperationException exception) {
            error = "Could not save this setting.";
        }
        return true;
    }

    private void closeEditor() {
        editing = null;
        error = "";
        search.visible = true;
        editor.visible = false;
        editor.setFocused(false);
        setFocused(null);
    }

    private boolean toggle(Entry entry) {
        try {
            entry.field.setBoolean(config, !entry.field.getBoolean(config));
            ConstellationClient.saveConfig();
        } catch (ReflectiveOperationException ignored) {}
        return true;
    }

    private boolean reset(Entry entry) {
        if (defaults == null) return true;
        try {
            entry.field.set(config, entry.field.get(defaults));
            ConstellationClient.saveConfig();
        } catch (ReflectiveOperationException ignored) {}
        return true;
    }

    private List<Entry> visible() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> filter.accepts(entry.kind)).filter(entry -> query.isBlank()
            || entry.field.getName().toLowerCase(Locale.ROOT).contains(query)
            || label(entry.field.getName()).toLowerCase(Locale.ROOT).contains(query)
            || entry.kind.label.toLowerCase(Locale.ROOT).contains(query)
            || value(entry).toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private String value(Entry entry) {
        String raw = raw(entry);
        if (entry.kind == Kind.BOOLEAN) return bool(entry) ? "On" : "Off";
        if (entry.kind == Kind.STRING) return raw.isBlank() ? "(empty)" : raw;
        return raw;
    }

    private String raw(Entry entry) {
        try {
            Object value = entry.field.get(config);
            if (entry.kind == Kind.COLOR && value instanceof Number number)
                return String.format(Locale.ROOT, "#%08X", number.longValue() & 0xFFFFFFFFL);
            return String.valueOf(value == null ? "" : value);
        } catch (ReflectiveOperationException ignored) {
            return "(unavailable)";
        }
    }

    private boolean bool(Entry entry) {
        try { return entry.field.getBoolean(config); } catch (ReflectiveOperationException ignored) { return false; }
    }

    private int colorValue(Entry entry) {
        try { return ((Number) entry.field.get(config)).intValue(); }
        catch (ReflectiveOperationException | ClassCastException ignored) { return 0xFF000000; }
    }

    private void set(Field field, String text) throws ReflectiveOperationException {
        String value = text.trim();
        Class<?> type = field.getType();
        boolean color = kind(field) == Kind.COLOR;
        try {
            if (type == String.class) field.set(config, text);
            else if (type == int.class) field.setInt(config, color ? (int) parseColor(value) : Integer.parseInt(value));
            else if (type == long.class) field.setLong(config, color ? parseColor(value) : Long.parseLong(value));
            else if (type == float.class) {
                float number = Float.parseFloat(value);
                if (!Float.isFinite(number)) throw new NumberFormatException();
                field.setFloat(config, number);
            } else if (type == double.class) {
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number)) throw new NumberFormatException();
                field.setDouble(config, number);
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(color ? "Use #AARRGGBB or #RRGGBB." : "Enter a valid finite " + type.getSimpleName() + ".");
        }
    }

    private static long parseColor(String value) {
        String clean = value.startsWith("#") ? value.substring(1) : value.startsWith("0x") || value.startsWith("0X")
            ? value.substring(2) : value;
        if (!clean.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) throw new NumberFormatException();
        long parsed = Long.parseUnsignedLong(clean, 16);
        return clean.length() == 6 ? parsed | 0xFF000000L : parsed;
    }

    private static Kind kind(Field field) {
        String name = field.getName().toLowerCase(Locale.ROOT);
        if ((field.getType() == int.class || field.getType() == long.class)
            && (name.contains("color") || name.contains("colour"))) return Kind.COLOR;
        if (field.getType() == boolean.class) return Kind.BOOLEAN;
        if (field.getType() == String.class) return Kind.STRING;
        return Kind.NUMBER;
    }

    private static boolean supported(Class<?> type) {
        return type == boolean.class || type == int.class || type == long.class || type == float.class
            || type == double.class || type == String.class;
    }

    private static Object createDefaults(BaseConfigGroup config) {
        if (config == null) return null;
        try { return config.getClass().getDeclaredConstructor().newInstance(); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    private static int color(Kind kind) {
        return switch (kind) {
            case BOOLEAN -> ConstellationTheme.ACCENT;
            case NUMBER -> 0xFF55AAFF;
            case STRING -> 0xFFB47AFF;
            case COLOR -> 0xFFFF77AA;
        };
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int buttonWidth, String text, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, buttonWidth, 18);
        graphics.fill(x, y, x + buttonWidth, y + 18, hover ? 0xFF30304A : 0xFF202033);
        graphics.text(font, fit(text, buttonWidth - 8), x + 4, y + 5,
            hover ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width("..."))) + "...";
    }

    private static String label(String camel) {
        String value = camel.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return value.isBlank() ? camel : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int boxWidth, int boxHeight) {
        return mouseX >= x && mouseX < x + boxWidth && mouseY >= y && mouseY < y + boxHeight;
    }

    @Override public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private record Entry(Field field, Kind kind) {}

    private enum Kind {
        BOOLEAN("Toggle"), NUMBER("Number"), STRING("Text"), COLOR("ARGB color");
        private final String label;
        Kind(String label) { this.label = label; }
    }

    private enum Filter {
        ALL("All types"), BOOLEAN("Toggles"), NUMBER("Numbers"), STRING("Text"), COLOR("Colors");
        private final String label;
        Filter(String label) { this.label = label; }
        private boolean accepts(Kind kind) { return this == ALL || name().equals(kind.name()); }
        private Filter next() { return values()[(ordinal() + 1) % values().length]; }
    }
}
