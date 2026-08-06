package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.SlayerState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// death event ported from Athen (BSD-3-Clause): api/slayers/SlayerAPI.kt EntityEvent.Death
@Mixin(LivingEntity.class)
public abstract class SlayerDeathMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void constellation$slayerDeath(byte status, CallbackInfo ci) {
        if (status == 3) SlayerState.onDeathEvent((LivingEntity) (Object) this);
    }
}
