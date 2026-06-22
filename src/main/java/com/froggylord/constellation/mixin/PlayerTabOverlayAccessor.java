package com.froggylord.constellation.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Comparator;

// we have to read them in this e...
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("PLAYER_COMPARATOR")
    static Comparator<PlayerInfo> constellation$ordering() {
        throw new UnsupportedOperationException();
    }
}
