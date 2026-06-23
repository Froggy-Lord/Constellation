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

        // skip our own themed screens — they render SpaceBackground themselves
        if (className.startsWith("com.froggylord.constellation.ui.")) return;
        if (className.startsWith("com.froggylord.constellation.hud.")) return;

        // skip non-game screens (realms, social interactions, etc. — their own styling)
        if (className.contains("Realms")) return;
        if (className.contains("SocialInteractions")) return;

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // dim the game world so the space bg + ui is readable
        g.fill(0, 0, w, h, 0x99080814);

        // render space background behind the vanilla content
        SpaceBackground.render(g, w, h, delta);

        // fade-in on first open
        if (constellation$openTime == 0) constellation$openTime = System.currentTimeMillis();
        SpaceBackground.fadeIn(g, w, h, constellation$openTime);
    }
}
