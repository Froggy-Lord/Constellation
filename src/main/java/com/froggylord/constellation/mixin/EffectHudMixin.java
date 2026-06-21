package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// hide potion effect icons in the top-right of the hud
@Mixin(Gui.class)
public class EffectHudMixin {
    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void constellation$hideEffects(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        PhoenixConfig cfg = ConstellationClient.cfg().phoenix;
        if (cfg != null && cfg.hideStatusEffects) ci.cancel();
    }
}
