package com.froggylord.constellation.constellation;

import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Spirit Leap helper — when the leap menu is open, tag each teammate's head with their class so
 * you can pick the right jump at a glance instead of reading every tooltip.
 */
public final class OrionSpiritLeap {

    private OrionSpiritLeap() {}

    private static OrionConfig cfg;

    public static void init(OrionConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            String title = cs.getTitle().getString();
            if (!title.contains("Spirit Leap") && !title.contains("Teleport")) return;
            ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                if (cfg == null || !cfg.spiritLeapHelper) return;
                try { draw(cs, g); } catch (Exception ignored) {}
            });
        });
    }

    private static void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        var font = Minecraft.getInstance().font;
        int chest = cs.getMenu().slots.size() - 36;
        for (int i = 0; i < chest; i++) {
            Slot slot = cs.getMenu().slots.get(i);
            ItemStack s = slot.getItem();
            if (s.isEmpty() || !s.getItem().getDescriptionId().contains("player_head")) continue;
            String text = s.getHoverName().getString();
            ItemLore lore = s.get(DataComponents.LORE);
            if (lore != null) for (var line : lore.lines()) text += " " + line.getString();

            String tag = classTag(text);
            if (tag != null) g.text(font, tag, left + slot.x, top + slot.y - 1, 0xFFFFFFFF, true);
        }
    }

    private static String classTag(String t) {
        if (t.contains("Healer")) return "§dH";
        if (t.contains("Mage")) return "§bM";
        if (t.contains("Berserk") || t.contains("Berserker")) return "§cB";
        if (t.contains("Archer")) return "§6A";
        if (t.contains("Tank")) return "§aT";
        if (t.contains("DEAD") || t.contains("(Dead)")) return "§7X";
        return null;
    }
}
