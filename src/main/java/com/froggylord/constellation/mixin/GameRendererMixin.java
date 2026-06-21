package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Phoenix camera QoL — skip the view-bob walk sway and the hurt-camera tilt when toggled.
// both are self-contained private steps in the camera setup, so a HEAD cancel is clean.
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    private static PhoenixConfig constellation$phoenix() {
        var cfg = ConstellationClient.cfg();
        return cfg == null ? null : cfg.phoenix;
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void constellation$noViewBob(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        PhoenixConfig p = constellation$phoenix();
        if (p != null && p.enabled && p.noViewBob) ci.cancel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void constellation$noHurtCam(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        PhoenixConfig p = constellation$phoenix();
        if (p != null && p.enabled && p.noHurtCam) ci.cancel();
    }
}
