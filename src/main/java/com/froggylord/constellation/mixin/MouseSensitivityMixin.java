package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.HerculesMouseSensitivity;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// ported from SkyHanni (LGPL-3.0-or-later): mixins/transformers/MixinMouse.java
@Mixin(MouseHandler.class)
public abstract class MouseSensitivityMixin {
    @Redirect(
        method = "turnPlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void constellation$remapSensitivity(LocalPlayer player, double x, double y) {
        player.turn(HerculesMouseSensitivity.remap(x), HerculesMouseSensitivity.remap(y));
    }
}
