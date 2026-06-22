package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyraSlotText {

    private LyraSlotText() {}

    private static LyraConfig cfg;
    private static final Pattern PET_LVL = Pattern.compile("\\[Lvl (\\d+)]");
    private static final Pattern CAKE_YEAR = Pattern.compile("Year (\\d+)");

    public static void init(LyraConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                if (cfg == null || !cfg.slotText || !ConstellationClient.loc().onHypixel()) return;
                try { draw(cs, g); } catch (Exception ignored) {}
            });
        });
    }

    private static void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        var font = Minecraft.getInstance().font;
        for (Slot slot : cs.getMenu().slots) {
            ItemStack s = slot.getItem();
            if (s.isEmpty()) continue;
            String name = s.getHoverName().getString();

            if (cfg.slotTextPetLevel) {
                Matcher m = PET_LVL.matcher(name);
                if (m.find()) { corner(g, font, slot, left, top, m.group(1), 0xFF55FFFF); continue; }
            }
            if (cfg.slotTextCakeYear && name.contains("New Year Cake")) {
                Matcher m = CAKE_YEAR.matcher(name);
                if (m.find()) { corner(g, font, slot, left, top, m.group(1), 0xFFFFD0E0); continue; }
            }
            if (cfg.slotTextStars) {
                int stars = stars(s);
                if (stars > 0) corner(g, font, slot, left, top, Integer.toString(stars), 0xFFFFAA00);
            }
        }
    }

    private static int stars(ItemStack s) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        CompoundTag extra = cd.copyTag().getCompoundOrEmpty("ExtraAttributes");
        if (extra.isEmpty()) return 0;
        return extra.getIntOr("upgrade_level", extra.getIntOr("dungeon_item_level", 0));
    }

    
    private static void corner(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Slot slot,
                               int left, int top, String text, int colour) {
        g.text(font, text, left + slot.x, top + slot.y - 1, colour, true);
    }
}
