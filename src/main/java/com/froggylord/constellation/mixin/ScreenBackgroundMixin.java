package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.ui.SpaceBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * intercepts every Screen.render to add the space background + theme.
 * skips screens that already have their own themed rendering
 * (our HubScreen, ConfigScreen, HudEditScreen, and any other mod screens).
 */
@Mixin(Screen.class)
public class ScreenBackgroundMixin {

    @Unique private long constellation$openTime = 0;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void constellation$beforeRender(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        String className = self.getClass().getName();

        // skip our own themed screens
        if (className.startsWith("com.froggylord.constellation.ui.")) return;
        if (className.startsWith("com.froggylord.constellation.hud.")) return;
        if (className.contains("Realms")) return;
        if (className.contains("SocialInteractions")) return;

        // only full-screen menus get the space background — overlays (inventory, chat, etc.)
        // show the game world behind them, just styled panels
        boolean fullScreen = className.contains("TitleScreen")
            || className.contains("SelectWorld")
            || className.contains("Multiplayer")
            || className.contains("Options")
            || className.contains("Language")
            || className.contains("Controls")
            || className.contains("Video")
            || className.contains("Sound")
            || className.contains("Chat")
            || className.contains("CreateWorld")
            || className.contains("EditWorld")
            || className.contains("LevelLoading")
            || className.contains("Progress")
            || className.contains("GenericMessage")
            || className.contains("Disconnected")
            || className.contains("Death");

        if (!fullScreen) return; // let game world show behind inventory/overlay screens

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // dim + nasa background
        g.fill(0, 0, w, h, 0x99080814);
        SpaceBackground.render(g, w, h, delta);

        if (constellation$openTime == 0) constellation$openTime = System.currentTimeMillis();
        SpaceBackground.fadeIn(g, w, h, constellation$openTime);
    }
}
