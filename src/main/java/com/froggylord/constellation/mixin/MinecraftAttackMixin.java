package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.AquilaTunnelMaps;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/TunnelsMaps.kt
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
    @Inject(method="startAttack",at=@At("HEAD"))
    private void constellation$tunnelMapPigeon(CallbackInfoReturnable<Boolean> cir){AquilaTunnelMaps.onAttack();}
}
