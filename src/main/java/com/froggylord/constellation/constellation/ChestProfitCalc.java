package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.BazaarApi;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;

/**
 * When a dungeon reward chest opens at the end of a run, this reads every item's hidden SB id,
 * looks up its bazaar buy price, and draws a live total on the screen so you can decide
 * whether the chest is worth unlocking. (cmp. Skyblocker ChestValue)
 */
public final class ChestProfitCalc {

    private ChestProfitCalc() {}

    private static OrionConfig cfg;

    public static void init(OrionConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            String title = cs.getTitle().getString();
            // dungeon reward chests have titles like "Wood Chest", "Gold Chest", etc.
            // or "Dungeon Reward" — match any chest screen that appears after a dungeon
            if (!title.contains("Chest") && !title.contains("Reward") && !title.contains("Dungeon")) return;
            if (!ConstellationClient.loc().inDungeons()) return;
            ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                if (cfg == null || !cfg.chestProfitCalc) return;
                try { calculate(cs, g); } catch (Exception ignored) {}
            });
        });
    }

    private static void calculate(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        BazaarApi.ensureFresh();
        double total = 0;
        int count = 0;
        int chest = cs.getMenu().slots.size() - 36;
        for (int i = 0; i < chest; i++) {
            var stack = cs.getMenu().slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd == null) continue;
            CompoundTag extra = cd.copyTag().getCompoundOrEmpty("ExtraAttributes");
            if (extra.isEmpty()) continue;
            String id = extra.getStringOr("id", "");
            if (id.isEmpty()) continue;
            double[] bz = BazaarApi.get(id);
            if (bz == null || bz[1] <= 0) continue; // bz[1] = sell price
            total += bz[1] * stack.getCount();
            count++;
        }
        if (count == 0) return;
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        var font = Minecraft.getInstance().font;
        String text = "§6💰 " + count + " items: §f" + money(total);
        g.text(font, text, left + 8, top - 12, 0xFFFFAA00, true);
    }

    private static String money(double n) {
        if (n < 1000) return String.format("%.1f", n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        if (n < 1_000_000_000) return String.format("%.2fM", n / 1_000_000.0);
        return String.format("%.2fB", n / 1_000_000_000.0);
    }
}
