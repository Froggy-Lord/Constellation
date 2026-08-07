package com.froggylord.constellation.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.inventory.Slot;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int constellation$left();

    @Accessor("topPos")
    int constellation$top();

    @Accessor("imageWidth")
    int constellation$imageWidth();

    @Accessor("imageHeight")
    int constellation$imageHeight();

    @Accessor("hoveredSlot")
    Slot constellation$hoveredSlot();
}
