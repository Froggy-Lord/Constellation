package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ui.ContainerTheme;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StonecutterScreen.class)
public abstract class StonecutterScreenThemeMixin {
    // ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    @Redirect(method = "extractBackground", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"),
        require = 1)
    private void constellation$stonecutterTheme(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                                                 Identifier texture, int x, int y, float u, float v,
                                                 int width, int height, int textureWidth, int textureHeight) {
        StonecutterScreen screen = (StonecutterScreen) (Object) this;
        if (!ContainerTheme.stonecutter(screen)) {
            graphics.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
            return;
        }
        ContainerTheme.drawStonecutter(graphics, screen, x, y, width, height);
    }
}
