package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Experimentation-table solvers (the enchanting-island minigames). Same highlight-only deal as
 * the dungeon terminals — we only paint a hint over the slot, the clicking is all you.
 *  - Ultrasequencer: the clocks are numbered, so light up the lowest one left.
 *  - Superpairs: we remember every card we've seen flipped this round and, once both halves of a
 *    pair are known, box them so you can grab the match.
 */
public class AurigaExperiments {

    private static AurigaConfig cfg;

    public static void init(AurigaConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            String title = cs.getTitle().getString();
            if (title.startsWith("Ultrasequencer")) {
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                    if (ok(cfg != null && cfg.ultrasequencerSolver)) ultrasequencer(cs, g);
                });
            } else if (title.startsWith("Superpairs")) {
                Map<Integer, String> seen = new HashMap<>();
                Set<Integer> locked = new HashSet<>();
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                    if (ok(cfg != null && cfg.superpairsSolver)) superpairs(cs, g, seen, locked);
                });
            }
        });
    }

    private static boolean ok(boolean toggle) {
        return toggle && ConstellationClient.loc().onHypixel();
    }

    private static void ultrasequencer(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        var menu = cs.getMenu();
        int chest = menu.slots.size() - 36;
        int best = -1, low = Integer.MAX_VALUE;
        for (int i = 0; i < chest; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty() || !s.getItem().getDescriptionId().contains("clock")) continue;
            int c = s.getCount();
            if (c > 0 && c < low) { low = c; best = i; }
        }
        if (best >= 0) box(cs, g, menu.slots.get(best), 0xA020FF20);
    }

    private static void superpairs(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Map<Integer, String> seen, Set<Integer> locked) {
        var menu = cs.getMenu();
        int chest = menu.slots.size() - 36;
        // record whatever's face-up this frame; covers are enchanted-glass panes or clay
        for (int i = 0; i < chest; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) continue;
            String id = s.getItem().getDescriptionId();
            // cover cards: stained glass, hardened clay, plain clay
            if (id.contains("stained_glass") || id.contains("clay") || id.contains("glass_pane") || id.contains("terracotta")) continue;
            seen.put(i, s.getHoverName().getString());
            // first time we see this slot face-up, click it to lock it open permanently
            if (!locked.contains(i)) {
                locked.add(i);
                try {
                    var mc = Minecraft.getInstance();
                    if (mc.gameMode != null && mc.player != null)
                        mc.gameMode.handleContainerInput(cs.getMenu().containerId, i, 0, ContainerInput.PICKUP, mc.player);
                } catch (Exception ignored) {}
            }
        }
        // box any pair we've now seen both halves of
        for (var a : seen.entrySet()) {
            for (var b : seen.entrySet()) {
                if (a.getKey() < b.getKey() && a.getValue().equals(b.getValue())) {
                    box(cs, g, menu.slots.get(a.getKey()), 0x80FFD020);
                    box(cs, g, menu.slots.get(b.getKey()), 0x80FFD020);
                }
            }
        }
    }

    private static void box(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Slot slot, int argb) {
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, argb);
    }
}
