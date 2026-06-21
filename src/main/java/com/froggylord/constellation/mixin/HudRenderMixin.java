package com.froggylord.constellation.mixin;

import com.froggylord.constellation.render.HudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudRenderMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void constellation$renderHud(GuiGraphicsExtractor g, DeltaTracker tracker, CallbackInfo ci) {
        HudRenderer.onRenderOverlay(g, tracker.getGameTimeDeltaTicks());
    }
}
