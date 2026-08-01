package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ui.MenuTheme;
import com.froggylord.constellation.ui.SpaceBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MenuBackgroundMixin {
    // ported from CryptKit (GPL-3.0-only): mixin/ScreenBackgroundMixin.java
    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void constellation$titleBackdrop(GuiGraphicsExtractor graphics, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!MenuTheme.titleBackdrop(screen)) return;
        Minecraft minecraft = Minecraft.getInstance();
        SpaceBackground.renderMenu(graphics, minecraft.getWindow().getGuiScaledWidth(),
            minecraft.getWindow().getGuiScaledHeight(), delta, MenuTheme.config().reducedMotion);
        ci.cancel();
    }
}
