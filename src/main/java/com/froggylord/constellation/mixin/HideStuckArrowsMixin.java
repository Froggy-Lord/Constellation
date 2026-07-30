package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ported from Devonian (GPL-3.0-only): mixin/PlayerRendererMixin.java
@Mixin(AvatarRenderer.class)
public class HideStuckArrowsMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL"))
    private void constellation$hideAttachedArrows(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        PhoenixConfig p = phx();
        if (p != null && p.enabled && p.hideAttachedArrows) state.arrowCount = 0;
    }
    private static PhoenixConfig phx() {
        var c = ConstellationClient.cfg();
        return c == null ? null : c.phoenix;
    }
}
