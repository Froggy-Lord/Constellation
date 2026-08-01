package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ui.MenuTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleThemeStateMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void constellation$startTitleTheme(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                float delta, CallbackInfo ci) {
        MenuTheme.setRenderingTitle(true);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void constellation$endTitleTheme(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                              float delta, CallbackInfo ci) {
        MenuTheme.setRenderingTitle(false);
    }
}
