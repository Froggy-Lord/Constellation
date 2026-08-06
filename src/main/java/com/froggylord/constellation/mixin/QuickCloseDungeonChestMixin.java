package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.QuickCloseDungeonChest;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public final class QuickCloseDungeonChestMixin {

    // ported from Devonian (GPL-3.0): features/dungeons/clear/CloseChestOnKey.kt
    // standard onClose path cross-checked with NoFrills (GPL-3.0): features/dungeons/QuickClose.java
    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void constellation$quickCloseDungeonChest(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (!QuickCloseDungeonChest.shouldClose(screen, event)) return;
        screen.onClose();
        cir.setReturnValue(true);
    }
}
