package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ProjectileWeaponItem;
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
        // 26.2 removed SwordItem — detect via bow superclass + description patterns
        var item = stack.getItem();
        if (item instanceof ProjectileWeaponItem) {
            cir.setReturnValue(InteractionResult.PASS);
        } else {
            String id = item.getDescriptionId();
            if (id.contains("sword") || id.contains("wand") || id.contains("staff")
                || id.contains("blade") || id.contains("scythe") || id.contains("dagger")
                || id.contains("axe"))
                cir.setReturnValue(InteractionResult.PASS);
        }
    }

    private static PhoenixConfig constellation$phoenix() {
        var cfg = ConstellationClient.cfg();
        return cfg == null ? null : cfg.phoenix;
    }
}
