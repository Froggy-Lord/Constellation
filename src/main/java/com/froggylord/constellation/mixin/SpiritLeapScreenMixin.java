package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.OrionSpiritLeap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// render cancellation ported from Devonian (GPL-3.0): features/dungeons/CustomLeapGui.kt
@Mixin(AbstractContainerScreen.class)
public abstract class SpiritLeapScreenMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void constellation$replaceLeapScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                  float delta, CallbackInfo callback) {
        if (OrionSpiritLeap.shouldReplace((AbstractContainerScreen<?>) (Object) this)) callback.cancel();
    }
}
