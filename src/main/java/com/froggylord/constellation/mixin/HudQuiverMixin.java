package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.LyraQuiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ported from Skyblocker (LGPL-3.0-or-later): mixins/HudMixin.java
@Mixin(Hud.class)
public class HudQuiverMixin {
    @Unique private ItemStack constellation$quiverStack = ItemStack.EMPTY;

    @Inject(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V", ordinal = 0))
    private void constellation$markQuiverSlot(CallbackInfo ci, @Local(name = "i") int index, @Local(name = "player") Player player) {
        constellation$quiverStack = index == 8 ? player.getInventory().getNonEquipmentItems().get(index) : ItemStack.EMPTY;
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
    private void constellation$trueQuiverCount(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, Operation<Void> original) {
        String count = stack == constellation$quiverStack ? LyraQuiver.hotbarCount(stack) : null;
        constellation$quiverStack = ItemStack.EMPTY;
        if (count == null) original.call(graphics, font, stack, x, y);
        else graphics.itemDecorations(font, stack, x, y, count);
    }
}
