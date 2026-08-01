package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ui.ContainerTheme;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemCombinerScreen.class)
public abstract class ItemCombinerScreenThemeMixin {
    // ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    @Redirect(method = "extractBackground", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"),
        require = 1)
    private void constellation$anvilTheme(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                                           Identifier texture, int x, int y, float u, float v,
                                           int width, int height, int textureWidth, int textureHeight) {
        ItemCombinerScreen<?> screen = (ItemCombinerScreen<?>) (Object) this;
        if (screen instanceof AnvilScreen anvil && ContainerTheme.anvil(anvil)) {
            ContainerTheme.drawAnvil(graphics, anvil, pipeline, texture, x, y, width, height,
                textureWidth, textureHeight);
            return;
        }
        if (screen instanceof SmithingScreen smithing && ContainerTheme.smithingTable(smithing)) {
            ContainerTheme.drawSmithingTable(graphics, smithing, pipeline, texture, x, y, width, height,
                textureWidth, textureHeight);
            return;
        }
        graphics.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }
}
