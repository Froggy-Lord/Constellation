package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ui.ContainerTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class BasicContainerThemeMixin {
    // ported from CryptKit (GPL-3.0-only): mixin/ContainerThemeMixin.java
    @Inject(method = "extractContents", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
    private void constellation$containerTheme(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                               float delta, CallbackInfo ci) {
        ContainerTheme.drawBasicContainer(graphics, (AbstractContainerScreen<?>) (Object) this);
    }
}
