package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.VisualConfig;
import com.froggylord.constellation.constellation.AndromedaRiftCore;
import com.froggylord.constellation.ui.SkyblockCraftingTableMenu;
import com.froggylord.constellation.ui.SkyblockCraftingTableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(MenuScreens.ScreenConstructor.class)
public interface SkyblockCraftingTableScreenMixin<T extends AbstractContainerMenu> {
    // ported from Skyblocker (LGPL-3.0-or-later): mixins/MenuScreensConstructorMixin.java
    @Inject(method = "fromPacket", at = @At("HEAD"), cancellable = true)
    private void constellation$skyblockCraftingTable(Component title, MenuType<T> type,
                                                      Minecraft minecraft, int id, CallbackInfo ci) {
        LocalPlayer player = minecraft.player;
        VisualConfig config = config();
        if (player == null || config == null || !config.enabled || !config.skyblockCraftingTable) return;
        boolean local = minecraft.hasSingleplayerServer() && config.skyblockCraftingTableLocalWorlds;
        if (!ConstellationClient.loc().onHypixel() && !local) return;
        String cleanTitle = title.getString().strip().toLowerCase(Locale.ROOT);
        if (!cleanTitle.contains("craft item")) return;
        boolean mirrorverse = AndromedaRiftCore.currentArea().equalsIgnoreCase("Mirrorverse")
            || ConstellationClient.loc().getSidebarLines().stream()
                .anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("mirrorverse"))
            || local && cleanTitle.contains("mirrorverse");
        if (mirrorverse && !config.skyblockCraftingTableMirrorverseLayout) return;
        T created = type.create(id, player.getInventory());
        if (!(created instanceof ChestMenu chest)) return;
        if (chest.getRowCount() < 4) {
            chest.getContainer().stopOpen(player);
            return;
        }
        chest.getContainer().stopOpen(player);
        SkyblockCraftingTableMenu menu = SkyblockCraftingTableMenu.from(chest, player.getInventory(),
            mirrorverse, config.skyblockCraftingTableQuickCrafts);
        player.containerMenu = menu;
        minecraft.gui.setScreen(new SkyblockCraftingTableScreen(menu, player.getInventory(), title));
        ci.cancel();
    }

    private static VisualConfig config() {
        try { return ConstellationClient.cfg().visual; }
        catch (RuntimeException ignored) { return null; }
    }
}
