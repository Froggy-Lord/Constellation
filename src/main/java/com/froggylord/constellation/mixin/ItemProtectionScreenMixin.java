package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.ItemProtection;
import com.froggylord.constellation.constellation.DungeonLootHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// ported from Skyblocker (LGPL-3.0): mixins/AbstractContainerScreenMixin.java
@Mixin(AbstractContainerScreen.class)
public abstract class ItemProtectionScreenMixin {
    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"),
        cancellable = true)
    private void constellation$protectItemClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (com.froggylord.constellation.constellation.HerculesVisitorHelper.shouldBlockClick(screen, slot, slotId, input)) { ci.cancel(); return; }
        if (com.froggylord.constellation.constellation.LyraAuctionHelper.shouldBlockClick(screen, slot, slotId)) { ci.cancel(); return; }
        com.froggylord.constellation.constellation.LyraBazaarHelper.onSlotClick(screen, slot, slotId);
        if (!com.froggylord.constellation.constellation.LyraAuctionHelper.consumeProtectionBypass()
            && ItemProtection.shouldBlockClick(screen, slot, slotId, input)) ci.cancel();
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void constellation$markProtected(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        DungeonLootHelper.drawSlot(graphics, screen, slot);
        com.froggylord.constellation.constellation.LyraInventorySearch.drawSlot(graphics, screen, slot);
        com.froggylord.constellation.constellation.LyraSlotText.drawSlot(graphics, slot);
        com.froggylord.constellation.constellation.HydraThunderBottles.drawSlot(graphics, slot);
        com.froggylord.constellation.constellation.SpiritMaskState.drawSlot(graphics, slot);
        com.froggylord.constellation.constellation.LyraBazaarHelper.drawSlot(graphics, screen, slot);
        com.froggylord.constellation.constellation.LyraAuctionHelper.drawSlot(graphics, screen, slot);
        com.froggylord.constellation.constellation.HerculesVisitorHelper.drawSlot(graphics, screen, slot);
        com.froggylord.constellation.constellation.HerculesStereoHarmony.drawSlot(graphics, screen, slot);
        com.froggylord.constellation.constellation.HerculesGreenhouse.drawSlot(graphics, screen, slot);
        if (slot != null && ItemProtection.showMarker(slot.getItem()))
            graphics.text(net.minecraft.client.Minecraft.getInstance().font, "P", slot.x + 1, slot.y + 1, 0xFF55FF55, true);
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void constellation$protectedTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        cir.setReturnValue(DungeonLootHelper.appendTooltip(screen, stack,
            com.froggylord.constellation.constellation.HerculesVisitorHelper.appendTooltip(screen, stack,
                com.froggylord.constellation.constellation.LyraAuctionHelper.appendTooltip(screen, stack,
                    com.froggylord.constellation.constellation.LyraBazaarHelper.appendTooltip(screen, stack,
                        ItemProtection.appendTooltip(stack, cir.getReturnValue()))))));
    }
}
