package com.froggylord.constellation.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Comparator;

// the tab list is sorted by a private static comparator (spectators last, then team, then
// name). Hypixel packs dungeon stats into fake tab entries ordered by hidden team names, so
// we have to read them in this exact vanilla order to land on the right lines.
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("PLAYER_COMPARATOR")
    static Comparator<PlayerInfo> constellation$ordering() {
        throw new UnsupportedOperationException();
    }
}
