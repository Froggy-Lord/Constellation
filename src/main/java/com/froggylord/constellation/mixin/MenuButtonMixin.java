package com.froggylord.constellation.mixin;

import com.froggylord.constellation.render.ConstellationTheme;
import com.froggylord.constellation.ui.MenuTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class MenuButtonMixin {
    // ported from CryptKit (GPL-3.0-only): mixin/ButtonThemeMixin.java
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void constellation$titleButton(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!MenuTheme.buttons()) return;
        AbstractButton button = (AbstractButton) (Object) this;
        int alpha = Math.clamp((int) (button.getAlpha() * 255f), 0, 255);
        if (button.active) {
            int fill = button.isHoveredOrFocused() ? ConstellationTheme.SURFACE_HOVER : ConstellationTheme.CARD;
            int border = button.isHoveredOrFocused() ? ConstellationTheme.BORDER_SOFT : ConstellationTheme.BORDER;
            ConstellationTheme.surface(graphics, button.getX(), button.getY(), button.getWidth(),
                button.getHeight(), withAlpha(fill, alpha), withAlpha(border, alpha));
        } else {
            ConstellationTheme.surface(graphics, button.getX(), button.getY(), button.getWidth(),
                button.getHeight(), withAlpha(0xCC101020, alpha), withAlpha(ConstellationTheme.BORDER, alpha));
        }
        ci.cancel();
    }

    private static int withAlpha(int color, int alpha) {
        int source = color >>> 24;
        return (source * alpha / 255 << 24) | color & 0x00FFFFFF;
    }
}
