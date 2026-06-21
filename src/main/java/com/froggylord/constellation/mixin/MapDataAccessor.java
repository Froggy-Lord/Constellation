package com.froggylord.constellation.mixin;

import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// the player markers on the dungeon map live in a private decorations map; expose it
// so the map overlay can draw where everyone is.
@Mixin(MapItemSavedData.class)
public interface MapDataAccessor {
    @Accessor("decorations") Map<String, MapDecoration> constellation$decorations();
}
