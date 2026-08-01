package com.froggylord.constellation.mixin;

import com.froggylord.constellation.render.ConstellationTheme;
import com.froggylord.constellation.ui.MenuTheme;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractSliderButton.class)
public abstract class MenuSliderMixin {
    // ported from CryptKit (GPL-3.0-only): mixin/SliderThemeMixin.java
    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
        ordinal = 0), require = 1)
    private void constellation$sliderTrack(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                                            Identifier sprite, int x, int y, int width, int height, int color) {
        if (!MenuTheme.menuSliders()) {
            graphics.blitSprite(pipeline, sprite, x, y, width, height, color);
            return;
        }
        AbstractSliderButton slider = (AbstractSliderButton) (Object) this;
        int alpha = color >>> 24;
        int fill = withAlpha(slider.active ? 0xE614142E : 0xCC101020, alpha);
        int border = slider.isHoveredOrFocused() && slider.active
            ? ConstellationTheme.ACCENT : ConstellationTheme.BORDER;
        ConstellationTheme.surface(graphics, x, y, width, height, fill, withAlpha(border, alpha));
    }

    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
        ordinal = 1), require = 1)
    private void constellation$sliderHandle(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                                             Identifier sprite, int x, int y, int width, int height, int color) {
        if (!MenuTheme.menuSliders()) {
            graphics.blitSprite(pipeline, sprite, x, y, width, height, color);
            return;
        }
        AbstractSliderButton slider = (AbstractSliderButton) (Object) this;
        int handle = slider.active && slider.isHoveredOrFocused()
            ? ConstellationTheme.ACCENT_BRIGHT : slider.active ? ConstellationTheme.ACCENT : ConstellationTheme.TEXT_MUTED;
        int alpha = color >>> 24;
        graphics.fill(x, y, x + width, y + height, withAlpha(handle, alpha));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, withAlpha(0x99FFFFFF, alpha));
    }

    private static int withAlpha(int color, int alpha) {
        int source = color >>> 24;
        return (source * alpha / 255 << 24) | color & 0x00FFFFFF;
    }
}
