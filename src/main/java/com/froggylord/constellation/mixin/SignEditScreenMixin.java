package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.LyraBazaarHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ported from Skyblocker (LGPL-3.0-or-later): mixins/AbstractSignEditScreenMixin.java
@Mixin(AbstractSignEditScreen.class)
public abstract class SignEditScreenMixin extends Screen {
    @Shadow @Final private String[] messages;

    protected SignEditScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void constellation$bazaarQuantityButtons(CallbackInfo ci) {
        for (var button : LyraBazaarHelper.quantityButtons((AbstractSignEditScreen) (Object) this, messages)) addRenderableWidget(button);
    }
}
