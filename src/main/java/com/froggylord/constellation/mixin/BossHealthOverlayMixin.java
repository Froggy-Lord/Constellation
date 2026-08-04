package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.WatcherBossBar;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
    // ported from Devonian (GPL-3.0): features/bossbar/BossBar.kt
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<LerpingBossEvent> constellation$modifyWatcherBar(
            Map<UUID, LerpingBossEvent> events) {
        return WatcherBossBar.modify(events.values());
    }
}
