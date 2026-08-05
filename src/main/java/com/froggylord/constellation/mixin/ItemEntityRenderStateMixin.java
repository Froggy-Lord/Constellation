package com.froggylord.constellation.mixin;

import com.froggylord.constellation.render.BigSlayerDropRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// render-state carrier adapted from Athen (BSD-3-Clause): mixin/mixins/EntityRenderStateMixin.java
@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements BigSlayerDropRenderState {
    @Unique private float constellation$slayerDropScale = 1.0f;
    @Override public float constellation$slayerDropScale() { return constellation$slayerDropScale; }
    @Override public void constellation$slayerDropScale(float scale) { constellation$slayerDropScale = scale; }
}
