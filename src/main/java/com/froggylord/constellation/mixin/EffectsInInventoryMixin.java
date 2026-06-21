package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// hide potion effect icons in inventory screens
@Mixin(EffectsInInventory.class)
public class EffectsInInventoryMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void constellation$hide(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        PhoenixConfig cfg = ConstellationClient.cfg().phoenix;
        if (cfg != null && cfg.hideStatusEffects) ci.cancel();
    }
}
