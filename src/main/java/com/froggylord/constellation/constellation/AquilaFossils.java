package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/fossil/Structures.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/fossil/FossilTypes.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/fossil/FossilCalculations.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/fossil/FossilSolver.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/fossil/FossilMuncher.java
// ported from SkyHanni (LGPL-2.1): features/mining/fossilexcavator/solver/FossilSolver.kt
// ported from SkyHanni (LGPL-2.1): features/mining/fossilexcavator/solver/FossilSolverDisplay.kt
public final class AquilaFossils {
    private enum Tile { UNKNOWN, EMPTY, FOSSIL }
    private enum Turn { R0, R90, R180, R270, F0, F90, F180, F270 }
    private record Shape(String name, String progress, String[] rows, Set<Turn> turns, int tiles) {}
    private record Placement(Shape shape, boolean[][] cells, int x, int y) {}
    private record Start(int slot, double chance, int patterns) {}
    public record State(boolean visible, String status, int nextSlot, double nextChance, int patterns,
                        int minimumTiles, int charges, String fossil, List<String> types, double[] probabilities) {}

    private static final Pattern CHARGES = Pattern.compile("Chisel Charges Remaining: (\\d+)");
    private static final Pattern PROGRESS = Pattern.compile("Fossil Excavation Progress: ([\\d.]+)%");
    private static final Pattern MUNCHER = Pattern.compile("^\\[NPC] Fossil Muncher: the fossil i want ([a-zA-Z, \\-]*)$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> MUNCHER_ANSWERS = Map.of(
        "lived underground and dug tunnels", "Claw Fossil",
        "had a really fancy tail", "Clubbed Fossil",
        "was the king of his kind", "Footprint Fossil",
        "lived underwater", "Helix Fossil",
        "was kinda spiny u know", "Spine Fossil",
        "lived in herds and was quite woolly", "Tusk Fossil",
        "is pretty rough to look at", "Ugly Fossil",
        "had a really pointy beak", "Webbed Fossil"
    );
    private static final List<Shape> SHAPES = List.of(
        shape("Claw", "7.7", 13, new String[]{".#....", "####..", ".##.#.", ".#.#.#", "..#.#."}, Turn.values()),
        shape("Tusk", "12.5", 8, new String[]{"....#", ".#..#", "#...#", ".#.#.", "..#.."}, Turn.values()),
        shape("Ugly", "6.2", 16, new String[]{"..##..", ".####.", "######", ".####."}, Turn.R0, Turn.R90, Turn.R180, Turn.R270),
        shape("Helix", "7.1", 14, new String[]{"#####", "#...#", "#.#.#", "#.###"}, Turn.values()),
        shape("Webbed", "10", 10, new String[]{"...#...", "#..#..#", ".#.#.#.", "..###.."}, Turn.R0, Turn.F0),
        shape("Footprint", "7.7", 13, new String[]{"#.#.#", "#.#.#", ".###.", ".###.", "..#.."}, Turn.R0, Turn.R90, Turn.R180, Turn.R270),
        shape("Clubbed", "9.1", 11, new String[]{"......##", ".#....##", "#....#..", ".####..."}, Turn.R0, Turn.R180, Turn.F0, Turn.F180),
        shape("Spine", "8.3", 12, new String[]{"..##..", ".####.", "######"}, Turn.R0, Turn.R90, Turn.R180, Turn.R270)
    );
    private static final List<Placement> PLACEMENTS = placements();
    private static final List<Start> SAFE_START = List.of(
        start(4, 2, .515, 404), start(5, 4, .413, 196), start(3, 3, .461, 115), start(5, 2, .387, 62),
        start(3, 1, .342, 38), start(7, 3, .48, 25), start(1, 2, .846, 13), start(3, 4, 1, 2));
    private static final List<Start> RISKY_START = List.of(
        start(4, 2, .515, 404), start(5, 3, .393, 196), start(3, 2, .513, 119), start(7, 2, .345, 58),
        start(1, 3, .342, 38), start(3, 4, .6, 25), start(5, 1, .8, 10), start(4, 3, 1, 2));

    private static AquilaConfig cfg;
    private static State state = empty();
    private static String fingerprint = "";
    private static int maxCharges;
    private static boolean initialized;

    private AquilaFossils() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(2, "aquila-fossils", AquilaFossils::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !muncher(clean(message.getString())));
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || !excavator(screen)) {
            reset();
            return;
        }
        List<Slot> slots = screen.getMenu().slots;
        if (slots.size() < 54) { reset(); return; }
        Tile[] board = new Tile[54];
        int charges = -1;
        String progress = null;
        StringBuilder key = new StringBuilder();
        int dirt = 0;
        for (int i = 0; i < 54; i++) {
            ItemStack stack = slots.get(i).getItem();
            String name = clean(stack.getHoverName().getString());
            board[i] = name.equals("Fossil") ? Tile.FOSSIL : name.equals("Dirt") ? Tile.UNKNOWN : Tile.EMPTY;
            if (board[i] == Tile.UNKNOWN) dirt++;
            key.append((char) ('0' + board[i].ordinal()));
            if (charges < 0 || progress == null) {
                ItemLore lore = stack.get(DataComponents.LORE);
                if (lore != null) for (Component line : lore.lines()) {
                    String text = clean(line.getString());
                    Matcher charge = CHARGES.matcher(text);
                    if (charges < 0 && charge.find()) charges = parseInt(charge.group(1), -1);
                    Matcher percent = PROGRESS.matcher(text);
                    if (progress == null && percent.find()) progress = normalizeProgress(percent.group(1));
                }
            }
        }
        if (dirt == 0 && Arrays.stream(board).noneMatch(tile -> tile == Tile.FOSSIL)) { reset(); return; }
        key.append(':').append(charges).append(':').append(progress);
        String nextKey = key.toString();
        if (nextKey.equals(fingerprint)) return;
        fingerprint = nextKey;
        if (maxCharges == 0 && charges > 0) maxCharges = charges;
        state = solve(board, charges, progress);
    }

    private static State solve(Tile[] board, int charges, String progress) {
        Set<Integer> found = new HashSet<>();
        Set<Integer> empty = new HashSet<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] == Tile.FOSSIL) found.add(i);
            else if (board[i] == Tile.EMPTY) empty.add(i);
        }
        List<Start> sequence = maxCharges >= 18 ? SAFE_START : RISKY_START;
        if (found.isEmpty() && empty.stream().allMatch(slot -> sequence.stream().anyMatch(move -> move.slot == slot))) {
            if (empty.size() >= sequence.size()) return new State(true, "No possible fossil", -1, 0, 0, 54, charges, "", List.of(), new double[54]);
            Start move = sequence.get(empty.size());
            double[] chances = new double[54];
            chances[move.slot] = move.chance;
            return new State(true, "Starting sequence", move.slot, move.chance, move.patterns, 0, charges, "", List.of(), chances);
        }

        List<Placement> valid = new ArrayList<>();
        for (Placement placement : PLACEMENTS) {
            if (progress != null && !placement.shape.progress.equals(progress)) continue;
            if (valid(placement, found, empty)) valid.add(placement);
        }
        double[] chances = new double[54];
        if (!valid.isEmpty()) for (Placement placement : valid) {
            for (int y = 0; y < placement.cells.length; y++) for (int x = 0; x < placement.cells[y].length; x++)
                if (placement.cells[y][x]) chances[(placement.y + y) * 9 + placement.x + x]++;
        }
        int best = -1;
        double high = 0;
        for (int i = 0; i < chances.length; i++) {
            chances[i] = valid.isEmpty() ? 0 : chances[i] / valid.size();
            if (board[i] == Tile.UNKNOWN && chances[i] > high) { high = chances[i]; best = i; }
        }
        int minimum = valid.stream().mapToInt(p -> Math.max(0, p.shape.tiles - found.size())).min().orElse(0);
        LinkedHashSet<String> types = new LinkedHashSet<>();
        valid.forEach(p -> types.add(p.shape.name));
        String fossil = types.size() == 1 && !found.isEmpty() ? types.getFirst() : "";
        String status = valid.isEmpty() ? (found.isEmpty() ? "No possible fossil" : "Fossil uncovered") : "Solving";
        return new State(true, status, best, high, valid.size(), minimum, charges, fossil, List.copyOf(types), chances);
    }

    private static boolean valid(Placement placement, Set<Integer> found, Set<Integer> empty) {
        for (int slot : found) if (!contains(placement, slot)) return false;
        for (int slot : empty) if (contains(placement, slot)) return false;
        return true;
    }

    private static boolean contains(Placement placement, int slot) {
        int x = slot % 9 - placement.x, y = slot / 9 - placement.y;
        return y >= 0 && y < placement.cells.length && x >= 0 && x < placement.cells[y].length && placement.cells[y][x];
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!activeBoard(screen) || slot == null || slot.getContainerSlot() < 0 || slot.getContainerSlot() >= 54) return;
        int index = slot.getContainerSlot();
        double chance = state.probabilities[index];
        if (index == state.nextSlot && cfg.fossilShowBestHighlight) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, cfg.fossilBestColor);
        } else if (chance > 0 && cfg.fossilShowProbabilityHighlights) {
            int base = cfg.fossilProbabilityColor;
            int alpha = Math.clamp((int) Math.round(((base >>> 24) & 255) * chance), 20, 255);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, alpha << 24 | base & 0xFFFFFF);
        }
        if (index == state.nextSlot && cfg.fossilShowPercentageText && chance > 0) {
            String text = Math.round(chance * 100) + "%";
            graphics.text(Minecraft.getInstance().font, text, slot.x + 1, slot.y + 5, 0xFFFFFFFF, true);
        }
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, ItemStack stack, List<Component> lines) {
        if (!activeBoard(screen)) return lines;
        String name = clean(stack.getHoverName().getString());
        if (cfg.fossilHideAllTooltips || cfg.fossilHideDirtTooltips && name.equals("Dirt")) return List.of();
        if (!cfg.fossilTooltipInfo || !name.equals("Dirt") && !name.equals("Fossil")) return lines;
        List<Component> out = new ArrayList<>(lines);
        out.add(Component.empty());
        if (state.patterns == 0) out.add(Component.literal("§c" + state.status + "."));
        else {
            if (cfg.fossilShowPatterns) out.add(Component.literal("§ePossible patterns: §f" + state.patterns));
            if (cfg.fossilShowMinimumTiles) {
                String color = state.charges >= state.minimumTiles ? "§f" : "§c";
                out.add(Component.literal("§eMinimum tiles left: " + color + state.minimumTiles));
            }
            int slot = slotOf(screen, stack);
            if (slot >= 0 && slot < 54 && state.probabilities[slot] > 0)
                out.add(Component.literal("§eFossil probability: §f" + Math.round(state.probabilities[slot] * 100) + "%"));
            if (!state.fossil.isEmpty()) out.add(Component.literal("§eFound fossil: §f" + state.fossil));
        }
        return out;
    }

    public static boolean shouldBlockClick(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput input) {
        if (!activeBoard(screen) || !cfg.fossilProtectWrongClicks || slot == null || slot.getContainerSlot() < 0
            || slot.getContainerSlot() >= 54 || state.nextSlot < 0 || slot.getContainerSlot() == state.nextSlot) return false;
        if (!clean(slot.getItem().getHoverName().getString()).equals("Dirt")) return false;
        if (cfg.fossilControlBypass && (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS)) return false;
        return true;
    }

    private static int slotOf(AbstractContainerScreen<?> screen, ItemStack stack) {
        for (Slot slot : screen.getMenu().slots) if (slot.getItem() == stack) return slot.getContainerSlot();
        return -1;
    }

    private static boolean muncher(String message) {
        if (cfg == null || !cfg.enabled || !cfg.fossilMuncherSolver || !ConstellationClient.loc().onHypixel()) return false;
        Matcher matcher = MUNCHER.matcher(message);
        if (!matcher.matches()) return false;
        String riddle = matcher.group(1).toLowerCase(Locale.ROOT);
        String answer = MUNCHER_ANSWERS.get(riddle);
        if (answer == null) return false;
        if (cfg.fossilMuncherLocalAnswer) local("Fossil Muncher wants " + answer + ".");
        return cfg.fossilMuncherReplaceRiddle;
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.fossilHelper && cfg.fossilSolverSuite && ConstellationClient.loc().onHypixel();
    }
    private static boolean excavator(AbstractContainerScreen<?> screen) { return clean(screen.getTitle().getString()).equals("Fossil Excavator"); }
    private static boolean activeBoard(AbstractContainerScreen<?> screen) { return active() && state.visible && excavator(screen); }
    public static State state() { return state; }
    public static AquilaConfig config() { return cfg; }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fossilsolver")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(context -> { reset(); return status(); }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> color(StringArgumentType.getString(context, "target"), StringArgumentType.getString(context, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int option(String name, String raw) {
        Boolean value = parse(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.fossilSolverSuite = value;
            case "hud" -> cfg.fossilSolverHud = value;
            case "best" -> cfg.fossilShowBestHighlight = value;
            case "probabilities" -> cfg.fossilShowProbabilityHighlights = value;
            case "percent" -> cfg.fossilShowPercentageText = value;
            case "patterns" -> cfg.fossilShowPatterns = value;
            case "minimum" -> cfg.fossilShowMinimumTiles = value;
            case "charges" -> cfg.fossilShowCharges = value;
            case "types" -> cfg.fossilShowPossibleTypes = value;
            case "tooltip" -> cfg.fossilTooltipInfo = value;
            case "hidedirt" -> cfg.fossilHideDirtTooltips = value;
            case "hideall" -> cfg.fossilHideAllTooltips = value;
            case "protect" -> cfg.fossilProtectWrongClicks = value;
            case "bypass" -> cfg.fossilControlBypass = value;
            case "muncher" -> cfg.fossilMuncherSolver = value;
            case "replace" -> cfg.fossilMuncherReplaceRiddle = value;
            case "answer" -> cfg.fossilMuncherLocalAnswer = value;
            default -> { local("Unknown fossil-solver option."); return 0; }
        }
        save();
        return status();
    }

    private static int color(String target, String raw) {
        Integer value = parseColor(raw);
        if (value == null) { local("Color must be an eight-digit ARGB hex value."); return 0; }
        switch (target.toLowerCase(Locale.ROOT)) {
            case "best" -> cfg.fossilBestColor = value;
            case "probability" -> cfg.fossilProbabilityColor = value;
            case "impossible" -> cfg.fossilImpossibleColor = value;
            default -> { local("Color target must be best, probability, or impossible."); return 0; }
        }
        save();
        return status();
    }

    private static int status() {
        local("Solver " + on(cfg.fossilSolverSuite) + ", " + (state.visible ? state.status + ", " + state.patterns + " patterns" : "no active board") + ".");
        return 1;
    }

    private static List<Placement> placements() {
        List<Placement> out = new ArrayList<>();
        for (Shape shape : SHAPES) for (Turn turn : shape.turns) {
            boolean[][] grid = transform(shape.rows, turn);
            for (int y = 0; y <= 6 - grid.length; y++) for (int x = 0; x <= 9 - grid[0].length; x++)
                out.add(new Placement(shape, grid, x, y));
        }
        return List.copyOf(out);
    }

    private static boolean[][] transform(String[] rows, Turn turn) {
        boolean[][] grid = new boolean[rows.length][rows[0].length()];
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) grid[y][x] = rows[y].charAt(x) == '#';
        if (turn.ordinal() >= Turn.F0.ordinal()) grid = flip(grid);
        int rotations = turn.ordinal() % 4;
        for (int i = 0; i < rotations; i++) grid = rotate(grid);
        return grid;
    }

    private static boolean[][] flip(boolean[][] input) {
        boolean[][] out = new boolean[input.length][input[0].length];
        for (int y = 0; y < input.length; y++) for (int x = 0; x < input[y].length; x++) out[input.length - 1 - y][x] = input[y][x];
        return out;
    }

    private static boolean[][] rotate(boolean[][] input) {
        boolean[][] out = new boolean[input[0].length][input.length];
        for (int y = 0; y < input.length; y++) for (int x = 0; x < input[y].length; x++) out[x][input.length - 1 - y] = input[y][x];
        return out;
    }

    private static Shape shape(String name, String progress, int tiles, String[] rows, Turn... turns) {
        return new Shape(name, progress, rows, EnumSet.copyOf(List.of(turns)), tiles);
    }
    private static Start start(int x, int y, double chance, int patterns) { return new Start(y * 9 + x, chance, patterns); }
    private static String normalizeProgress(String value) { return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value; }
    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (RuntimeException ignored) { return fallback; } }
    private static void reset() { state = empty(); fingerprint = ""; maxCharges = 0; }
    private static State empty() { return new State(false, "", -1, 0, 0, 0, -1, "", List.of(), new double[54]); }
    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[Fossils] §f" + text));
    }
    private static String clean(String text) { return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim(); }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static Boolean parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static Integer parseColor(String raw) {
        try {
            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            return value.length() == 8 ? (int) Long.parseLong(value, 16) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
