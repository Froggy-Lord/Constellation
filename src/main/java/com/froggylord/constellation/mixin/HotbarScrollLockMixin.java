package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// ported from NoFrills (GPL-3.0-only): mixin/MouseHandlerMixin.java, features/misc/HotbarScrollLock.java
@Mixin(MouseHandler.class)
public abstract class HotbarScrollLockMixin {
    @Redirect(method = "onScroll", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void constellation$preventHotbarWrap(Inventory inventory, int requestedSlot) {
        var config = ConstellationClient.cfg();
        boolean enabled = config != null && config.phoenix.enabled && config.phoenix.hotbarScrollLock;
        int selected = inventory.getSelectedSlot();
        if (enabled && (selected == 0 && requestedSlot == 8 || selected == 8 && requestedSlot == 0)) return;
        inventory.setSelectedSlot(requestedSlot);
    }
}
