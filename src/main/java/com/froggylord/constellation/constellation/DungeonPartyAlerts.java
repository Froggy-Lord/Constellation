package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.DungeonState;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

// ported from NoFrills (GPL-3.0): features/dungeons/DupeClassAlert.java
// ported from devonian (GPL-3.0):
// features/dungeons/clear/PartyDuplicateAlert.kt
// features/dungeons/clear/PartyNotFullAlert.kt
public final class DungeonPartyAlerts {
    private static boolean initialized;

    private DungeonPartyAlerts() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && message.getString().equals("Starting in 4 seconds."))
                ConstellationClient.tick().once(2, "orion-party-entry-alert", DungeonPartyAlerts::check);
            return true;
        });
    }

    private static void check() {
        if (!ConstellationClient.loc().inDungeons()) return;
        List<DungeonState.Teammate> teammates = ConstellationClient.dungeon().teammates();
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        for (DungeonState.Teammate teammate : teammates) {
            if (teammate.playerClass() == null || teammate.playerClass().isBlank()) continue;
            byClass.computeIfAbsent(teammate.playerClass(), ignored -> new ArrayList<>()).add(teammate.name());
        }

        List<String> warnings = new ArrayList<>();
        if (teammates.size() < 5) warnings.add("Party not full: " + teammates.size() + "/5");
        for (var entry : byClass.entrySet()) {
            if (entry.getValue().size() > 1)
                warnings.add("Duplicate " + entry.getKey() + ": " + String.join(", ", entry.getValue()));
        }
        if (warnings.isEmpty()) return;

        PartyMessages.send("party-composition", Map.of("warnings", String.join(" | ", warnings)));

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.gui.hud.resetTitleTimes();
        mc.gui.hud.setTitle(Component.literal("§c" + String.join(" | ", warnings)));
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .8f, .7f);
    }
}
