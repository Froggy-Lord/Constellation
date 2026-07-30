package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// ported from NoFrills (GPL-3.0-only): mixin/CameraMixin.java, features/tweaks/InstantSneak.java
@Mixin(Camera.class)
public abstract class CameraInstantSneakMixin {
    @Shadow private Entity entity;

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 1))
    private float constellation$instantEyeHeight(float delta, float start, float end) {
        var config = ConstellationClient.cfg();
        if (config != null && config.phoenix.enabled && config.phoenix.instantSneak && entity != null)
            return entity.getEyeHeight();
        return Mth.lerp(delta, start, end);
    }
}
