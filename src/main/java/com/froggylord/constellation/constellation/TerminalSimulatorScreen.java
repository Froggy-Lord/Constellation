package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

// ported from Athen (BSD-3-Clause): modules/impl/dungeon/terminals/simulator/base/ITerminalSim.kt
// ported from Athen (BSD-3-Clause): modules/impl/dungeon/terminals/simulator/base/SimulatorMenu.kt
// ported from Athen (BSD-3-Clause): modules/impl/dungeon/terminals/simulator/impl/*.kt
public final class TerminalSimulatorScreen extends ContainerScreen {
    // 26.2 exposes block-items through the registry instead of Items constants
    private static final class Items {
        private static final Item AIR = item("air");
        private static final Item APPLE = item("apple");
        private static final Item STONE = item("stone");
        private static final Item BONE_MEAL = item("bone_meal");
        private static final Item COCOA_BEANS = item("cocoa_beans");
        private static final Item INK_SAC = item("ink_sac");
        private static final Item LAPIS_LAZULI = item("lapis_lazuli");
        private static final Item BLACK_STAINED_GLASS_PANE = item("black_stained_glass_pane");
        private static final Item BLUE_STAINED_GLASS_PANE = item("blue_stained_glass_pane");
        private static final Item BROWN_STAINED_GLASS_PANE = item("brown_stained_glass_pane");
        private static final Item CYAN_STAINED_GLASS_PANE = item("cyan_stained_glass_pane");
        private static final Item GREEN_STAINED_GLASS_PANE = item("green_stained_glass_pane");
        private static final Item LIME_STAINED_GLASS_PANE = item("lime_stained_glass_pane");
        private static final Item MAGENTA_STAINED_GLASS_PANE = item("magenta_stained_glass_pane");
        private static final Item ORANGE_STAINED_GLASS_PANE = item("orange_stained_glass_pane");
        private static final Item PINK_STAINED_GLASS_PANE = item("pink_stained_glass_pane");
        private static final Item PURPLE_STAINED_GLASS_PANE = item("purple_stained_glass_pane");
        private static final Item RED_STAINED_GLASS_PANE = item("red_stained_glass_pane");
        private static final Item WHITE_STAINED_GLASS_PANE = item("white_stained_glass_pane");
        private static final Item YELLOW_STAINED_GLASS_PANE = item("yellow_stained_glass_pane");
        private static final Item LIME_TERRACOTTA = item("lime_terracotta");
        private static final Item RED_TERRACOTTA = item("red_terracotta");
    }

    private enum Type {
        MENU(27), PANES(45), RUBIX(45), NUMBERS(36), STARTS_WITH(45), SELECT_ALL(54), MELODY(54);

        final int size;
        Type(int size) { this.size = size; }
    }

    private record Setup(SimpleContainer container, Inventory inventory, ChestMenu menu) {}

    private static final List<Item> RUBIX = List.of(
        Items.ORANGE_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE,
        Items.GREEN_STAINED_GLASS_PANE, Items.BLUE_STAINED_GLASS_PANE,
        Items.RED_STAINED_GLASS_PANE);
    private static final List<String> LETTERS = List.of("A", "B", "C", "D", "E", "G", "M", "N", "R", "S", "T", "W");

    private final Type type;
    private final SimpleContainer simulated;
    private final String targetLetter;
    private final DyeColor targetColor;
    private int melodyTarget;
    private int melodyPointer;
    private int melodyDirection = 1;
    private int melodyRow = 1;
    private int melodyTicks;

    private TerminalSimulatorScreen(Type type, String letter, DyeColor color) {
        this(type, letter, color, setup(type));
    }

    private TerminalSimulatorScreen(Type type, String letter, DyeColor color, Setup setup) {
        super(setup.menu(), setup.inventory(), Component.literal(title(type, letter, color)));
        this.type = type;
        this.simulated = setup.container();
        this.targetLetter = letter;
        this.targetColor = color;
        create();
    }

    public static void openMenu() {
        var cfg = ConstellationClient.cfg().orion;
        Minecraft mc = Minecraft.getInstance();
        if (cfg == null || !cfg.terminalSimulator || mc.player == null) return;
        mc.setScreenAndShow(new TerminalSimulatorScreen(Type.MENU, "", DyeColor.WHITE));
    }

    private static Setup setup(Type type) {
        Minecraft mc = Minecraft.getInstance();
        Inventory inventory = mc.player.getInventory();
        SimpleContainer simulated = new SimpleContainer(type.size);
        MenuType<?> menuType = switch (type.size) {
            case 27 -> MenuType.GENERIC_9x3;
            case 36 -> MenuType.GENERIC_9x4;
            case 45 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        return new Setup(simulated, inventory,
            new ChestMenu(menuType, -2, inventory, simulated, type.size / 9));
    }

    private static String title(Type type, String letter, DyeColor color) {
        return switch (type) {
            case MENU -> "Terminal Simulator";
            case PANES -> "Correct all the panes!";
            case RUBIX -> "Change all to same color!";
            case NUMBERS -> "Click in order!";
            case STARTS_WITH -> "What starts with: '" + letter + "'?";
            case SELECT_ALL -> "Select all the " + color.getName().replace("light_gray", "silver").replace('_', ' ') + " items!";
            case MELODY -> "Click the button on time!";
        };
    }

    private void create() {
        for (int i = 0; i < type.size; i++) set(i, pane(Items.BLACK_STAINED_GLASS_PANE));
        switch (type) {
            case MENU -> createMenu();
            case PANES -> createPanes();
            case RUBIX -> createRubix();
            case NUMBERS -> createNumbers();
            case STARTS_WITH -> createStartsWith();
            case SELECT_ALL -> createSelectAll();
            case MELODY -> createMelody();
        }
    }

    private void createMenu() {
        set(10, named(Items.LIME_STAINED_GLASS_PANE, "Panes"));
        set(11, named(Items.RED_STAINED_GLASS_PANE, "Rubix"));
        set(12, named(Items.CYAN_STAINED_GLASS_PANE, "Numbers"));
        set(13, named(Items.WHITE_STAINED_GLASS_PANE, "Random"));
        set(14, named(Items.PINK_STAINED_GLASS_PANE, "Starts With"));
        set(15, named(Items.BROWN_STAINED_GLASS_PANE, "Select All"));
        set(16, named(Items.PURPLE_STAINED_GLASS_PANE, "Melody"));
    }

    private void createPanes() {
        for (int row = 1; row <= 3; row++) for (int col = 2; col <= 6; col++)
            set(row * 9 + col, pane(random(4) == 0 ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE));
    }

    private void createRubix() {
        for (int row = 1; row <= 3; row++) for (int col = 3; col <= 5; col++)
            set(row * 9 + col, pane(RUBIX.get(random(RUBIX.size()))));
    }

    private void createNumbers() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= 14; i++) numbers.add(i);
        Collections.shuffle(numbers);
        int n = 0;
        for (int row = 1; row <= 2; row++) for (int col = 1; col <= 7; col++) {
            int value = numbers.get(n++);
            ItemStack stack = named(Items.RED_STAINED_GLASS_PANE, Integer.toString(value));
            stack.setCount(value);
            set(row * 9 + col, stack);
        }
    }

    private void createStartsWith() {
        List<Item> matching = letterItems(true);
        List<Item> other = letterItems(false);
        int guaranteed = boardSlots(3).get(random(boardSlots(3).size()));
        for (int slot : boardSlots(3)) {
            List<Item> pool = slot == guaranteed || random(10) < 3 ? matching : other;
            set(slot, new ItemStack(pool.get(random(pool.size()))));
        }
    }

    private void createSelectAll() {
        List<Item> matching = coloredItems(targetColor);
        List<Integer> slots = boardSlots(4);
        int guaranteed = slots.get(random(slots.size()));
        DyeColor[] colors = DyeColor.values();
        for (int slot : slots) {
            List<Item> pool;
            if (slot == guaranteed || random(4) == 0) pool = matching;
            else {
                DyeColor other;
                do other = colors[random(colors.length)]; while (other == targetColor);
                pool = coloredItems(other);
            }
            set(slot, new ItemStack(pool.get(random(pool.size()))));
        }
    }

    private void createMelody() {
        melodyTarget = 1 + random(5);
        melodyPointer = 1;
        melodyDirection = 1;
        melodyRow = 1;
        melodyTicks = 0;
        updateMelody();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (type != Type.MELODY || melodyTicks++ % 10 != 0) return;
        melodyPointer += melodyDirection;
        if (melodyPointer == 1 || melodyPointer == 5) melodyDirection *= -1;
        updateMelody();
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        if (slot == null || slot.container != simulated || slotId < 0 || slotId >= type.size) return;
        if (slot.getItem().is(Items.BLACK_STAINED_GLASS_PANE)) return;
        switch (type) {
            case MENU -> menuClick(slotId);
            case PANES -> panesClick(slotId);
            case RUBIX -> rubixClick(slotId, button);
            case NUMBERS -> numbersClick(slotId);
            case STARTS_WITH -> startsWithClick(slotId);
            case SELECT_ALL -> selectAllClick(slotId);
            case MELODY -> melodyClick(slotId);
        }
    }

    @Override
    public void onClose() {
        // AbstractContainerScreen closes the player's server container; this fake screen must stay local
        if (minecraft != null) minecraft.gui.setScreen(null);
    }

    private void menuClick(int slot) {
        Type next = switch (slot) {
            case 10 -> Type.PANES;
            case 11 -> Type.RUBIX;
            case 12 -> Type.NUMBERS;
            case 14 -> Type.STARTS_WITH;
            case 15 -> Type.SELECT_ALL;
            case 16 -> Type.MELODY;
            case 13 -> Type.values()[1 + random(Type.values().length - 1)];
            default -> null;
        };
        if (next != null) open(next);
    }

    private void panesClick(int slot) {
        Item item = simulated.getItem(slot).getItem();
        set(slot, pane(item == Items.RED_STAINED_GLASS_PANE ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE));
        if (none(Items.RED_STAINED_GLASS_PANE)) complete();
        else sound();
    }

    private void rubixClick(int slot, int button) {
        int current = RUBIX.indexOf(simulated.getItem(slot).getItem());
        if (current < 0) return;
        int direction = button == 1 ? -1 : 1;
        set(slot, pane(RUBIX.get(Math.floorMod(current + direction, RUBIX.size()))));
        Item first = simulated.getItem(12).getItem();
        for (int row = 1; row <= 3; row++) for (int col = 3; col <= 5; col++)
            if (simulated.getItem(row * 9 + col).getItem() != first) { sound(); return; }
        complete();
    }

    private void numbersClick(int slot) {
        ItemStack clicked = simulated.getItem(slot);
        if (!clicked.is(Items.RED_STAINED_GLASS_PANE)) return;
        int smallest = Integer.MAX_VALUE;
        for (int i = 0; i < type.size; i++) {
            ItemStack stack = simulated.getItem(i);
            if (stack.is(Items.RED_STAINED_GLASS_PANE)) smallest = Math.min(smallest, stack.getCount());
        }
        if (clicked.getCount() != smallest) return;
        ItemStack done = pane(Items.LIME_STAINED_GLASS_PANE);
        done.setCount(clicked.getCount());
        set(slot, done);
        if (none(Items.RED_STAINED_GLASS_PANE)) complete();
        else sound();
    }

    private void startsWithClick(int slot) {
        ItemStack stack = simulated.getItem(slot);
        if (!stack.getHoverName().getString().toUpperCase(Locale.ROOT).startsWith(targetLetter) || stack.hasFoil()) return;
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        for (int board : boardSlots(3)) {
            ItemStack other = simulated.getItem(board);
            if (other.getHoverName().getString().toUpperCase(Locale.ROOT).startsWith(targetLetter) && !other.hasFoil()) { sound(); return; }
        }
        complete();
    }

    private void selectAllClick(int slot) {
        ItemStack stack = simulated.getItem(slot);
        List<Item> matching = coloredItems(targetColor);
        if (!matching.contains(stack.getItem()) || stack.hasFoil()) return;
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        for (int board : boardSlots(4)) {
            ItemStack other = simulated.getItem(board);
            if (matching.contains(other.getItem()) && !other.hasFoil()) { sound(); return; }
        }
        complete();
    }

    private void melodyClick(int slot) {
        if (slot % 9 != 7 || slot / 9 != melodyRow || melodyPointer != melodyTarget) return;
        melodyRow++;
        if (melodyRow >= 5) { complete(); return; }
        melodyTarget = 1 + random(5);
        updateMelody();
        sound();
    }

    private void updateMelody() {
        for (int i = 0; i < type.size; i++) set(i, pane(Items.BLACK_STAINED_GLASS_PANE));
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 5; col++) set(row * 9 + col,
                pane(row == melodyRow && col == melodyPointer ? Items.LIME_STAINED_GLASS_PANE
                    : row == melodyRow ? Items.RED_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE));
            set(row * 9 + 7, pane(row == melodyRow ? Items.LIME_TERRACOTTA : Items.RED_TERRACOTTA));
        }
        set(melodyTarget, pane(Items.MAGENTA_STAINED_GLASS_PANE));
    }

    private void complete() {
        sound();
        Minecraft.getInstance().execute(TerminalSimulatorScreen::openMenu);
    }

    private void open(Type next) {
        String letter = next == Type.STARTS_WITH ? LETTERS.get(random(LETTERS.size())) : "";
        DyeColor[] colors = DyeColor.values();
        DyeColor color = next == Type.SELECT_ALL ? colors[random(colors.length)] : DyeColor.WHITE;
        Minecraft.getInstance().setScreenAndShow(new TerminalSimulatorScreen(next, letter, color));
    }

    private List<Item> letterItems(boolean matching) {
        List<Item> result = BuiltInRegistries.ITEM.stream().filter(item -> {
            if (item == Items.AIR) return false;
            String name = new ItemStack(item).getHoverName().getString().toUpperCase(Locale.ROOT);
            return !name.contains("PANE") && name.startsWith(targetLetter) == matching;
        }).toList();
        return result.isEmpty() ? List.of(matching ? Items.APPLE : Items.STONE) : result;
    }

    private static List<Item> coloredItems(DyeColor color) {
        String name = color.getName();
        List<Item> result = new ArrayList<>();
        result.add(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name + "_stained_glass")));
        result.add(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name + "_wool")));
        result.add(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name + "_concrete")));
        result.add(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name + "_dye")));
        if (color == DyeColor.WHITE) result.add(Items.BONE_MEAL);
        else if (color == DyeColor.BLUE) result.add(Items.LAPIS_LAZULI);
        else if (color == DyeColor.BLACK) result.add(Items.INK_SAC);
        else if (color == DyeColor.BROWN) result.add(Items.COCOA_BEANS);
        return result;
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
    }

    private static List<Integer> boardSlots(int rows) {
        List<Integer> result = new ArrayList<>();
        for (int row = 1; row <= rows; row++) for (int col = 1; col <= 7; col++) result.add(row * 9 + col);
        return result;
    }

    private boolean none(Item item) {
        for (int i = 0; i < type.size; i++) if (simulated.getItem(i).is(item)) return false;
        return true;
    }

    private void set(int slot, ItemStack stack) {
        simulated.setItem(slot, stack);
    }

    private static ItemStack pane(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.empty());
        return stack;
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static int random(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    private static void sound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, .35f, 1f);
    }
}
