package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.LyraBazaarHelper;
import com.froggylord.constellation.constellation.PhoenixSignCalculator;
import com.froggylord.constellation.constellation.PhoenixSpeedPresets;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ported from Skyblocker (LGPL-3.0-or-later): mixins/AbstractSignEditScreenMixin.java
@Mixin(AbstractSignEditScreen.class)
public abstract class SignEditScreenMixin extends Screen {
    @Shadow @Final private String[] messages;

    protected SignEditScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void constellation$bazaarQuantityButtons(CallbackInfo ci) {
        for (var button : LyraBazaarHelper.quantityButtons((AbstractSignEditScreen) (Object) this, messages)) addRenderableWidget(button);
    }

    // ported from Skyblocker (LGPL-3.0-or-later): mixins/AbstractSignEditScreenMixin.java
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void constellation$speedPresetPreview(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta,CallbackInfo ci) {
        if(isSpeedInput()&&PhoenixSpeedPresets.signAlias(messages[0]))graphics.centeredText(font,PhoenixSpeedPresets.signPreview(messages[0]),graphics.guiWidth()/2,55,0xFF55FF55);
        else if(PhoenixSignCalculator.active()&&PhoenixSignCalculator.isInput(messages)
            &&com.froggylord.constellation.ConstellationClient.cfg().phoenix.signCalculatorPreview)
            graphics.centeredText(font,PhoenixSignCalculator.preview(messages[0]),graphics.guiWidth()/2,55,0xFFFFFFFF);
    }

    // ported from Skyblocker (LGPL-3.0-or-later): mixins/AbstractSignEditScreenMixin.java
    @Inject(method = "onDone", at = @At("HEAD"))
    private void constellation$resolveSpeedPreset(CallbackInfo ci) {
        if(isSpeedInput()&&PhoenixSpeedPresets.signAlias(messages[0]))messages[0]=PhoenixSpeedPresets.signValue(messages[0]);
        else if(PhoenixSignCalculator.active()&&PhoenixSignCalculator.isInput(messages))
            messages[0]=PhoenixSignCalculator.resolve(messages[0],messages[2].contains("price"));
    }

    // ported from Devonian (GPL-3.0-only): features/misc/Misc.kt, mixin/AbstractSignEditScreenMixin.java
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void constellation$finishWithEnter(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        var cfg=com.froggylord.constellation.ConstellationClient.cfg().phoenix;
        if(!cfg.enabled||!cfg.signEnterToDone||!event.isConfirmation()||event.hasShiftDown())return;
        onClose();
        cir.setReturnValue(true);
    }

    private boolean isSpeedInput(){return messages.length>3&&messages[3].equals("speed cap!");}
}
