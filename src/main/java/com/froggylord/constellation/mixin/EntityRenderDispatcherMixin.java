package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.DungeonEncounterVisibility;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public final class EntityRenderDispatcherMixin {

    // ported from Skyblocker (LGPL-3.0):
    // src/main/java/de/hysky/skyblocker/mixins/EntityRenderDispatcherMixin.java
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void constellation$hideDungeonSkulls(E entity, Frustum frustum,
                                                                     double x, double y, double z,
                                                                     CallbackInfoReturnable<Boolean> cir) {
        // Slayer nametag suppression ported from Athen (BSD-3-Clause): modules/impl/slayer/SlayerInfo.kt
        if (cir.getReturnValue() && (DungeonEncounterVisibility.shouldHide(entity)
            || com.froggylord.constellation.constellation.PerseusSlayers.shouldHideSlayerStand(entity)
            // ported from SkyHanni (LGPL-3.0-or-later): features/fishing/FishingHookDisplay.kt
            || com.froggylord.constellation.constellation.HydraFishingState.shouldHideHookLabel(entity))) {
            cir.setReturnValue(false);
        }
    }
}
