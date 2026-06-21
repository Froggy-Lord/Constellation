package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class HideStuckArrowsMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void constellation$removeStuckArrows(CallbackInfo ci) {
        PhoenixConfig p = phx();
        if (p == null || !p.enabled) return;
        Entity self = (Entity)(Object)this;
        for (Entity passenger : self.getPassengers()) {
            if (passenger instanceof AbstractArrow) passenger.discard();
        }
    }
    private static PhoenixConfig phx() {
        var c = ConstellationClient.cfg();
        return c == null ? null : c.phoenix;
    }
}
