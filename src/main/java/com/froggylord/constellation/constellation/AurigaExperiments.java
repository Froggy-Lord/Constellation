package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class AurigaExperiments {

    private static AurigaConfig cfg;
    // remember pairs as you click — highlight matches. no auto-click (like skyblocker).
    private static final Set<Integer> revealed = new HashSet<>();
    private static final Map<Integer, String> names = new HashMap<>();

    public static void init(AurigaConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            String title = cs.getTitle().getString();
            if (title.startsWith("Ultrasequencer")) {
                revealed.clear(); names.clear();
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                    if (ok(cfg.ultrasequencerSolver)) ultrasequencer(cs, g);
                });
            } else if (title.startsWith("Superpairs")) {
                revealed.clear(); names.clear();
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                    if (ok(cfg.superpairsSolver)) superpairs(cs, g);
                });
            }
        });
    }

    // show the next click in the sequence
    private static void ultrasequencer(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        int chest = cs.getMenu().slots.size() - 36;
        int last = -1;
        for (int i = 1; i < chest; i++) {
            ItemStack s = cs.getMenu().slots.get(i).getItem();
            if (s.isEmpty() || s.getDescriptionId().contains("stained_glass")) continue;
            if (last >= 0) {
                int prevVal = itemVal(cs.getMenu().slots.get(last).getItem());
                int curVal = itemVal(s.getItem());
                if (curVal < prevVal && curVal >= 0 && prevVal >= 0) {
                    box(cs, g, cs.getMenu().slots.get(last), 0xA0FF2020);
                    box(cs, g, cs.getMenu().slots.get(i), 0xA020FF20);
                }
            }
            last = i;
        }
    }

    private static int itemVal(ItemStack s) {
        String id = s.getDescriptionId();
        if (id.contains("0")) return 0;
        if (id.contains("1")) return 1;
        if (id.contains("2")) return 2;
        if (id.contains("3")) return 3;
        if (id.contains("4")) return 4;
        if (id.contains("5")) return 5;
        if (id.contains("6")) return 6;
        if (id.contains("7")) return 7;
        if (id.contains("8")) return 8;
        if (id.contains("9")) return 9;
        if (id.contains("10")) return 10;
        if (id.contains("11")) return 11;
        if (id.contains("12")) return 12;
        if (id.contains("13")) return 13;
        if (id.contains("14")) return 14;
        return -1;
    }

    // highlight matching pairs from memory — you click to reveal, we box matches. no auto-click.
    private static void superpairs(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        int chest = cs.getMenu().slots.size() - 36;
        // record what each slot currently shows
        for (int i = 0; i < chest; i++) {
            ItemStack s = cs.getMenu().slots.get(i).getItem();
            if (s.isEmpty()) { names.remove(i); revealed.remove(i); continue; }
            String id = s.getDescriptionId();
            // skip glass/clay spacers — hypixel fills empty slots with these
            if (id.contains("stained_glass") || id.contains("clay") || id.contains("glass_pane") || id.contains("terracotta")) { names.remove(i); continue; }
            String nm = net.minecraft.ChatFormatting.stripFormatting(s.getHoverName().getString());
            if (!nm.isEmpty()) { names.put(i, nm); revealed.add(i); }
        }
        // highlight pairs
        List<Integer> slots = new ArrayList<>(names.keySet());
        for (int a = 0; a < slots.size(); a++)
            for (int b = a + 1; b < slots.size(); b++) {
                String na = names.get(slots.get(a));
                String nb = names.get(slots.get(b));
                if (na != null && na.equals(nb)) {
                    box(cs, g, cs.getMenu().slots.get(slots.get(a)), 0x6020FF20);
                    box(cs, g, cs.getMenu().slots.get(slots.get(b)), 0x6020FF20);
                }
            }
    }

    private static boolean ok(boolean on) { return on && ConstellationClient.loc().onHypixel(); }

    private static void box(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Slot slot, int argb) {
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, argb);
    }
}
