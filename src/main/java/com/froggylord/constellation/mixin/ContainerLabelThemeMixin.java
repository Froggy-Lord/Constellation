package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ui.ContainerTheme;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerLabelThemeMixin {
    @ModifyConstant(method = "extractLabels", constant = @Constant(intValue = -12566464), require = 2)
    private int constellation$readableContainerLabels(int original) {
        return ContainerTheme.labelColor((AbstractContainerScreen<?>) (Object) this, original);
    }
}
