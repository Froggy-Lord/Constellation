package com.froggylord.constellation.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// the chest's top-left pixel is protected on the screen. terminal solvers need it to line a
// highlight up over the right slot, so expose leftPos/topPos.
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int constellation$left();

    @Accessor("topPos")
    int constellation$top();
}
