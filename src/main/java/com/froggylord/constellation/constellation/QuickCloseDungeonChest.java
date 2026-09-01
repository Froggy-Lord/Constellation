package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;

import java.util.Set;

/** Keyboard-only fast close for dungeon secret chests and optionally reward chests. */
public final class QuickCloseDungeonChest {
    // ported from NoFrills (GPL-3.0): misc/DungeonUtil.java and features/dungeons/QuickClose.java
    private static final Set<String> REWARD_CHESTS = Set.of(
        "Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock",
        "Wood Chest", "Gold Chest", "Diamond Chest", "Emerald Chest", "Obsidian Chest", "Bedrock Chest");

    private QuickCloseDungeonChest() {}

    public static boolean shouldClose(AbstractContainerScreen<?> screen, KeyEvent event) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.enabled || !cfg.quickCloseDungeonChests) return false;
        if (!ConstellationClient.loc().inDungeons() || !(screen instanceof ContainerScreen)) return false;

        String title = screen.getTitle().getString().trim();
        boolean secret = title.equals("Chest") && !ConstellationClient.dungeon().inBoss();
        boolean reward = cfg.quickCloseRewardChests && isRewardChest(title);
        if (!secret && !reward) return false;

        if (cfg.quickCloseAnyKey) return true;
        Minecraft mc = Minecraft.getInstance();
        if (cfg.quickCloseMovementKeys && (mc.options.keyUp.matches(event) || mc.options.keyLeft.matches(event)
            || mc.options.keyDown.matches(event) || mc.options.keyRight.matches(event))) return true;
        return cfg.quickCloseCrouchKey && mc.options.keyShift.matches(event);
    }

    private static boolean isRewardChest(String title) {
        if (REWARD_CHESTS.contains(title)) return true;
        for (String chest : REWARD_CHESTS) {
            if (!chest.endsWith(" Chest") && title.startsWith(chest) && title.endsWith("Chest")) return true;
        }
        return false;
    }
}
