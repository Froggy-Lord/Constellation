package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The F7/M7 phase-3 terminals. We only ever paint a hint over the slot you're meant to click —
 * nothing reaches in and clicks for you. Reading is done off the open chest's slots each frame.
 */
public class OrionTerminals {

    private static OrionConfig cfg;

    private static final Pattern LETTER = Pattern.compile("letter '?([A-Za-z])'?");
    private static final Pattern COLOUR = Pattern.compile("all the ([A-Z ]+?) (?:items|item)");

    // the 16 vanilla dye colours as they appear inside item ids
    private static final String[] COLOURS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    public static void init(OrionConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            ScreenEvents.afterExtract(screen).register((scr, g, mx, my, delta) -> {
                if (cfg == null || !cfg.terminalSolvers) return;
                if (!ConstellationClient.loc().inDungeons()) return;
                try { solve(cs, g); } catch (Exception ignored) {}
            });
        });
    }

    private static void solve(AbstractContainerScreen<?> cs, net.minecraft.client.gui.GuiGraphicsExtractor g) {
        String title = cs.getTitle().getString();
        var menu = cs.getMenu();
        int total = menu.slots.size();
        int chest = total - 36; // last 36 slots are always the player inventory
        if (chest <= 0) return;

        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        var font = Minecraft.getInstance().font;

        if (title.startsWith("Click in order") || title.contains("Melody")) {
            // sequential: the next click is the lowest-numbered red pane left
            int best = -1, bestCount = Integer.MAX_VALUE;
            for (int i = 0; i < chest; i++) {
                ItemStack s = menu.slots.get(i).getItem();
                if (s.isEmpty()) continue;
                if (!id(s).contains("red_stained_glass")) continue;
                int c = s.getCount();
                if (c < bestCount) { bestCount = c; best = i; }
            }
            for (int i = 0; i < chest; i++) {
                ItemStack s = menu.slots.get(i).getItem();
                if (s.isEmpty()) continue;
                if (!id(s).contains("stained_glass")) continue;
                if (cfg.terminalNumbers && id(s).contains("red_stained_glass"))
                    number(g, font, menu.slots.get(i), left, top, s.getCount());
            }
            if (best >= 0) box(g, menu.slots.get(best), left, top, 0xA020FF20);
            return;
        }

        if (title.startsWith("Correct all") || title.startsWith("Change all to same")) {
            // order doesn't matter — every red pane still needs clicking through to green
            for (int i = 0; i < chest; i++) {
                ItemStack s = menu.slots.get(i).getItem();
                if (s.isEmpty()) continue;
                if (id(s).contains("red_stained_glass"))
                    box(g, menu.slots.get(i), left, top, 0x9020FF20);
            }
            return;
        }

        Matcher cm = COLOUR.matcher(title);
        if (cm.find()) {
            String want = cm.group(1).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            for (int i = 0; i < chest; i++) {
                ItemStack s = menu.slots.get(i).getItem();
                if (s.isEmpty() || s.hasFoil()) continue; // foil = already selected
                if (matchesColour(id(s), want))
                    box(g, menu.slots.get(i), left, top, 0x9020FF20);
            }
            return;
        }

        Matcher lm = LETTER.matcher(title);
        if (lm.find()) {
            char letter = Character.toLowerCase(lm.group(1).charAt(0));
            for (int i = 0; i < chest; i++) {
                ItemStack s = menu.slots.get(i).getItem();
                if (s.isEmpty() || s.hasFoil()) continue;
                String name = s.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
                if (!name.isEmpty() && name.charAt(0) == letter)
                    box(g, menu.slots.get(i), left, top, 0x9020FF20);
            }
        }
    }

    private static boolean matchesColour(String id, String want) {
        // light_blue/light_gray must not be caught by blue/gray, so match the exact token
        if (!id.contains(want)) return false;
        if ((want.equals("blue") || want.equals("gray")) && id.contains("light_" + want)) return false;
        return true;
    }

    private static String id(ItemStack s) { return s.getItem().getDescriptionId(); }

    private static void box(net.minecraft.client.gui.GuiGraphicsExtractor g, Slot slot, int left, int top, int argb) {
        int x = left + slot.x, y = top + slot.y;
        g.fill(x, y, x + 16, y + 16, argb);
    }

    private static void number(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font,
                               Slot slot, int left, int top, int n) {
        g.text(font, Integer.toString(n), left + slot.x + 1, top + slot.y + 1, 0xFFFFFFFF, true);
    }
}
