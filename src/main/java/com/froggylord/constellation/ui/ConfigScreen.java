package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.NebulaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {

    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Constellation Settings"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var font = Minecraft.getInstance().font;

        g.fill(0, 0, width, height, NebulaTheme.BG_DEEP);
        g.fill(15, 10, width - 15, height - 10, NebulaTheme.BG_PANEL);

        String title = "Constellation Settings";
        g.text(font, title, width / 2 - font.width(title) / 2, 14, NebulaTheme.ACCENT_GOLD, false);

        int y = 30;
        for (Object o : ConstellationClient.featureManager().getLoadedIds()) {
            String id = (String) o;
            var c = ConstellationClient.featureManager().get(id);
            if (c.isEmpty()) continue;

            String name = c.get().displayName();
            boolean enabled = c.get().isEnabled();
            String entry = (enabled ? "✦ " : "  ") + name;

            boolean hover = mx >= 20 && mx <= 220 && my >= y && my < y + 20;
            g.fill(20, y, 220, y + 20, hover ? 0xFF2A2A3A : 0xFF1A1A28);
            g.text(font, entry, 26, y + 5, enabled ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_MUTED, false);
            y += 24;
        }

        int btnW = 80, btnH = 20, btnX = width / 2 - btnW / 2, btnY = height - 28;
        boolean hover = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, hover ? 0xFF3A3050 : 0xFF1A1428);
        g.fill(btnX, btnY, btnX + btnW, btnY + 2, NebulaTheme.ACCENT_GOLD);

        String done = "Done";
        g.text(font, done, width / 2 - font.width(done) / 2, btnY + 5,
            hover ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_WHITE, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();

        int btnW = 80, btnH = 20, btnX = width / 2 - btnW / 2, btnY = height - 28;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            onClose();
            return true;
        }

        int y = 30;
        for (Object o : ConstellationClient.featureManager().getLoadedIds()) {
            String id = (String) o;
            if (mx >= 20 && mx <= 220 && my >= y && my < y + 20) {
                ConstellationClient.featureManager().toggle(id);
                ConstellationClient.saveConfig();
                Minecraft.getInstance().setScreenAndShow(new ConfigScreen(parent));
                return true;
            }
            y += 24;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
