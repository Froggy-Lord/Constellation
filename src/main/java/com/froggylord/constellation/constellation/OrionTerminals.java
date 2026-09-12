package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F7/M7 phase-3 terminal solvers. Advisory only — highlights the correct slot(s)
 * on the open terminal GUI; it never auto-clicks. Optional click-blocking (see
 * {@link #shouldBlockClick}) cancels clicks on non-solution slots, fail-open.
 *
 * Solver logic ported from Skyblocker (skyblock/dungeon/terminal/*) for Select,
 * In Order, Same Color, Correct-the-panes and Starts-With, and from Odin's
 * MelodyHandler (BSD-3) for Melody. Screen-overlay render reuses the
 * ContainerScreenAccessor + GuiGraphicsExtractor pattern established by
 * OrionPuzzles.
 */
public class OrionTerminals {

    private OrionTerminals() {}

    private static OrionConfig cfg;
    private static boolean melodyOpen;
    private static int lastMelodyProgress = -1;

    private enum Type { SELECT, SAME_COLOR, IN_ORDER, PANES, STARTS_WITH, MELODY, NONE }

    private static final Pattern LETTER = Pattern.compile("'?([A-Za-z])'?\\??\\s*$");
    private static final Pattern COLOUR = Pattern.compile("Select all the (.+?) items!?");

    // the 4 button slots in the melody terminal (column 7 of rows 1-4)
    private static final int[] MELODY_BUTTONS = {16, 25, 34, 43};
    private static final int[] RUBIX_SLOTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};

    public static void init(OrionConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            Type openedType = typeOf(cs.getTitle().getString());
            if (openedType == Type.MELODY && cfg != null && inGoldor()) {
                melodyOpen = true;
                lastMelodyProgress = -1;
                PartyMessages.send("melody-start");
            } else if (openedType != Type.MELODY) {
                melodyOpen = false;
                lastMelodyProgress = -1;
            }
            ScreenEvents.afterExtract(screen).register((scr, g, mx, my, delta) -> {
                if (cfg == null || !cfg.terminalSolvers) return;
                if (!ConstellationClient.loc().inDungeons()) return;
                try { solve(cs, g); } catch (Exception ignored) {}
            });
        });
    }

    private static Type typeOf(String title) {
        if (title.startsWith("Correct all the panes")) return Type.PANES;
        if (title.startsWith("Click in order")) return Type.IN_ORDER;
        if (title.startsWith("What starts with")) return Type.STARTS_WITH;
        if (title.startsWith("Select all the")) return Type.SELECT;
        if (title.startsWith("Change all to same color")) return Type.SAME_COLOR;
        if (title.startsWith("Click the button on time")) return Type.MELODY;
        return Type.NONE;
    }

    private static boolean inGoldor() {
        var dungeon = ConstellationClient.dungeon();
        return ConstellationClient.loc().inDungeons() && dungeon.inBoss()
            && dungeon.floor().endsWith("7") && "Goldor".equals(dungeon.bossPhase());
    }

    // ------------------------------------------------------------------ render

    private static void solve(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        if (!inGoldor()) return;
        String title = cs.getTitle().getString();
        Type type = typeOf(title);
        if (type == Type.NONE) return;

        var menu = cs.getMenu();
        int total = menu.slots.size();
        int chest = total - 36;
        if (chest <= 0) return;

        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        Font font = Minecraft.getInstance().font;

        switch (type) {
            case SELECT -> {
                String want = selectColour(title);
                if (want == null) return;
                for (int i = 0; i < chest; i++) {
                    ItemStack s = menu.slots.get(i).getItem();
                    if (s.isEmpty() || s.hasFoil()) continue;
                    if (matchesColour(s, want))
                        box(g, menu.slots.get(i), left, top, 0x9020FF20);
                }
            }
            case PANES -> {
                // "Correct all the panes!" — click every red pane to turn it green
                for (int i = 0; i < chest; i++) {
                    ItemStack s = menu.slots.get(i).getItem();
                    if (isPane(s, "red"))
                        box(g, menu.slots.get(i), left, top, 0x9020FF20);
                }
            }
            case IN_ORDER -> {
                int next = orderNext(menu, chest); // count of already-clicked (lime) panes
                for (int i = 0; i < chest; i++) {
                    ItemStack s = menu.slots.get(i).getItem();
                    if (!isPane(s, "red")) continue;
                    int n = s.getCount();
                    if (cfg.terminalNumbers)
                        number(g, font, menu.slots.get(i), left, top, n);
                    // fade the next three: brightest is the one to click now
                    int rank = n - (next + 1);
                    if (rank >= 0 && rank < 3) {
                        int alpha = 0xE0 - 0x50 * rank;
                        box(g, menu.slots.get(i), left, top, (alpha << 24) | 0x20FF20);
                    }
                }
            }
            case STARTS_WITH -> {
                char letter = startsLetter(title);
                if (letter == 0) return;
                for (int i = 0; i < chest; i++) {
                    ItemStack s = menu.slots.get(i).getItem();
                    if (s.isEmpty() || s.hasFoil()) continue;
                    if (nameStartsWith(s, letter))
                        box(g, menu.slots.get(i), left, top, 0x9020FF20);
                }
            }
            case SAME_COLOR -> {
                int target = sameColourTarget(menu, chest);
                if (target < 0) return;
                for (int i = 0; i < chest; i++) {
                    ItemStack s = menu.slots.get(i).getItem();
                    int col = paneCycleIndex(s);
                    if (col < 0) continue;
                    int clicks = sameColourClicks(col, target);
                    if (clicks == 0) continue;
                    boolean left2 = clicks > 0;
                    box(g, menu.slots.get(i), left, top, left2 ? 0x6020FF20 : 0x60FF8020);
                    number(g, font, menu.slots.get(i), left, top, Math.abs(clicks));
                }
            }
            case MELODY -> melodyOverlay(cs, g, chest, left, top);
            default -> { }
        }
    }

    // ported from Odin (BSD-3): src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/terminals/terminalhandler/MelodyHandler.kt
    private static void melodyOverlay(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g,
                                      int chest, int left, int top) {
        var menu = cs.getMenu();
        // the magenta pane marks the target column; the last lime pane is the
        // moving pointer; the last lime terracotta is the active button to press.
        int magenta = -1, pointer = -1, button = -1;
        for (int i = 0; i < chest; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (magenta < 0 && isPane(s, "magenta")) magenta = i;
            if (isPane(s, "lime")) pointer = i;
            if (isTerracotta(s, "lime")) button = i;
        }
        if (magenta < 0 || button < 0) return;

        // always show the target-column marker so the note is readable
        box(g, menu.slots.get(magenta), left, top, 0x80FF20FF);

        // press now when the pointer's column lines up with the target column
        boolean onTime = pointer >= 0 && pointer % 9 == magenta % 9;
        // bright green = press now, dim amber = the button but not yet aligned
        box(g, menu.slots.get(button), left, top, onTime ? 0xC020FF20 : 0x50FFB020);

        if (cfg.melodyTerminalHelper) {
            int progress = melodyProgress(menu, chest);
            if (progress >= 0) {
                if (melodyOpen && progress > 0 && progress > lastMelodyProgress) {
                    lastMelodyProgress = progress;
                    PartyMessages.send("melody-progress", java.util.Map.of("progress", progress));
                } else if (lastMelodyProgress < 0) {
                    lastMelodyProgress = progress;
                }
                String status = "Melody " + progress + "%" + (onTime ? " - press" : "");
                g.text(Minecraft.getInstance().font, status, left + 8, top - 11,
                    onTime ? 0xFF55FF55 : 0xFFFFFFFF, true);
            }
        }
    }

    // ported from NoFrills (GPL-3.0): features/dungeons/MelodyMessage.java
    private static int melodyProgress(net.minecraft.world.inventory.AbstractContainerMenu menu, int chest) {
        if (chest != 54) return -1;
        int terracotta = 0;
        for (int i = chest - 1; i >= 0; i--) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (isTerracotta(stack, "red") || isTerracotta(stack, "lime")) terracotta++;
            if (isTerracotta(stack, "lime")) {
                return switch (terracotta) {
                    case 1 -> 75;
                    case 2 -> 50;
                    case 3 -> 25;
                    case 4 -> 0;
                    default -> -1;
                };
            }
        }
        return -1;
    }

    // ------------------------------------------------------------- click block

    /**
     * Returns true if a click on {@code slotId} should be cancelled. Advisory,
     * fail-open: only returns true when the terminal type and the wrong-slot
     * condition are both certain from the live menu; any doubt returns false so
     * a legitimate click is never swallowed.
     */
    public static boolean shouldBlockClick(AbstractContainerScreen<?> cs, int slotId, int button) {
        if (cfg == null || !cfg.terminalSolvers || !inGoldor()) return false;

        Type type = typeOf(cs.getTitle().getString());
        if (type == Type.NONE) return false;

        var menu = cs.getMenu();
        int chest = menu.slots.size() - 36;
        if (chest <= 0 || slotId < 0 || slotId >= chest) return false; // never touch inventory clicks

        ItemStack stack = menu.slots.get(slotId).getItem();

        try {
            boolean wrong = switch (type) {
                case SELECT -> {
                    String want = selectColour(cs.getTitle().getString());
                    if (want == null || stack.isEmpty()) yield false;
                    yield stack.hasFoil() || !matchesColour(stack, want);
                }
                case PANES ->
                    // only wrong click is a pane that is already green
                    isPane(stack, "lime");
                case IN_ORDER -> {
                    if (!orderReady(menu, chest)) yield false; // fail-open until board is fully sent
                    int next = orderNext(menu, chest);
                    yield !(isPane(stack, "red") && stack.getCount() == next + 1);
                }
                case STARTS_WITH -> {
                    char letter = startsLetter(cs.getTitle().getString());
                    if (letter == 0 || stack.isEmpty()) yield false;
                    yield !nameStartsWith(stack, letter);
                }
                case SAME_COLOR -> {
                    int target = sameColourTarget(menu, chest);
                    int col = paneCycleIndex(stack);
                    if (target < 0 || col < 0) yield false;
                    int clicks = sameColourClicks(col, target);
                    if (clicks == 0) yield true;
                    // ported from Devonian (GPL-3.0): features/dungeons/solvers/TerminalSolvers.kt RUBIX.cancelClick
                    yield cfg.terminalRubixBlockBadDirection
                        && ((clicks > 0 && button != 0) || (clicks < 0 && button != 1));
                }
                case MELODY -> {
                    // the only valid targets are the four buttons; timing is fail-open
                    for (int b : MELODY_BUTTONS) if (b == slotId) yield false;
                    yield true;
                }
                default -> false;
            };
            return wrong && (cfg.blockWrongTerminalClicks
                || type == Type.SAME_COLOR && cfg.terminalRubixBlockBadDirection);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean active(AbstractContainerScreen<?> cs) {
        return cfg != null && cfg.terminalSolvers && inGoldor() && typeOf(cs.getTitle().getString()) != Type.NONE;
    }

    // ported from Devonian (GPL-3.0): features/dungeons/solvers/TerminalSolvers.kt TooltipRenderEvent
    public static boolean shouldHideTooltip(AbstractContainerScreen<?> cs) {
        return active(cs) && cfg.terminalDisableTooltips;
    }

    public static boolean shouldHideLabels(AbstractContainerScreen<?> cs) {
        return active(cs) && cfg.terminalHideLabels;
    }

    // ported from Devonian (GPL-3.0): features/dungeons/solvers/TerminalSolvers.kt onRenderSlot
    public static boolean beforeRenderSlot(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Slot slot) {
        if (!active(cs)) return false;
        int chest = cs.getMenu().slots.size() - 36;
        int slotId = cs.getMenu().slots.indexOf(slot);
        if (slotId < 0 || slotId >= chest) return false;
        if (cfg.terminalSlotBackground)
            g.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, cfg.terminalSlotBackgroundColour);
        if (!cfg.terminalHideDone && !cfg.terminalHideItems) return false;

        Type type = typeOf(cs.getTitle().getString());
        if (type == Type.SAME_COLOR && sameColourTarget(cs.getMenu(), chest) < 0) return false;
        boolean renderable = isRenderable(cs, slotId);
        if (cfg.terminalHideDone && type != Type.MELODY && !renderable) return true;
        return cfg.terminalHideItems && renderable && (type == Type.SELECT || type == Type.STARTS_WITH);
    }

    // ported from Devonian (GPL-3.0): api/ScreenUtils.kt and TerminalSolvers.kt middleClick/drop handlers
    public static boolean shouldMiddleClick(AbstractContainerScreen<?> cs, int slotId, int button,
                                            net.minecraft.world.inventory.ContainerInput input) {
        if (!active(cs) || typeOf(cs.getTitle().getString()) == Type.SAME_COLOR) return false;
        int chest = cs.getMenu().slots.size() - 36;
        if (slotId < 0 || slotId >= chest) return false;
        return cfg.terminalMiddleClick && input == net.minecraft.world.inventory.ContainerInput.PICKUP && button == 0
            || cfg.terminalDropKeyClick && input == net.minecraft.world.inventory.ContainerInput.THROW;
    }

    public static void playClickSound(AbstractContainerScreen<?> cs, int slotId, int button) {
        if (!active(cs) || !cfg.terminalClickSounds || !isSolution(cs, slotId, button)) return;
        var player = Minecraft.getInstance().player;
        if (player != null) player.playSound(SoundEvents.BLAZE_HURT,
            Math.clamp(cfg.terminalClickSoundVolume, 0.0f, 1.0f), 1.0f);
    }

    private static boolean isSolution(AbstractContainerScreen<?> cs, int slotId, int button) {
        Type type = typeOf(cs.getTitle().getString());
        var menu = cs.getMenu();
        int chest = menu.slots.size() - 36;
        if (slotId < 0 || slotId >= chest) return false;
        ItemStack stack = menu.slots.get(slotId).getItem();
        try {
            return switch (type) {
                case SELECT -> {
                    String want = selectColour(cs.getTitle().getString());
                    yield want != null && !stack.isEmpty() && !stack.hasFoil() && matchesColour(stack, want);
                }
                case PANES -> isPane(stack, "red");
                case IN_ORDER -> orderReady(menu, chest) && isPane(stack, "red")
                    && stack.getCount() == orderNext(menu, chest) + 1;
                case STARTS_WITH -> {
                    char letter = startsLetter(cs.getTitle().getString());
                    yield letter != 0 && !stack.isEmpty() && !stack.hasFoil() && nameStartsWith(stack, letter);
                }
                case SAME_COLOR -> {
                    int target = sameColourTarget(menu, chest);
                    int colour = paneCycleIndex(stack);
                    if (target < 0 || colour < 0) yield false;
                    int clicks = sameColourClicks(colour, target);
                    yield clicks != 0 && (!cfg.terminalRubixBlockBadDirection
                        || clicks > 0 && button == 0 || clicks < 0 && button == 1);
                }
                case MELODY -> {
                    boolean buttonSlot = false;
                    for (int candidate : MELODY_BUTTONS) if (candidate == slotId) buttonSlot = true;
                    yield buttonSlot;
                }
                default -> false;
            };
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isRenderable(AbstractContainerScreen<?> cs, int slotId) {
        Type type = typeOf(cs.getTitle().getString());
        var menu = cs.getMenu();
        int chest = menu.slots.size() - 36;
        if (slotId < 0 || slotId >= chest) return false;
        ItemStack stack = menu.slots.get(slotId).getItem();
        return switch (type) {
            case SELECT -> {
                String want = selectColour(cs.getTitle().getString());
                yield want != null && !stack.isEmpty() && !stack.hasFoil() && matchesColour(stack, want);
            }
            case PANES -> isPane(stack, "red");
            case IN_ORDER -> isPane(stack, "red");
            case STARTS_WITH -> {
                char letter = startsLetter(cs.getTitle().getString());
                yield letter != 0 && !stack.isEmpty() && !stack.hasFoil() && nameStartsWith(stack, letter);
            }
            case SAME_COLOR -> {
                int target = sameColourTarget(menu, chest);
                int colour = paneCycleIndex(stack);
                yield target >= 0 && colour >= 0 && sameColourClicks(colour, target) != 0;
            }
            case MELODY -> true;
            default -> false;
        };
    }

    // --------------------------------------------------------------- per-type helpers

    private static String selectColour(String title) {
        Matcher m = COLOUR.matcher(title);
        if (!m.find()) return null;
        String raw = m.group(1).trim().toUpperCase(Locale.ROOT);
        if (raw.equals("SILVER")) return "light_gray";
        return raw.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static char startsLetter(String title) {
        Matcher m = LETTER.matcher(title);
        if (!m.find()) return 0;
        return Character.toLowerCase(m.group(1).charAt(0));
    }

    private static boolean nameStartsWith(ItemStack s, char letter) {
        String name = s.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
        return !name.isEmpty() && name.charAt(0) == letter;
    }

    /** Count of panes already turned lime (== index of the next number to click). */
    private static int orderNext(net.minecraft.world.inventory.AbstractContainerMenu menu, int chest) {
        int lime = 0;
        for (int i = 0; i < chest; i++) if (isPane(menu.slots.get(i).getItem(), "lime")) lime++;
        return lime;
    }

    /** True once all 14 order panes (red or lime) are present — the board is fully synced. */
    private static boolean orderReady(net.minecraft.world.inventory.AbstractContainerMenu menu, int chest) {
        int panes = 0;
        for (int i = 0; i < chest; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (isPane(s, "red") || isPane(s, "lime")) panes++;
        }
        return panes == 14;
    }

    // Same Color cycle: red - orange - yellow - green - blue (cyclic, mod 5).
    private static final String[] CYCLE = {"red", "orange", "yellow", "green", "blue"};

    private static int paneCycleIndex(ItemStack s) {
        for (int i = 0; i < CYCLE.length; i++) if (isPane(s, CYCLE[i])) return i;
        return -1;
    }

    // ported from Devonian (GPL-3.0): features/dungeons/solvers/TerminalSolvers.kt RUBIX.onTick
    /** Minimum-total-click target, or -1 until all nine board panes are synced. */
    private static int sameColourTarget(net.minecraft.world.inventory.AbstractContainerMenu menu, int chest) {
        int[] colours = new int[RUBIX_SLOTS.length];
        for (int i = 0; i < RUBIX_SLOTS.length; i++) {
            int slot = RUBIX_SLOTS[i];
            if (slot >= chest) return -1;
            colours[i] = paneCycleIndex(menu.slots.get(slot).getItem());
            if (colours[i] < 0) return -1;
        }
        int bestTarget = -1;
        int bestClicks = Integer.MAX_VALUE;
        for (int target = 0; target < CYCLE.length; target++) {
            int total = 0;
            for (int colour : colours) {
                int forward = Math.floorMod(target - colour, CYCLE.length);
                int backward = Math.floorMod(colour - target, CYCLE.length);
                total += Math.min(forward, backward);
            }
            if (total < bestClicks) {
                bestClicks = total;
                bestTarget = target;
            }
        }
        return bestTarget;
    }

    /** Signed click count: +n = n left-clicks forward, -n = n right-clicks back. */
    private static int sameColourClicks(int color, int target) {
        int n = CYCLE.length;
        int forward = Math.floorMod(target - color, n);
        int backward = Math.floorMod(color - target, n);
        return forward <= backward ? forward : -backward;
    }

    // --------------------------------------------------------------- item helpers

    private static boolean matchesColour(ItemStack s, String want) {
        String id = id(s);
        // special-cased dye items that carry a colour but not its name
        switch (want) {
            case "white" -> { if (id.contains("bone_meal")) return true; }
            case "blue" -> { if (id.contains("lapis_lazuli")) return true; }
            case "brown" -> { if (id.contains("cocoa_beans")) return true; }
            case "black" -> { if (id.contains("ink_sac")) return true; }
            default -> { }
        }
        if (!id.contains(want)) return false;
        // don't let "blue"/"gray" swallow "light_blue"/"light_gray"
        if ((want.equals("blue") || want.equals("gray")) && id.contains("light_" + want)) return false;
        return true;
    }

    private static boolean isPane(ItemStack s, String colour) {
        if (s.isEmpty()) return false;
        String id = id(s);
        if (!id.contains(colour + "_stained_glass_pane")) return false;
        if ((colour.equals("blue") || colour.equals("gray")) && id.contains("light_" + colour + "_stained_glass_pane"))
            return false;
        return true;
    }

    private static boolean isTerracotta(ItemStack s, String colour) {
        if (s.isEmpty()) return false;
        return id(s).contains(colour + "_terracotta");
    }

    private static String id(ItemStack s) { return s.getItem().getDescriptionId(); }

    // --------------------------------------------------------------- draw helpers

    private static void box(GuiGraphicsExtractor g, Slot slot, int left, int top, int argb) {
        int x = left + slot.x, y = top + slot.y;
        g.fill(x, y, x + 16, y + 16, argb);
    }

    private static void number(GuiGraphicsExtractor g, Font font, Slot slot, int left, int top, int n) {
        g.text(font, Integer.toString(n), left + slot.x + 1, top + slot.y + 1, 0xFFFFFFFF, true);
    }
}
