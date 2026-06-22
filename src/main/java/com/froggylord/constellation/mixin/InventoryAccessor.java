package com.froggylord.constellation.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Inventory.class)
public interface InventoryAccessor {
    @Accessor("selected")
    int constellation$selected();

    @Accessor("selected")
    void constellation$setSelected(int slot);
}
