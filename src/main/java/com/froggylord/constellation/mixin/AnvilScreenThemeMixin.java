package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.VisualConfig;
import com.froggylord.constellation.ui.ContainerTheme;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenThemeMixin {
    // ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    @Redirect(method = "extractBackground", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
        require = 1)
    private void constellation$anvilNameField(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                                               Identifier sprite, int x, int y, int width, int height) {
        AnvilScreen screen = (AnvilScreen) (Object) this;
        VisualConfig config;
        try { config = ConstellationClient.cfg().visual; }
        catch (RuntimeException ignored) { config = null; }
        if (!ContainerTheme.anvil(screen) || config == null || !config.anvilNameField) {
            graphics.blitSprite(pipeline, sprite, x, y, width, height);
            return;
        }
        ContainerTheme.drawAnvilNameField(graphics, screen, x, y, width, height);
    }
}
