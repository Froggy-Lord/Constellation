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
        if (!MenuTheme.titleButtons()) return;
        AbstractButton button = (AbstractButton) (Object) this;
        if (button.active) {
            ConstellationTheme.button(graphics, button.getX(), button.getY(), button.getWidth(),
                button.getHeight(), button.isHoveredOrFocused(), false);
        } else {
            ConstellationTheme.surface(graphics, button.getX(), button.getY(), button.getWidth(),
                button.getHeight(), 0xCC101020, ConstellationTheme.BORDER);
        }
        ci.cancel();
    }
}
