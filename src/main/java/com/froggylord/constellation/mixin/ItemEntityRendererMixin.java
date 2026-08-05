package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.BigSlayerDrops;
import com.froggylord.constellation.render.BigSlayerDropRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ported from Athen (BSD-3-Clause): mixin/mixins/ItemEntityRendererMixin.java
@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void constellation$extract(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        ((BigSlayerDropRenderState) state).constellation$slayerDropScale(BigSlayerDrops.scale(entity));
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
    private void constellation$scale(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                     CameraRenderState camera, CallbackInfo ci) {
        float scale = ((BigSlayerDropRenderState) state).constellation$slayerDropScale();
        if (scale > 1.0f) poseStack.scale(scale, scale, scale);
    }
}
