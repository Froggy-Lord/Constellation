package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.LyraMarketSearch;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ported from Skyblocker (LGPL-3.0-or-later): mixins/LocalPlayerMixin.java openTextEdit
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerTextEditMixin {
    @Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
    private void constellation$marketSearch(SignBlockEntity sign, boolean front, CallbackInfo ci) {
        if (LyraMarketSearch.intercept(sign, front)) ci.cancel();
    }
}
