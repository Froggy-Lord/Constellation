package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.core.LocationManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/experiment/ExperimentSolver.java,
// UltrasequencerSolver.java, SuperpairsSolver.java, ChronomatronSolver.java
// ported from NoFrills (GPL-3.0-only): features/solvers/ExperimentSolver.java
public final class AurigaExperiments {
    public enum Type { NONE, CHRONOMATRON, ULTRASEQUENCER, SUPERPAIRS }
    public enum Phase { REMEMBER, WAIT, SHOW, END }
    public record Status(Type type, Phase phase, int current, int total, int remembered) {}
    private record UltraStep(int slot, int number) {}

    private static AurigaConfig cfg;
    private static AbstractContainerScreen<?> activeScreen;
    private static Type type = Type.NONE;
    private static Phase phase = Phase.REMEMBER;
    private static final List<UltraStep> ultra = new ArrayList<>();
    private static int ultraOrdinal;
    private static final List<String> chrono = new ArrayList<>();
    private static int chronoShown;
    private static int chronoOrdinal;
    private static int chronoActiveSlot = -1;
    private static final Map<Integer, ItemStack> pairMemory = new HashMap<>();
    private static final Map<String, List<Integer>> pairGroups = new LinkedHashMap<>();
    private static int pairClicked = -1;
    private static String pairCurrent = "";
    private static boolean pairAwaitingSecond;

    private AurigaExperiments() {}

    public static void init(AurigaConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            Type found = identify(container.getTitle().getString());
            if (found == Type.NONE) return;
            reset(container, found);
            ScreenEvents.afterTick(screen).register(ignored -> tick(container));
            ScreenEvents.remove(screen).register(ignored -> clear());
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("experiments")
            .executes(c -> statusCommand())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(c -> {
                cfg.experimentSolver = !cfg.experimentSolver; save(); return statusCommand();
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c -> {
                if (activeScreen != null) reset(activeScreen, type); else clear();
                local("Experiment memory reset."); return 1;
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"),
                            StringArgumentType.getString(c, "state")))))));
    }

    private static void tick(AbstractContainerScreen<?> screen) {
        if (screen != activeScreen || !active()) return;
        if (screen.getMenu().slots.size() <= 49) return;
        String instruction = clean(screen.getMenu().getSlot(49).getItem().getHoverName().getString());
        switch (type) {
            case ULTRASEQUENCER -> tickUltra(screen, instruction);
            case CHRONOMATRON -> tickChrono(screen, instruction);
            case SUPERPAIRS -> tickPairs(screen);
            default -> {}
        }
    }

    private static void tickUltra(AbstractContainerScreen<?> screen, String instruction) {
        if (instruction.equals("Remember the pattern!")) {
            if (phase == Phase.SHOW || phase == Phase.END) {
                ultra.clear(); ultraOrdinal = 0;
            }
            phase = Phase.REMEMBER;
            Map<Integer, UltraStep> found = new HashMap<>();
            for (int i = 9; i <= 44; i++) {
                ItemStack stack = screen.getMenu().getSlot(i).getItem();
                String name = clean(stack.getHoverName().getString());
                if (!name.matches("\\d+")) continue;
                int number = Integer.parseInt(name);
                found.put(number, new UltraStep(i, number));
            }
            ultra.clear();
            ultra.addAll(found.values().stream().sorted(Comparator.comparingInt(UltraStep::number)).toList());
            phase = ultra.isEmpty() ? Phase.REMEMBER : Phase.WAIT;
        } else if (instruction.startsWith("Timer:")) {
            if (phase != Phase.SHOW) ultraOrdinal = 0;
            phase = Phase.SHOW;
        } else if (phase == Phase.SHOW) {
            phase = Phase.END;
        }
    }

    private static void tickChrono(AbstractContainerScreen<?> screen, String instruction) {
        if (instruction.equals("Remember the pattern!")) {
            if (phase != Phase.REMEMBER && phase != Phase.WAIT) {
                chronoShown = 0; chronoOrdinal = 0; chronoActiveSlot = -1;
            }
            phase = Phase.REMEMBER;
            int glowing = -1;
            for (int i = 17; i <= 34; i++) {
                ItemStack stack = screen.getMenu().getSlot(i).getItem();
                if (stack.hasFoil() && !colorKey(stack).isEmpty()) { glowing = i; break; }
            }
            if (glowing >= 0 && glowing != chronoActiveSlot) {
                String color = colorKey(screen.getMenu().getSlot(glowing).getItem());
                if (chronoShown >= chrono.size()) chrono.add(color);
                chronoShown++;
                chronoActiveSlot = glowing;
                phase = Phase.WAIT;
            } else if (glowing < 0) {
                chronoActiveSlot = -1;
            }
        } else if (instruction.startsWith("Timer:")) {
            if (phase != Phase.SHOW) chronoOrdinal = 0;
            phase = Phase.SHOW;
        } else if (phase == Phase.SHOW) {
            phase = Phase.END;
        }
    }

    private static void tickPairs(AbstractContainerScreen<?> screen) {
        phase = Phase.SHOW;
        if (!cfg.superpairsRememberItems) return;
        for (int i = 0; i < Math.min(45, screen.getMenu().slots.size()); i++) {
            ItemStack stack = screen.getMenu().getSlot(i).getItem();
            if (!isReward(stack)) continue;
            pairMemory.put(i, stack.copy());
        }
        rebuildPairs();
        if (pairAwaitingSecond && pairClicked >= 0 && pairMemory.containsKey(pairClicked))
            pairCurrent = identity(pairMemory.get(pairClicked));
    }

    private static void rebuildPairs() {
        pairGroups.clear();
        for (Map.Entry<Integer, ItemStack> entry : pairMemory.entrySet())
            pairGroups.computeIfAbsent(identity(entry.getValue()), ignored -> new ArrayList<>()).add(entry.getKey());
    }

    public static boolean shouldBlockClick(AbstractContainerScreen<?> screen, Slot slot, int slotId) {
        if (!active() || !enabled(type) || screen != activeScreen || slot == null || slotId < 0 || slotId >= 45) return false;
        if (cfg.experimentControlBypass && controlHeld()) return false;
        return switch (type) {
            case ULTRASEQUENCER -> ultraClick(slotId);
            case CHRONOMATRON -> chronoClick(slot);
            case SUPERPAIRS -> pairClick(slotId);
            default -> false;
        };
    }

    private static boolean ultraClick(int slotId) {
        if (phase != Phase.SHOW) return cfg.experimentBlockIncorrectClicks && cfg.experimentBlockEarlyClicks;
        UltraStep next = ultraOrdinal < ultra.size() ? ultra.get(ultraOrdinal) : null;
        if (next == null || slotId != next.slot()) return cfg.experimentBlockIncorrectClicks;
        ultraOrdinal++;
        if (ultraOrdinal >= ultra.size()) phase = Phase.END;
        return false;
    }

    private static boolean chronoClick(Slot slot) {
        if (phase != Phase.SHOW) return cfg.experimentBlockIncorrectClicks && cfg.experimentBlockEarlyClicks;
        String next = chronoOrdinal < chrono.size() ? chrono.get(chronoOrdinal) : "";
        if (next.isEmpty() || !next.equals(colorKey(slot.getItem()))) return cfg.experimentBlockIncorrectClicks;
        chronoOrdinal++;
        if (chronoOrdinal >= chrono.size()) phase = Phase.END;
        return false;
    }

    private static boolean pairClick(int slotId) {
        if (pairAwaitingSecond && cfg.superpairsBlockKnownWrongSecond
            && !pairCurrent.isEmpty() && pairMemory.containsKey(slotId)
            && !pairCurrent.equals(identity(pairMemory.get(slotId)))) return true;
        if (pairAwaitingSecond) {
            pairAwaitingSecond = false;
            pairClicked = -1;
            pairCurrent = "";
        } else {
            pairAwaitingSecond = true;
            pairClicked = slotId;
            pairCurrent = pairMemory.containsKey(slotId) ? identity(pairMemory.get(slotId)) : "";
        }
        return false;
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!active() || !enabled(type) || screen != activeScreen || slot == null || slot.index >= 45) return;
        int color = 0;
        String label = "";
        if (type == Type.ULTRASEQUENCER && phase == Phase.SHOW) {
            int index = indexOfUltra(slot.index);
            if (index >= ultraOrdinal) {
                int relative = index - ultraOrdinal;
                color = relative == 0 && cfg.experimentShowNext ? cfg.experimentNextColor
                    : relative == 1 && cfg.experimentShowSecond ? cfg.experimentSecondColor
                    : relative > 1 && cfg.experimentShowRemaining ? cfg.experimentLaterColor : 0;
                if (cfg.experimentShowNumbers) label = Integer.toString(ultra.get(index).number());
            } else if (cfg.experimentDimWrong) color = cfg.experimentWrongColor;
        } else if (type == Type.CHRONOMATRON && phase == Phase.SHOW) {
            String key = colorKey(slot.getItem());
            int index = chrono.indexOf(key);
            if (index >= chronoOrdinal) {
                int relative = index - chronoOrdinal;
                color = relative == 0 && cfg.experimentShowNext ? cfg.experimentNextColor
                    : relative == 1 && cfg.experimentShowSecond ? cfg.experimentSecondColor
                    : relative > 1 && cfg.experimentShowRemaining ? cfg.experimentLaterColor : 0;
                if (cfg.experimentShowNumbers) label = Integer.toString(index + 1);
            } else if (cfg.experimentDimWrong && slot.index >= 17 && slot.index <= 34) color = cfg.experimentWrongColor;
        } else if (type == Type.SUPERPAIRS) {
            ItemStack remembered = pairMemory.get(slot.index);
            if (remembered != null) {
                String id = identity(remembered);
                if (cfg.superpairsHighlightCurrentMatch && !pairCurrent.isEmpty() && pairCurrent.equals(id) && slot.index != pairClicked)
                    color = cfg.superpairsCurrentMatchColor;
                else if (cfg.superpairsHighlightKnownPairs && pairGroups.getOrDefault(id, List.of()).size() >= 2)
                    color = cfg.superpairsKnownPairColor;
                if (cfg.superpairsHighlightPowerups && isPowerup(remembered)) color = cfg.superpairsPowerupColor;
            }
        }
        if (color != 0) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
        if (!label.isEmpty()) graphics.text(Minecraft.getInstance().font, label, slot.x + 4, slot.y + 4, 0xFFFFFFFF, true);
    }

    public static Status status() {
        int current = switch (type) { case ULTRASEQUENCER -> ultraOrdinal; case CHRONOMATRON -> chronoOrdinal; default -> 0; };
        int total = switch (type) { case ULTRASEQUENCER -> ultra.size(); case CHRONOMATRON -> chrono.size(); default -> pairGroups.size(); };
        int remembered = type == Type.SUPERPAIRS ? pairMemory.size() : total;
        return new Status(type, phase, current, total, remembered);
    }
    public static AurigaConfig config() { return cfg; }
    public static boolean visible() { return active() && type != Type.NONE && enabled(type); }
    public static boolean shouldHideTooltip(AbstractContainerScreen<?> screen) {
        return active() && enabled(type) && cfg.experimentHideTooltips && screen == activeScreen
            && (type == Type.CHRONOMATRON || type == Type.ULTRASEQUENCER);
    }

    private static int indexOfUltra(int slot) {
        for (int i = 0; i < ultra.size(); i++) if (ultra.get(i).slot() == slot) return i;
        return -1;
    }
    private static Type identify(String title) {
        if (title.matches("^Chronomatron \\(\\w+\\)$")) return Type.CHRONOMATRON;
        if (title.matches("^Ultrasequencer \\(\\w+\\)$")) return Type.ULTRASEQUENCER;
        if (title.matches("^Superpairs \\(\\w+\\)$")) return Type.SUPERPAIRS;
        return Type.NONE;
    }
    private static void reset(AbstractContainerScreen<?> screen, Type found) {
        clear(); activeScreen = screen; type = found;
        phase = found == Type.SUPERPAIRS ? Phase.SHOW : Phase.REMEMBER;
    }
    private static void clear() {
        activeScreen = null; type = Type.NONE; phase = Phase.REMEMBER;
        ultra.clear(); ultraOrdinal = 0; chrono.clear(); chronoShown = 0; chronoOrdinal = 0; chronoActiveSlot = -1;
        pairMemory.clear(); pairGroups.clear(); pairClicked = -1; pairCurrent = ""; pairAwaitingSecond = false;
    }
    private static boolean active() {
        if (cfg == null || !cfg.enabled || !cfg.experimentSolver) return false;
        if (cfg.experimentHypixelOnly && !ConstellationClient.loc().onHypixel()) return false;
        return !cfg.experimentPrivateIslandOnly || ConstellationClient.loc().area() == LocationManager.SkyblockArea.PRIVATE_ISLAND;
    }
    private static boolean enabled(Type value) {
        return switch (value) {
            case CHRONOMATRON -> cfg.chronomatronSolver;
            case ULTRASEQUENCER -> cfg.ultrasequencerSolver;
            case SUPERPAIRS -> cfg.superpairsSolver;
            default -> false;
        };
    }
    private static String colorKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        int split = id.indexOf(':'); if (split >= 0) id = id.substring(split + 1);
        for (String suffix : List.of("_stained_glass_pane", "_stained_glass", "_terracotta"))
            if (id.endsWith(suffix)) return id.substring(0, id.length() - suffix.length());
        return "";
    }
    private static boolean isReward(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String name = clean(stack.getHoverName().getString());
        return !name.isEmpty() && !name.startsWith("Click ") && !name.startsWith("Timer:")
            && !name.equals("Remember the pattern!") && !id.contains("stained_glass") && !id.contains("glowstone")
            && !id.contains("clock") && !id.contains("cauldron");
    }
    private static String identity(ItemStack stack) {
        return stack.getItem().toString() + "|" + clean(stack.getHoverName().getString()) + "|" + stack.getCount();
    }
    private static boolean isPowerup(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore != null && lore.lines().stream().anyMatch(line -> clean(line.getString()).toLowerCase(Locale.ROOT).contains("powerup"));
    }
    private static boolean controlHeld() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
            || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
    private static String clean(String value) {
        String stripped = net.minecraft.ChatFormatting.stripFormatting(value);
        return stripped == null ? "" : stripped.trim();
    }
    private static int statusCommand() {
        local("Solver " + on(cfg.experimentSolver) + ", Chronomatron " + on(cfg.chronomatronSolver)
            + ", Ultrasequencer " + on(cfg.ultrasequencerSolver) + ", Superpairs " + on(cfg.superpairsSolver) + ".");
        return 1;
    }
    private static int option(String name, String raw) {
        Boolean value = bool(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.experimentSolver = value;
            case "chrono", "chronomatron" -> cfg.chronomatronSolver = value;
            case "ultra", "ultrasequencer" -> cfg.ultrasequencerSolver = value;
            case "pairs", "superpairs" -> cfg.superpairsSolver = value;
            case "hud" -> cfg.experimentHud = value;
            case "hypixel" -> cfg.experimentHypixelOnly = value;
            case "island" -> cfg.experimentPrivateIslandOnly = value;
            case "block" -> cfg.experimentBlockIncorrectClicks = value;
            case "bypass" -> cfg.experimentControlBypass = value;
            case "early" -> cfg.experimentBlockEarlyClicks = value;
            case "tooltips" -> cfg.experimentHideTooltips = value;
            case "next" -> cfg.experimentShowNext = value;
            case "second" -> cfg.experimentShowSecond = value;
            case "remaining" -> cfg.experimentShowRemaining = value;
            case "dim" -> cfg.experimentDimWrong = value;
            case "numbers" -> cfg.experimentShowNumbers = value;
            case "rememberpairs" -> cfg.superpairsRememberItems = value;
            case "knownpairs" -> cfg.superpairsHighlightKnownPairs = value;
            case "currentmatch" -> cfg.superpairsHighlightCurrentMatch = value;
            case "powerups" -> cfg.superpairsHighlightPowerups = value;
            case "blockpairs" -> cfg.superpairsBlockKnownWrongSecond = value;
            default -> { local("Unknown experiment option."); return 0; }
        }
        save(); return statusCommand();
    }
    private static Boolean bool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a75[Experiments] \u00a7f" + text));
    }
    private static void save() { ConstellationClient.saveConfig(); }
}
