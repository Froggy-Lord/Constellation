package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.OrionTerminals;
import com.froggylord.constellation.constellation.LyraStorageValue;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels clicks on non-solution slots of an F7/M7 terminal so you cannot misclick.
 * Advisory only — it never sends a click, it only swallows a wrong one, and it
 * fails open (see {@link OrionTerminals#shouldBlockClick}). Injects before the
 * container-input packet is sent, mirroring Skyblocker's AbstractContainerScreenMixin.
 */
@Mixin(AbstractContainerScreen.class)
public class TerminalClickBlockMixin {

    @Inject(
        method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"),
        cancellable = true)
    private void constellation$blockWrongTerminalClick(Slot slot, int slotId, int button,
                                                       ContainerInput containerInput, CallbackInfo ci) {
        if (slot == null) return;
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) (Object) this;
        if (OrionTerminals.shouldBlockClick(cs, slotId, button)) {
            ci.cancel();
            return;
        }
        OrionTerminals.playClickSound(cs, slotId, button);
    }

    @ModifyArgs(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"))
    private void constellation$middleClickTerminal(Args args, Slot slot, int slotId, int button,
                                                    ContainerInput containerInput) {
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) (Object) this;
        if (!OrionTerminals.shouldMiddleClick(cs, slotId, button, containerInput)) return;
        args.set(2, 2);
        args.set(3, ContainerInput.CLONE);
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void constellation$renderTerminalSlot(GuiGraphicsExtractor graphics, Slot slot,
                                                   int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) (Object) this;
        if (OrionTerminals.beforeRenderSlot(cs, graphics, slot)) ci.cancel();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void constellation$hideTerminalTooltip(GuiGraphicsExtractor graphics, int mouseX,
                                                    int mouseY, CallbackInfo ci) {
        if (LyraStorageValue.renderPreviewTooltip((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY)) {
            ci.cancel();
            return;
        }
        if (OrionTerminals.shouldHideTooltip((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void constellation$hideTerminalLabels(GuiGraphicsExtractor graphics, int mouseX,
                                                   int mouseY, CallbackInfo ci) {
        if (OrionTerminals.shouldHideLabels((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }
}
