package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.mixin.InventoryAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArchitectNotifier {
    // ported from devonian (GPL-3.0): features/dungeons/clear/AutoArchitectDraft.kt
    private static final Pattern PUZZLE_FAIL = Pattern.compile("^PUZZLE FAIL! (?<player>\\w{1,16}) .*$");
    private static final Pattern QUIZ_FAIL = Pattern.compile(
        "^\\[STATUE] Oruo the Omniscient: (?<player>\\w{1,16}) chose the wrong answer! I shall never forget this moment of misrememberance\\.$");
    // ported from NoammAddons (CC0-1.0): features/impl/dungeon/ArchitectDraft.kt
    private static final Pattern DRAFT_USED = Pattern.compile("^You used the Architect's First Draft to reset (.+)!$");
    private static final String DRAFT_ID = "ARCHITECT_FIRST_DRAFT";
    private static KeyMapping useKey;
    private static OrionConfig cfg;
    private static boolean initialized;

    private ArchitectNotifier() {}

    // ported from SkyHanni (LGPL-2.1): features/dungeon/DungeonArchitectFeatures.kt
    public static void init(OrionConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        useKey = ConstellationClient.instance().keys().register("use_architect_draft", InputConstants.UNKNOWN.getValue());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (useKey.consumeClick()) useDraft();
        });
        ClientPlayConnectionEvents.JOIN.register(ArchitectNotifier::reset);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(null, null, client));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || !config.architectNotifier || !ConstellationClient.loc().inDungeons()) return true;
            String value = message.getString();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return true;

            Matcher used = DRAFT_USED.matcher(value);
            if (used.matches()) {
                PartyMessages.send("draft-used", Map.of("puzzle", used.group(1)));
                return true;
            }

            if (ConstellationClient.dungeon().inBoss()) return true;
            Matcher failed = PUZZLE_FAIL.matcher(value);
            if (!failed.matches()) failed = QUIZ_FAIL.matcher(value);
            if (!failed.matches() || !failed.group("player").equalsIgnoreCase(mc.player.getName().getString())) return true;
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTitle(Component.literal("§cPUZZLE FAILED"));
            mc.gui.hud.setSubtitle(Component.literal("§eGet or use an Architect's First Draft"));
            showActions(mc);
            return true;
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("getdraft")
            .executes(ctx -> getDraft()));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("usedraft")
            .executes(ctx -> useDraft()));
    }

    // item lookup and delayed slot restore ported from NoFrills (GPL-3.0): misc/Utils.java
    private static int useDraft() {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return 0;
        int slot = hotbarDraft(mc);
        if (slot < 0) {
            local(mc, "No Architect's First Draft is in your hotbar. Click Get Draft first.");
            return 0;
        }
        InventoryAccessor inventory = (InventoryAccessor) mc.player.getInventory();
        int previous = inventory.constellation$selected();
        inventory.constellation$setSelected(slot);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (previous != slot) ConstellationClient.tick().once(2, "architect-restore-slot", () -> {
            if (mc.player != null) ((InventoryAccessor) mc.player.getInventory()).constellation$setSelected(previous);
        });
        return 1;
    }

    // ported from Devonian (GPL-3.0): features/dungeons/clear/AutoArchitectDraft.kt
    private static int getDraft() {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return 0;
        mc.player.connection.sendCommand("gfs architect's first draft 1");
        local(mc, "Requested one Architect's First Draft. Put it in your hotbar, then use the draft key.");
        return 1;
    }

    private static boolean ready(Minecraft mc) {
        if (cfg == null || !cfg.enabled || !cfg.architectNotifier || !ConstellationClient.loc().inDungeons()
            || ConstellationClient.dungeon().inBoss() || mc.player == null || mc.player.connection == null) {
            local(mc, "Architect Draft actions only work during dungeon clear.");
            return false;
        }
        return true;
    }

    private static int hotbarDraft(Minecraft mc) {
        for (int slot = 0; slot < 9; slot++) if (DRAFT_ID.equals(id(mc.player.getInventory().getItem(slot)))) return slot;
        return -1;
    }

    private static String id(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag extra = data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes");
        return extra.getStringOr("id", "");
    }

    private static void showActions(Minecraft mc) {
        Component line = Component.literal("§6Constellation §8» §f")
            .append(Component.literal("§a[Get Draft]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/getdraft"))))
            .append(Component.literal(" §7or "))
            .append(Component.literal("§e[Use Hotbar Draft]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/usedraft"))))
            .append(Component.literal(" §8(keybind available in Controls)"));
        mc.player.sendSystemMessage(line);
    }

    private static void local(Minecraft mc, String text) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6Constellation §8» §f" + text));
    }

    private static void reset(net.minecraft.client.multiplayer.ClientPacketListener handler, PacketSender sender, Minecraft client) {
        ConstellationClient.tick().cancel("architect-restore-slot");
    }
}
