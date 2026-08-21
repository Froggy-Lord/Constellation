package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// ported from devonian (GPL-3.0): features/dungeons/DungeonBreakerCharges.kt
public final class DungeonBreakerCharges {
    private static int charges = 20;
    private static boolean available;
    private static boolean initialized;

    private DungeonBreakerCharges() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(2, "orion-dungeon-breaker", DungeonBreakerCharges::scan);
    }

    public static String hudText() {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.dungeonBreakerDisplay || !ConstellationClient.loc().inDungeons() || !available) return null;
        String colour = charges >= 15 ? "§6" : charges >= 10 ? "§a" : "§c";
        return "§aCharges§f: " + colour + charges;
    }

    private static void scan() {
        Minecraft mc = Minecraft.getInstance();
        var cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.dungeonBreakerDisplay || !ConstellationClient.loc().inDungeons() || mc.player == null) {
            reset();
            return;
        }

        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!isDungeonBreaker(stack)) continue;
            Integer damage = stack.get(DataComponents.DAMAGE);
            int usedCharge = damage == null ? 1 : damage;
            charges = Math.clamp(20 - usedCharge / 78, 0, 20);
            available = true;
            return;
        }
        reset();
    }

    private static boolean isDungeonBreaker(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        String id = data.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("id", "");
        return id.equals("DUNGEONBREAKER");
    }

    private static void reset() {
        charges = 20;
        available = false;
    }
}
