package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/InventorySearch.java
// ported from NoammAddons (CC0-1.0): features/impl/misc/InventorySearch.kt
public final class LyraInventorySearch {
    private enum Field { ANY, NAME, LORE, ID }
    private record Term(Field field, String value) {}

    private static LyraConfig cfg;
    private static AbstractContainerScreen<?> openScreen;
    private static SearchBox searchBox;
    private static String query = "";
    private static List<Term> terms = List.of();
    private static Double calculation;

    private LyraInventorySearch() {}

    public static void init(LyraConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !active()) return;
            openScreen = null;
            searchBox = null;
            if (!cfg.inventorySearchRememberQuery) setQuery("");
            if (cfg.inventorySearchClickablePrompt) Screens.getWidgets(container).add(new SearchPrompt(container));
            ScreenEvents.remove(container).register(ignored -> close(container));
            ScreenKeyboardEvents.allowKeyPress(container).register((ignored, event) -> key(container, event));
            ScreenMouseEvents.allowMouseClick(container).register((ignored, event) -> mouse(container, event));
        });
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.inventorySearch && ConstellationClient.loc().onHypixel();
    }

    private static boolean key(AbstractContainerScreen<?> screen, KeyEvent event) {
        if (!active()) return true;
        int wanted = cfg.inventorySearchCtrlK ? GLFW.GLFW_KEY_K : GLFW.GLFW_KEY_F;
        if (event.key() == wanted && event.hasControlDownWithQuirk()) {
            show(screen);
            return false;
        }
        if (searchBox != null && searchBox.isFocused()
            && event.key() != GLFW.GLFW_KEY_ESCAPE
            && Minecraft.getInstance().options.keyInventory.matches(event)) return false;
        return true;
    }

    private static boolean mouse(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        if (searchBox == null || openScreen != screen || !active()) return true;
        if (searchBox.isMouseOver(event.x(), event.y())) return !searchBox.mouseClicked(event, false);
        if (searchBox.isFocused()) searchBox.setFocused(false);
        return true;
    }

    private static void show(AbstractContainerScreen<?> screen) {
        if (!active()) return;
        if (openScreen == screen && searchBox != null) {
            searchBox.setFocused(true);
            return;
        }
        openScreen = screen;
        searchBox = new SearchBox(screen.getFont(), Component.literal("Search inventory"));
        searchBox.setPosition((screen.width - searchBox.getWidth()) / 2, 15);
        searchBox.setHint(Component.literal("name, lore, id, or arithmetic"));
        searchBox.setValue(query);
        searchBox.setResponder(LyraInventorySearch::setQuery);
        Screens.getWidgets(screen).removeIf(widget -> widget instanceof SearchPrompt);
        Screens.getWidgets(screen).addFirst(searchBox);
        screen.setFocused(searchBox);
    }

    private static void close(AbstractContainerScreen<?> screen) {
        if (openScreen != screen) return;
        openScreen = null;
        searchBox = null;
        if (!cfg.inventorySearchRememberQuery) setQuery("");
    }

    private static void setQuery(String value) {
        query = value == null ? "" : value.strip();
        terms = parseTerms(query);
        calculation = cfg != null && cfg.inventorySearchCalculator ? calculate(query) : null;
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!active() || screen != openScreen || searchBox == null || query.isBlank() || slot == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean playerSlot = mc.player != null && slot.container == mc.player.getInventory();
        if (playerSlot ? !cfg.inventorySearchPlayerSlots : !cfg.inventorySearchContainerSlots) return;
        boolean match = matches(slot.getItem());
        if (match && cfg.inventorySearchHighlightMatches)
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, cfg.inventorySearchHighlightColor);
        else if (!match && cfg.inventorySearchDimNonMatches)
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, cfg.inventorySearchDimColor);
    }

    public static boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty() || terms.isEmpty()) return false;
        String name = normalize(plain(stack.getHoverName()));
        String lore = cfg.inventorySearchLore ? normalize(lore(stack)) : "";
        String id = cfg.inventorySearchId ? normalize(skyblockId(stack)) : "";
        for (Term term : terms) {
            boolean match = switch (term.field) {
                case NAME -> name.contains(term.value);
                case LORE -> cfg.inventorySearchLore && lore.contains(term.value);
                case ID -> cfg.inventorySearchId && id.contains(term.value);
                case ANY -> name.contains(term.value)
                    || (cfg.inventorySearchLore && lore.contains(term.value))
                    || (cfg.inventorySearchId && id.contains(term.value));
            };
            if (!match) return false;
        }
        return true;
    }

    private static List<Term> parseTerms(String input) {
        if (input.isBlank()) return List.of();
        List<Term> parsed = new ArrayList<>();
        for (String raw : splitQuoted(input)) {
            if (raw.isBlank()) continue;
            Field field = Field.ANY;
            String value = raw;
            int separator = raw.indexOf(':');
            if (separator > 0) {
                field = switch (raw.substring(0, separator).toLowerCase(Locale.ROOT)) {
                    case "name", "n" -> Field.NAME;
                    case "lore", "l" -> Field.LORE;
                    case "id", "i" -> Field.ID;
                    default -> Field.ANY;
                };
                if (field != Field.ANY) value = raw.substring(separator + 1);
            }
            value = normalize(value);
            if (!value.isBlank()) parsed.add(new Term(field, value));
        }
        return List.copyOf(parsed);
    }

    private static List<String> splitQuoted(String input) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (Character.isWhitespace(c) && !quoted) {
                if (!current.isEmpty()) { values.add(current.toString()); current.setLength(0); }
            } else current.append(c);
        }
        if (!current.isEmpty()) values.add(current.toString());
        return values;
    }

    private static String normalize(String value) {
        return cfg != null && cfg.inventorySearchIgnoreCase ? value.toLowerCase(Locale.ROOT) : value;
    }

    private static String lore(ItemStack stack) {
        ItemLore itemLore = stack.get(DataComponents.LORE);
        if (itemLore == null) return "";
        StringBuilder result = new StringBuilder();
        for (Component line : itemLore.lines()) result.append(' ').append(plain(line));
        return result.toString();
    }

    private static String skyblockId(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return "";
        return custom.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("id", "");
    }

    private static String plain(Component component) {
        String value = ChatFormatting.stripFormatting(component.getString());
        return value == null ? component.getString() : value;
    }

    // ported from NoammAddons (CC0-1.0): features/impl/misc/InventorySearch.kt evaluateExpression
    private static Double calculate(String expression) {
        if (expression == null || expression.isBlank() || expression.chars().noneMatch(Character::isDigit)) return null;
        List<String> tokens = tokenize(expression);
        if (tokens == null) return null;
        ArrayDeque<String> operators = new ArrayDeque<>();
        List<String> output = new ArrayList<>();
        for (String token : tokens) {
            Double number = number(token);
            if (number != null) output.add(token);
            else if (token.equals("(")) operators.push(token);
            else if (token.equals(")")) {
                while (!operators.isEmpty() && !operators.peek().equals("(")) output.add(operators.pop());
                if (operators.isEmpty()) return null;
                operators.pop();
            } else {
                while (!operators.isEmpty() && precedence(operators.peek()) >= precedence(token)) output.add(operators.pop());
                operators.push(token);
            }
        }
        while (!operators.isEmpty()) { if (operators.peek().equals("(")) return null; output.add(operators.pop()); }
        ArrayDeque<Double> values = new ArrayDeque<>();
        for (String token : output) {
            Double number = number(token);
            if (number != null) values.push(number);
            else {
                if (values.size() < 2) return null;
                double b = values.pop(), a = values.pop();
                double result = switch (token) { case "+" -> a + b; case "-" -> a - b; case "*", "x" -> a * b; case "/" -> b == 0 ? Double.NaN : a / b; default -> Double.NaN; };
                if (!Double.isFinite(result)) return null;
                values.push(result);
            }
        }
        return values.size() == 1 ? values.pop() : null;
    }

    private static List<String> tokenize(String expression) {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < expression.length();) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (Character.isDigit(c) || c == '.') {
                int start = i++;
                while (i < expression.length() && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) i++;
                if (i < expression.length() && "kmbtKMBT".indexOf(expression.charAt(i)) >= 0) i++;
                tokens.add(expression.substring(start, i));
            } else if ("+-*/x()".indexOf(c) >= 0) { tokens.add(Character.toString(c)); i++; }
            else return null;
        }
        return tokens;
    }

    private static Double number(String token) {
        if (token == null || token.isBlank()) return null;
        double multiplier = 1;
        char end = Character.toLowerCase(token.charAt(token.length() - 1));
        if (end == 'k' || end == 'm' || end == 'b' || end == 't') {
            multiplier = switch (end) { case 'k' -> 1e3; case 'm' -> 1e6; case 'b' -> 1e9; default -> 1e12; };
            token = token.substring(0, token.length() - 1);
        }
        try { double value = Double.parseDouble(token) * multiplier; return Double.isFinite(value) ? value : null; }
        catch (NumberFormatException ignored) { return null; }
    }

    private static int precedence(String operator) {
        return switch (operator) { case "+", "-" -> 1; case "*", "x", "/" -> 2; default -> -1; };
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("inventorysearch")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(context -> clear()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("§eInventory search " + on(cfg.inventorySearch) + ", shortcut §fCtrl+" + (cfg.inventorySearchCtrlK ? "K" : "F")
            + "§e, lore " + on(cfg.inventorySearchLore) + ", IDs " + on(cfg.inventorySearchId)
            + ", dim " + on(cfg.inventorySearchDimNonMatches) + ", highlight " + on(cfg.inventorySearchHighlightMatches)
            + ", calculator " + on(cfg.inventorySearchCalculator) + ".");
        return 1;
    }

    private static int clear() { setQuery(""); local("§aInventory search cleared."); return 1; }

    private static int option(String name, String state) {
        Boolean value = parseState(state);
        if (value == null) { local("§cState must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled", "search" -> cfg.inventorySearch = value;
            case "lore" -> cfg.inventorySearchLore = value;
            case "id", "ids" -> cfg.inventorySearchId = value;
            case "case", "ignorecase" -> cfg.inventorySearchIgnoreCase = value;
            case "dim" -> cfg.inventorySearchDimNonMatches = value;
            case "highlight" -> cfg.inventorySearchHighlightMatches = value;
            case "player" -> cfg.inventorySearchPlayerSlots = value;
            case "container", "chest" -> cfg.inventorySearchContainerSlots = value;
            case "prompt" -> cfg.inventorySearchClickablePrompt = value;
            case "ctrlk" -> cfg.inventorySearchCtrlK = value;
            case "remember" -> cfg.inventorySearchRememberQuery = value;
            case "calculator", "math" -> cfg.inventorySearchCalculator = value;
            default -> { local("§cOption must be enabled, lore, id, case, dim, highlight, player, container, prompt, ctrlk, remember, or calculator."); return 0; }
        }
        ConstellationClient.saveConfig();
        local("§aInventory-search option updated.");
        return 1;
    }

    private static Boolean parseState(String state) { return switch (state.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "§aon" : "§coff"; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f" + text)); }

    private static final class SearchPrompt extends StringWidget {
        private final Component normal;
        private final Component hovered;
        private final AbstractContainerScreen<?> screen;

        private SearchPrompt(AbstractContainerScreen<?> screen) {
            super(Component.empty(), screen.getFont());
            this.screen = screen;
            normal = Component.literal("Click or Ctrl+" + (cfg.inventorySearchCtrlK ? "K" : "F") + " to search").withStyle(ChatFormatting.GRAY);
            hovered = normal.copy().withStyle(ChatFormatting.UNDERLINE);
            setMessage(normal);
            setPosition((screen.width - getWidth()) / 2, 15);
        }

        @Override public void onClick(MouseButtonEvent click, boolean doubled) { show(screen); }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            setMessage(isHovered() ? hovered : normal);
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    private static final class SearchBox extends EditBox {
        private final Font font;
        private final Component label;
        private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.##");

        private SearchBox(Font font, Component label) {
            super(font, 200, 20, label);
            this.font = font;
            this.label = label;
        }

        @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
            graphics.centeredText(font, label, getX() + width / 2, getY() - 1 - font.lineHeight, CommonColors.WHITE);
            if (calculation != null) graphics.text(font, "= " + NUMBER.format(calculation), getX() + width + 5, getY() + 6, 0xFFFFFF55, true);
        }

        @Override public boolean keyPressed(KeyEvent event) {
            return super.keyPressed(event) || (event.key() != GLFW.GLFW_KEY_ESCAPE && isFocused());
        }

        @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            if (isFocused() && !isMouseOver(click.x(), click.y())) { setFocused(false); return false; }
            if (super.mouseClicked(click, doubled)) { setFocused(true); return true; }
            return false;
        }
    }
}
