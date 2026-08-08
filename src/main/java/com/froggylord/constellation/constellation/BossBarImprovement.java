package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

// ported from Devonian (GPL-3.0): features/bossbar/BossBarHealth.kt and features/bossbar/BossBar.kt
public final class BossBarImprovement {
    private BossBarImprovement() {}

    public static Collection<LerpingBossEvent> modify(Collection<LerpingBossEvent> source) {
        PerseusConfig cfg = ConstellationClient.cfg().perseus;
        if (cfg == null || !cfg.enabled || !cfg.bossBarImprovement || !active(cfg)) return source;
        List<LerpingBossEvent> result = new ArrayList<>(source.size());
        for (LerpingBossEvent event : source) {
            Component name = formatName(event.getName(), event.getProgress(), cfg);
            result.add(new LerpingBossEvent(event.getId(), name, event.getProgress(), event.getColor(), event.getOverlay(),
                event.shouldDarkenScreen(), event.shouldPlayBossMusic(), event.shouldCreateWorldFog()));
        }
        return result;
    }

    private static boolean active(PerseusConfig cfg) {
        SkyblockArea area = ConstellationClient.loc().area();
        return cfg.bossBarDungeons && ConstellationClient.loc().inDungeons() && ConstellationClient.dungeon().inBoss()
            || cfg.bossBarKuudra && area == SkyblockArea.KUUDRA;
    }

    public static Component formatName(Component original, float progress, PerseusConfig cfg) {
        int decimals = Math.clamp(cfg.bossBarDecimals, 0, 2);
        double percent = Math.clamp(progress, 0.0f, 1.0f) * 100.0;
        String value = String.format(Locale.US, "%." + decimals + "f%%", percent);
        MutableComponent name = original.copy();
        name.append(Component.literal(" - ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(cfg.bossBarSeparatorColour & 0xFFFFFF))));
        name.append(Component.literal(value).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(cfg.bossBarHealthColour & 0xFFFFFF))));
        return name;
    }
}
