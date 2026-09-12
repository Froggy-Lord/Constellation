package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Full Spirit Leap replacement. The server container remains authoritative: every card retains
 * its original slot and a deliberate mouse/key action sends the same vanilla container click.
 *
 * Layout, sorting modes, static-role slots, background and press/release behavior ported from
 * Devonian (GPL-3.0): features/dungeons/CustomLeapGui.kt
 * Class-key fallback and dead-state checks ported from NoFrills (GPL-3.0):
 * features/dungeons/LeapOverlay.java
 */
public final class OrionSpiritLeap {

    public static final String[] SORT_NAMES = {"class a-z", "class z-a", "name a-z", "name z-a", "dynamic", "custom"};
    private static final String[] CLASSES = {"Archer", "Berserk", "Healer", "Mage", "Tank"};
    private static final String[] ROLE_ORDER = {"Healer", "Tank", "Mage", "Berserk", "Archer"};
    private static final int[] DEFAULT_KEYS = {
        GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5
    };
    private static final KeyMapping[] leapKeys = new KeyMapping[CLASSES.length];
    private static final Map<String, DynamicRole> DYNAMIC = Map.of(
        "Archer", new DynamicRole(0, 2), "Berserk", new DynamicRole(1, 0),
        "Healer", new DynamicRole(2, 2), "Mage", new DynamicRole(3, 2),
        "Tank", new DynamicRole(3, 1));
    private static OrionConfig cfg;
    private static boolean initialized;
    private static List<Card> visibleCards = List.of();
    private static AbstractContainerScreen<?> visibleScreen;
    private static boolean actionSent;

    private record LeapPlayer(int slot, String name, String role, boolean alive, ItemStack head) {}
    private record Card(int x, int y, int width, int height, LeapPlayer player) {}
    private record DynamicRole(int slot, int priority) {}

    private OrionSpiritLeap() {}

    public static void init(OrionConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        var keys = ConstellationClient.instance().keys();
        for (int i = 0; i < CLASSES.length; i++)
            leapKeys[i] = keys.register(DungeonClassInfo.keyId(CLASSES[i]), DEFAULT_KEYS[i]);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !validTitle(container.getTitle().getString())) return;
            actionSent = false;

            ScreenEvents.remove(screen).register(ignored -> clearScreen(container));
            ScreenEvents.afterExtract(screen).register((ignored, graphics, mouseX, mouseY, delta) -> {
                if (!enabled()) return;
                if (cfg.spiritLeapCustomGui) drawCustom(container, graphics, mouseX, mouseY);
                else drawOverlay(container, graphics);
            });

            ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) -> mouse(container, event, true));
            ScreenMouseEvents.allowMouseRelease(screen).register((ignored, event) -> mouse(container, event, false));
            ScreenMouseEvents.allowMouseDrag(screen).register((ignored, event, deltaX, deltaY) -> !enabled() || !cfg.spiritLeapCustomGui);
            ScreenMouseEvents.allowMouseScroll(screen).register((ignored, x, y, horizontal, vertical) -> !enabled() || !cfg.spiritLeapCustomGui);
            ScreenKeyboardEvents.allowKeyRelease(screen).register((ignored, event) -> !enabled() || !cfg.spiritLeapCustomGui);
            ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, event) -> {
                if (!enabled()) return true;
                if (event.key() == GLFW.GLFW_KEY_ESCAPE || Minecraft.getInstance().options.keyInventory.matches(event)) return true;
                for (int i = 0; i < leapKeys.length; i++) {
                    if (cfg.spiritLeapKeybinds && leapKeys[i].matches(event)) {
                        if (!actionSent && !leapToClass(container.getMenu(), CLASSES[i]))
                            message("No living " + CLASSES[i] + " target is available.");
                        return false;
                    }
                }
                if (!cfg.spiritLeapCustomGui) return true;
                return false;
            });
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var fourth = RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("bottom_right", StringArgumentType.word())
            .executes(context -> order(
                StringArgumentType.getString(context, "top_left"),
                StringArgumentType.getString(context, "top_right"),
                StringArgumentType.getString(context, "bottom_left"),
                StringArgumentType.getString(context, "bottom_right")));
        var third = RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("bottom_left", StringArgumentType.word()).then(fourth);
        var second = RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("top_right", StringArgumentType.word()).then(third);
        var first = RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("top_left", StringArgumentType.word()).then(second);
        var command = LiteralArgumentBuilder.<FabricClientCommandSource>literal("leapgui")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("mode", IntegerArgumentType.integer(0, 5))
                    .executes(context -> sort(IntegerArgumentType.getInteger(context, "mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scale")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("percent", IntegerArgumentType.integer(50, 200))
                    .executes(context -> scale(IntegerArgumentType.getInteger(context, "percent")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("background")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                    .executes(context -> background(StringArgumentType.getString(context, "argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("order").then(first));
        dispatcher.register(command);
    }

    private static boolean enabled() {
        return cfg != null && cfg.enabled && cfg.spiritLeapHelper && ConstellationClient.loc().inDungeons();
    }

    public static boolean shouldReplace(AbstractContainerScreen<?> screen) {
        return enabled() && cfg.spiritLeapCustomGui && validTitle(screen.getTitle().getString());
    }

    private static boolean validTitle(String title) {
        return title.equals("Spirit Leap") || title.equals("Teleport to Player");
    }

    // ported from Devonian (GPL-3.0): features/dungeons/CustomLeapGui.kt
    private static void drawCustom(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        graphics.fill(0, 0, screenWidth, screenHeight, 0x55000000);
        List<LeapPlayer> players = sortedPlayers(screen);
        int scale = Math.clamp(cfg.spiritLeapScalePercent, 50, 200);
        int cardWidth = 128 * scale / 100;
        int cardHeight = 58 * scale / 100;
        int gapX = Math.max(4, 8 * scale / 100);
        int gapY = Math.max(4, 8 * scale / 100);
        int startX = (screenWidth - cardWidth * 2 - gapX) / 2;
        int startY = (screenHeight - cardHeight * 2 - gapY) / 2;
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < Math.min(4, players.size()); i++) {
            LeapPlayer player = players.get(i);
            int x = startX + (i % 2) * (cardWidth + gapX);
            int y = startY + (i / 2) * (cardHeight + gapY);
            Card card = new Card(x, y, cardWidth, cardHeight, player);
            cards.add(card);
            drawCard(graphics, card, mouseX, mouseY);
        }
        visibleCards = List.copyOf(cards);
        visibleScreen = screen;
        String title = "Spirit Leap";
        graphics.text(mc.font, title, (screenWidth - mc.font.width(title)) / 2, startY - 19, 0xFFFFFFFF, true);
        if (players.stream().noneMatch(player -> player.slot() >= 0)) {
            String loading = "Waiting for teammates...";
            graphics.text(mc.font, loading, (screenWidth - mc.font.width(loading)) / 2,
                screenHeight / 2 - mc.font.lineHeight / 2, 0xFFAAAAAA, true);
        }
    }

    private static void drawCard(GuiGraphicsExtractor graphics, Card card, int mouseX, int mouseY) {
        LeapPlayer player = card.player();
        boolean placeholder = player.slot() < 0;
        boolean hover = !placeholder && player.alive() && inside(mouseX, mouseY, card);
        int roleColour = DungeonClassInfo.colour(player.role());
        if (roleColour == 0) roleColour = 0xFFAAAAAA;
        int background = placeholder ? withAlpha(cfg.spiritLeapBackground, 45)
            : hover ? brighten(cfg.spiritLeapBackground) : cfg.spiritLeapBackground;
        if (!player.alive() && !placeholder) background = 0xDC181818;
        graphics.fill(card.x(), card.y(), card.x() + card.width(), card.y() + card.height(), background);
        border(graphics, card.x(), card.y(), card.width(), card.height(),
            placeholder ? 0x50666666 : withAlpha(roleColour, hover ? 255 : 170), hover ? 2 : 1);

        Font font = Minecraft.getInstance().font;
        String name = placeholder ? "Empty" : player.name();
        int nameColour = !player.alive() && !placeholder ? 0xFF777777 : 0xFFFFFFFF;
        graphics.text(font, fit(name, card.width() - 12, font),
            card.x() + 6, card.y() + card.height() / 2 - font.lineHeight, nameColour, true);
        if (cfg.spiritLeapShowClass) {
            String role = player.role().isBlank() ? "Unknown" : player.role();
            graphics.text(font, role, card.x() + 6, card.y() + card.height() / 2 + 3, roleColour, true);
        }
        if (!player.alive() && !placeholder && cfg.spiritLeapShowDead) {
            String dead = "DEAD";
            graphics.text(font, dead, card.x() + card.width() - font.width(dead) - 6,
                card.y() + card.height() - font.lineHeight - 5, 0xFFFF5555, true);
        }
    }

    private static List<LeapPlayer> sortedPlayers(AbstractContainerScreen<?> screen) {
        List<LeapPlayer> real = new ArrayList<>(readPlayers(screen.getMenu()));
        if (!cfg.spiritLeapShowDead) real.removeIf(player -> !player.alive());
        List<LeapPlayer> working = new ArrayList<>(real);
        String ownRole = ConstellationClient.dungeon().playerClass();
        if (cfg.spiritLeapStaticSlots && real.size() < 4 && !ownRole.isBlank()
            && real.stream().allMatch(player -> !player.role().isBlank())
            && real.stream().map(LeapPlayer::role).filter(role -> !role.isBlank()).distinct().count() == real.size()) {
            Set<String> present = new HashSet<>();
            real.forEach(player -> present.add(player.role()));
            for (String role : ROLE_ORDER) {
                if (!role.equals(ownRole) && !present.contains(role))
                    working.add(new LeapPlayer(-1, "", role, false, ItemStack.EMPTY));
            }
        }

        int mode = Math.clamp(cfg.spiritLeapSorting, 0, 5);
        Comparator<LeapPlayer> classSort = Comparator.comparing((LeapPlayer player) -> player.role().isBlank() ? "Z" : player.role())
            .thenComparing(player -> player.name().toLowerCase(Locale.ROOT));
        switch (mode) {
            case 0 -> working.sort(classSort);
            case 1 -> working.sort(classSort.reversed());
            case 2 -> working.sort(Comparator.comparing((LeapPlayer player) -> player.slot() < 0 ? "~" + player.role() : player.name().toLowerCase(Locale.ROOT)));
            case 3 -> {
                List<LeapPlayer> realPlayers = working.stream().filter(player -> player.slot() >= 0)
                    .sorted(Comparator.comparing((LeapPlayer player) -> player.name().toLowerCase(Locale.ROOT)).reversed()).toList();
                List<LeapPlayer> placeholders = working.stream().filter(player -> player.slot() < 0)
                    .sorted(Comparator.comparing(LeapPlayer::role)).toList();
                working = new ArrayList<>(realPlayers);
                working.addAll(placeholders);
            }
            case 4 -> working = dynamicSort(working, ownRole);
            case 5 -> working = customSort(working);
            default -> {}
        }
        return working;
    }

    // ported from Devonian (GPL-3.0): features/dungeons/CustomLeapGui.kt dynamicSorting
    private static List<LeapPlayer> dynamicSort(List<LeapPlayer> players, String ownRole) {
        if (ownRole.isBlank()) {
            players.sort(Comparator.comparing(LeapPlayer::role).thenComparing(LeapPlayer::name));
            return players;
        }
        LeapPlayer[] slots = new LeapPlayer[4];
        List<LeapPlayer> leftovers = new ArrayList<>();
        for (LeapPlayer player : players) {
            DynamicRole preference = DYNAMIC.get(player.role());
            if (preference != null && preference.slot() < slots.length && slots[preference.slot()] == null)
                slots[preference.slot()] = player;
            else leftovers.add(player);
        }
        leftovers.sort(Comparator.comparingInt((LeapPlayer player) ->
            DYNAMIC.getOrDefault(player.role(), new DynamicRole(0, -1)).priority()).reversed());
        int next = 0;
        for (LeapPlayer player : leftovers) {
            while (next < slots.length && slots[next] != null) next++;
            if (next < slots.length) slots[next] = player;
        }
        List<LeapPlayer> result = new ArrayList<>();
        for (LeapPlayer player : slots) if (player != null) result.add(player);
        return result;
    }

    private static List<LeapPlayer> customSort(List<LeapPlayer> players) {
        if (cfg.spiritLeapCustomOrder.isEmpty()) {
            players.sort(Comparator.comparing(player -> player.name().toLowerCase(Locale.ROOT)));
            return players;
        }
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < cfg.spiritLeapCustomOrder.size(); i++)
            order.put(cfg.spiritLeapCustomOrder.get(i).toLowerCase(Locale.ROOT), i);
        LeapPlayer[] fixed = new LeapPlayer[4];
        List<LeapPlayer> remaining = new ArrayList<>();
        for (LeapPlayer player : players) {
            Integer index = order.get(player.name().toLowerCase(Locale.ROOT));
            if (index != null && index >= 0 && index < fixed.length && fixed[index] == null) fixed[index] = player;
            else if (player.slot() >= 0) remaining.add(player);
        }
        remaining.sort(Comparator.comparing(player -> player.name().toLowerCase(Locale.ROOT)));
        for (int i = 0; i < fixed.length; i++) {
            if (fixed[i] == null && !remaining.isEmpty()) fixed[i] = remaining.removeFirst();
            if (fixed[i] == null) fixed[i] = new LeapPlayer(-1, "", "", false, ItemStack.EMPTY);
        }
        return new ArrayList<>(List.of(fixed));
    }

    private static List<LeapPlayer> readPlayers(AbstractContainerMenu menu) {
        List<LeapPlayer> result = new ArrayList<>();
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) continue;
            String name = cleanName(stack.getHoverName().getString());
            if (name.equalsIgnoreCase("Haunt") || name.isBlank()) continue;
            String lore = loreText(stack);
            String role = resolveClass(ConstellationClient.dungeon().classOf(name), lore);
            boolean alive = lore.contains("Click to teleport");
            result.add(new LeapPlayer(i, name, role, alive, stack));
        }
        return result;
    }

    private static boolean mouse(AbstractContainerScreen<?> screen, MouseButtonEvent event, boolean press) {
        if (!enabled() || !cfg.spiritLeapCustomGui) return true;
        if (actionSent || event.button() < 0 || event.button() > 1) return false;
        if (cfg.spiritLeapClickOnPress != press) return false;
        if (visibleScreen != screen) return false;
        for (Card card : visibleCards) {
            if (!inside((int) event.x(), (int) event.y(), card)) continue;
            LeapPlayer player = card.player();
            if (player.slot() >= 0 && player.alive()) click(screen.getMenu(), player.slot());
            return false;
        }
        return false;
    }

    private static void drawOverlay(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        int left = ((ContainerScreenAccessor) screen).constellation$left();
        int top = ((ContainerScreenAccessor) screen).constellation$top();
        Font font = Minecraft.getInstance().font;
        for (LeapPlayer player : readPlayers(screen.getMenu())) {
            Slot slot = screen.getMenu().slots.get(player.slot());
            int colour = player.alive() ? DungeonClassInfo.colour(player.role()) : 0xFF777777;
            if (colour == 0) colour = 0xFFFFFFFF;
            int x = left + slot.x, y = top + slot.y;
            graphics.fill(x, y, x + 16, y + 16, withAlpha(colour, 55));
            border(graphics, x, y, 16, 16, colour, 1);
            graphics.text(font, String.valueOf(DungeonClassInfo.initial(player.role())), x + 1, y + 1, colour, true);
        }
    }

    private static boolean leapToClass(AbstractContainerMenu menu, String targetClass) {
        for (LeapPlayer player : readPlayers(menu)) {
            if (player.alive() && targetClass.equals(player.role())) {
                click(menu, player.slot());
                return true;
            }
        }
        return false;
    }

    private static void click(AbstractContainerMenu menu, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (actionSent || mc.gameMode == null || mc.player == null || slot < 0 || slot >= menu.slots.size()) return;
        actionSent = true;
        visibleCards = List.of();
        mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        menu.setCarried(ItemStack.EMPTY);
    }

    private static String cleanName(String name) {
        String clean = name.replaceAll("§.", "").trim();
        clean = clean.replaceFirst("^\\[[^ ]+]\\s+", "");
        return clean;
    }

    private static String resolveClass(String fromTab, String freeText) {
        return fromTab == null || fromTab.isBlank() ? DungeonClassInfo.fromText(freeText) : fromTab;
    }

    private static String loreText(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return "";
        StringBuilder value = new StringBuilder();
        for (var line : lore.lines()) value.append(line.getString()).append('\n');
        return value.toString();
    }

    private static boolean inside(int x, int y, Card card) {
        return x >= card.x() && x < card.x() + card.width() && y >= card.y() && y < card.y() + card.height();
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int colour, int thickness) {
        graphics.fill(x, y, x + width, y + thickness, colour);
        graphics.fill(x, y + height - thickness, x + width, y + height, colour);
        graphics.fill(x, y, x + thickness, y + height, colour);
        graphics.fill(x + width - thickness, y, x + width, y + height, colour);
    }

    private static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
    }

    private static int brighten(int colour) {
        int alpha = (colour >>> 24) & 0xFF;
        int red = Math.min(255, ((colour >>> 16) & 0xFF) + 24);
        int green = Math.min(255, ((colour >>> 8) & 0xFF) + 24);
        int blue = Math.min(255, (colour & 0xFF) + 24);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static String fit(String value, int width, Font font) {
        if (font.width(value) <= width) return value;
        String shortened = value;
        while (!shortened.isEmpty() && font.width(shortened + "...") > width)
            shortened = shortened.substring(0, shortened.length() - 1);
        return shortened + "...";
    }

    private static void clearScreen(AbstractContainerScreen<?> screen) {
        if (visibleScreen != screen) return;
        visibleScreen = null;
        visibleCards = List.of();
        actionSent = false;
    }

    private static int status() {
        message("Leap GUI: " + (cfg.spiritLeapCustomGui ? "custom" : "overlay") + ", sort "
            + Math.clamp(cfg.spiritLeapSorting, 0, 5) + " (" + SORT_NAMES[Math.clamp(cfg.spiritLeapSorting, 0, 5)]
            + "), scale " + Math.clamp(cfg.spiritLeapScalePercent, 50, 200) + "%");
        message("Use /leapgui sort <0-5>, scale <50-200>, background <AARRGGBB>, or order <four names>.");
        return 1;
    }

    private static int sort(int mode) {
        cfg.spiritLeapSorting = mode;
        ConstellationClient.saveConfig();
        return status();
    }

    private static int scale(int percent) {
        cfg.spiritLeapScalePercent = percent;
        ConstellationClient.saveConfig();
        return status();
    }

    private static int background(String raw) {
        try {
            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            if (value.length() == 6) value = "E6" + value;
            if (value.length() != 8) throw new NumberFormatException();
            cfg.spiritLeapBackground = (int) Long.parseLong(value, 16);
            ConstellationClient.saveConfig();
            message("Leap GUI background set to " + String.format(Locale.ROOT, "%08X", cfg.spiritLeapBackground));
            return 1;
        } catch (NumberFormatException ignored) {
            message("Use RRGGBB or AARRGGBB.");
            return 0;
        }
    }

    private static int order(String a, String b, String c, String d) {
        cfg.spiritLeapCustomOrder = new ArrayList<>(List.of(a, b, c, d));
        cfg.spiritLeapSorting = 5;
        ConstellationClient.saveConfig();
        message("Custom leap order saved: " + String.join(", ", cfg.spiritLeapCustomOrder));
        return 1;
    }

    private static void message(String text) {
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§b[Orion] §f" + text));
    }
}
