package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.AquilaMiningTools;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ported from Skyblocker (LGPL-3.0-or-later): mixins/ItemStackMixin.java
@Mixin(ItemStack.class)
public abstract class MiningToolDurabilityMixin {
    @Inject(method = "isBarVisible", at = @At("RETURN"), cancellable = true)
    private void constellation$miningBarVisible(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && AquilaMiningTools.customBarEnabled()
            && AquilaMiningTools.barWidth((ItemStack) (Object) this) >= 0) cir.setReturnValue(true);
    }

    @Inject(method = "getBarWidth", at = @At("RETURN"), cancellable = true)
    private void constellation$miningBarWidth(CallbackInfoReturnable<Integer> cir) {
        if (!AquilaMiningTools.customBarEnabled()) return;
        int width = AquilaMiningTools.barWidth((ItemStack) (Object) this);
        if (width >= 0) cir.setReturnValue(width);
    }

    @Inject(method = "getBarColor", at = @At("RETURN"), cancellable = true)
    private void constellation$miningBarColor(CallbackInfoReturnable<Integer> cir) {
        if (AquilaMiningTools.customBarEnabled()
            && AquilaMiningTools.barWidth((ItemStack) (Object) this) >= 0)
            cir.setReturnValue(AquilaMiningTools.barColor((ItemStack) (Object) this));
    }
}
