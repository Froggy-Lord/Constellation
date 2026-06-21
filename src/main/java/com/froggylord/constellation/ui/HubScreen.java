package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.NebulaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HubScreen extends Screen {

    private final Screen parent;

    public HubScreen(Screen parent) {
        super(Component.literal("Constellation"));
        this.parent = parent;
    }

    public static void setup() {}

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int w = width, h = height;
        var font = Minecraft.getInstance().font;

        g.fill(0, 0, w, h, NebulaTheme.BG_DEEP);

        int panelW = (int) (w * 0.72);
        g.fill(0, 0, panelW, h, NebulaTheme.BG_PANEL);
        g.fill(panelW, 0, panelW + 1, h, NebulaTheme.ACCENT_DIM);

        String title = "✧ Constellation ✧";
        g.text(font, title, w / 2 - font.width(title) / 2, 6, NebulaTheme.ACCENT_GOLD, false);

        int cardY = 20;
        var loadedIds = ConstellationClient.featureManager().getLoadedIds();
        for (Object o : loadedIds) {
            String id = (String) o;
            var c = ConstellationClient.featureManager().get(id);
            if (c.isEmpty()) continue;

            String name = "✦ " + c.get().displayName();
            int cardH = 22;
            boolean hover = mx >= 10 && mx <= 170 && my >= cardY && my < cardY + cardH;

            g.fill(10, cardY, 170, cardY + cardH, hover ? 0xFF2A2A3A : 0xFF1A1A28);
            if (hover) g.fill(10, cardY, 13, cardY + cardH, NebulaTheme.ACCENT_GOLD);
            g.text(font, name, 18, cardY + 6, hover ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_WHITE, false);
            cardY += 26;
        }

        int btnW = 120, btnH = 22, btnX = w / 2 - btnW / 2, btnY = h - 32;
        boolean hoverSettings = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, hoverSettings ? 0xFF3A3050 : 0xFF1A1428);
        g.fill(btnX, btnY, btnX + btnW, btnY + 2, NebulaTheme.ACCENT_GOLD);

        String settings = "Settings";
        g.text(font, settings, w / 2 - font.width(settings) / 2, btnY + 6,
            hoverSettings ? NebulaTheme.ACCENT_BRIGHT : NebulaTheme.STAR_WHITE, false);

        String esc = "Right Shift or ESC to close";
        g.text(font, esc, w / 2 - font.width(esc) / 2, h - 10, NebulaTheme.STAR_MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        int btnW = 120, btnH = 22, btnX = width / 2 - btnW / 2, btnY = height - 32;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            Minecraft.getInstance().setScreenAndShow(new ConfigScreen(this));
            return true;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT || event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
