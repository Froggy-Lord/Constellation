package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// cancels block-placement when holding a weapon item, so right-click fires the
// weapon ability instead of placing whatever block is in your offhand.
@Mixin(MultiPlayerGameMode.class)
public class PreventWeaponPlaceMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void constellation$preventWeaponPlace(LocalPlayer player, InteractionHand hand,
                                                   BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        PhoenixConfig p = constellation$phoenix();
        if (p == null || !p.enabled || !p.preventPlacingWeapons) return;

        var stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return;
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof BowItem) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    private static PhoenixConfig constellation$phoenix() {
        var cfg = ConstellationClient.cfg();
        return cfg == null ? null : cfg.phoenix;
    }
}
