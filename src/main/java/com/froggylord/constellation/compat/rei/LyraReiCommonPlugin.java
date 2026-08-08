package com.froggylord.constellation.compat.rei;

import net.fabricmc.loader.api.FabricLoader;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/SkyblockerREICommonPlugin.java
public final class LyraReiCommonPlugin implements REICommonPlugin {
    @Override public void registerItemComparators(ItemComparatorRegistry registry) {
        if (!FabricLoader.getInstance().isModLoaded("skyblocker")) registry.registerGlobal(new LyraReiItemComparator());
    }
}
