package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.ApolloCustomScoreboard;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ported from CryptKit (GPL-3.0-only): mixin/ScoreboardSidebarMixin.java
// cross-checked with SkyHanni (LGPL-3.0-or-later): mixins/transformers/gui/MixinGui.java
@Mixin(Hud.class)
public final class ScoreboardSidebarMixin {
    @Inject(method="extractScoreboardSidebar",at=@At("HEAD"),cancellable=true)
    private void constellation$hideVanillaScoreboard(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo ci){if(ApolloCustomScoreboard.hideVanilla())ci.cancel();}
}
