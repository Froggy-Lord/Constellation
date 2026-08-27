package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Set;

// ported from Skyblocker (LGPL-3.0): skyblock/item/ItemProtection.java
// cross-checked with Athen (BSD-3-Clause): modules/impl/general/ProtectItems.kt
// cross-checked with NoammAddons (CC0-1.0): features/impl/general/ProtectItem.kt
public final class ItemProtection {
    private static final Set<String> VALUABLE_CONSUMABLES = Set.of(
        "NEW_BOTTLE_OF_JYRRE", "DARK_CACAO_TRUFFLE", "DISCRITE", "MOBY_DUCK", "ROSEWATER_FLASK");
    private static KeyMapping protectKey;
    private static long lastNotice;
    private static String overrideKey = "";
    private static int overrideClicks;
    private static long lastOverrideClick;
    private static boolean initialized;

    public enum Type { UUID, SKYBLOCK_ID, STARRED, RECOMBOBULATED, NONE }

    private ItemProtection() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        protectKey = ConstellationClient.instance().keys().register("protect_item", GLFW.GLFW_KEY_P);
        ConstellationClient.tick().every(1, "phoenix-protect-key", ItemProtection::tickKey);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> {
                if (!active() || protectKey == null || !protectKey.matches(event)) return true;
                Slot hovered = ((ContainerScreenAccessor) container).constellation$hoveredSlot();
                if (hovered == null || hovered.getItem().isEmpty()) return true;
                toggle(hovered.getItem(), false);
                return false;
            });
        });
        UseItemCallback.EVENT.register((player, level, hand) ->
            blocksConsumable(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
            blocksConsumable(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!active() || !level.isClientSide() || !(entity instanceof ItemFrame frame) || !frame.getItem().isEmpty())
                return InteractionResult.PASS;
            return isProtected(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    public static boolean active() {
        PhoenixConfig cfg = config();
        return cfg != null && cfg.itemProtection && ConstellationClient.loc().onHypixel();
    }

    public static Type protectionType(ItemStack stack) {
        PhoenixConfig cfg = config();
        if (!active() || stack == null || stack.isEmpty()) return Type.NONE;
        ensureSets(cfg);
        String uuid = uuid(stack);
        if (!uuid.isEmpty() && cfg.protectedItemUuids.contains(uuid)) return Type.UUID;
        String id = skyblockId(stack);
        if (!id.isEmpty() && cfg.protectedSkyblockIds.contains(id)) return Type.SKYBLOCK_ID;
        CompoundTag extra = extra(stack);
        if (cfg.protectStarredItems && extra.getIntOr("upgrade_level", 0) > 0) return Type.STARRED;
        if (cfg.protectRecombobulatedItems && extra.getIntOr("rarity_upgrades", 0) > 0) return Type.RECOMBOBULATED;
        return Type.NONE;
    }

    public static boolean isProtected(ItemStack stack) { return protectionType(stack) != Type.NONE; }

    public static boolean isConfiguredProtected(ItemStack stack) {
        PhoenixConfig cfg = config();
        if (cfg == null || stack == null || stack.isEmpty()) return false;
        ensureSets(cfg);
        String uuid = uuid(stack);
        String id = skyblockId(stack);
        CompoundTag extra = extra(stack);
        return (!uuid.isEmpty() && cfg.protectedItemUuids.contains(uuid))
            || (!id.isEmpty() && cfg.protectedSkyblockIds.contains(id))
            || (cfg.protectStarredItems && extra.getIntOr("upgrade_level", 0) > 0)
            || (cfg.protectRecombobulatedItems && extra.getIntOr("rarity_upgrades", 0) > 0);
    }

    public static boolean shouldBlockClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, ContainerInput input) {
        if (!active()) return false;
        ItemStack stack = slotId == -999 ? screen.getMenu().getCarried() : slot == null ? ItemStack.EMPTY : slot.getItem();
        if (!isProtected(stack)) return false;
        if ((slotId == -999 || input == ContainerInput.THROW) && config().preventDroppingValuable)
            return blocked(stack, "dropping");
        String title = screen.getTitle().getString();
        String action = overrideAction(title, screen.getMenu());
        if (!action.isEmpty()) return confirmOverride(stack, action);
        return false;
    }

    public static boolean shouldBlockDrop(ItemStack stack) {
        PhoenixConfig cfg = config();
        if (!active() || !cfg.preventDroppingValuable || !isProtected(stack)) return false;
        if (ConstellationClient.loc().inDungeons()) return false;
        if (ConstellationClient.loc().area() == SkyblockArea.DUNGEON_HUB && stack.is(Items.ENCHANTED_BOOK)) return false;
        return blocked(stack, "dropping");
    }

    public static List<Component> appendTooltip(ItemStack stack, List<Component> original) {
        Type type = protectionType(stack);
        if (type == Type.NONE) return original;
        java.util.ArrayList<Component> result = new java.util.ArrayList<>(original);
        result.add(Math.min(1, result.size()), Component.literal("§aItem Protected §7(" + label(type) + ")"));
        return result;
    }

    public static boolean showMarker(ItemStack stack) {
        PhoenixConfig cfg = config();
        return cfg != null && cfg.showProtectedItemMarker && isProtected(stack);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<FabricClientCommandSource>literal("protectitem")
            .executes(ctx -> toggleHeld(false))
            .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<FabricClientCommandSource>literal("type")
                .executes(ctx -> toggleHeld(true)))
            .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<FabricClientCommandSource>literal("list")
                .executes(ctx -> list())));
    }

    private static int toggleHeld(boolean byType) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getMainHandItem().isEmpty()) { notice("Hold an item first."); return 0; }
        toggle(mc.player.getMainHandItem(), byType);
        return 1;
    }

    private static void toggle(ItemStack stack, boolean byType) {
        PhoenixConfig cfg = config();
        if (cfg == null) return;
        ensureSets(cfg);
        String uuid = uuid(stack), id = skyblockId(stack);
        Set<String> target;
        String value;
        String kind;
        if (!byType && !uuid.isEmpty()) { target = cfg.protectedItemUuids; value = uuid; kind = "item"; }
        else if (!id.isEmpty()) { target = cfg.protectedSkyblockIds; value = id; kind = "item type"; }
        else { notice("This item has no SkyBlock UUID or ID."); return; }
        boolean added = target.add(value);
        if (!added) target.remove(value);
        ConstellationClient.saveConfig();
        notice((added ? "Protected " : "Unprotected ") + kind + ": " + stack.getHoverName().getString());
    }

    private static int list() {
        PhoenixConfig cfg = config();
        ensureSets(cfg);
        notice("Protected UUIDs: " + cfg.protectedItemUuids.size() + ", item types: " + cfg.protectedSkyblockIds.size());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (String uuid : cfg.protectedItemUuids) mc.player.sendSystemMessage(Component.literal("§7UUID: §f" + uuid));
            for (String id : cfg.protectedSkyblockIds) mc.player.sendSystemMessage(Component.literal("§7Type: §f" + id));
        }
        return 1;
    }

    private static void tickKey() {
        if (!active() || protectKey == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || mc.player == null) return;
        while (protectKey.consumeClick()) toggle(mc.player.getMainHandItem(), false);
    }

    private static boolean blocksConsumable(ItemStack stack) {
        PhoenixConfig cfg = config();
        if (!active() || !cfg.protectValuableConsumables || !VALUABLE_CONSUMABLES.contains(skyblockId(stack))) return false;
        return blocked(stack, "using");
    }

    private static String overrideAction(String title, AbstractContainerMenu menu) {
        if (title.equals("Salvage Items")) return "salvaging";
        if (title.endsWith("Auction House") || title.equals("Create Auction") || title.equals("Create BIN Auction"))
            return "auctioning";
        if (title.startsWith("You  ")) return "trading";
        if (isNpcSellMenu(menu)) return "selling";
        return "";
    }

    private static boolean confirmOverride(ItemStack stack, String action) {
        long now = System.currentTimeMillis();
        String itemKey = uuid(stack);
        if (itemKey.isEmpty()) itemKey = skyblockId(stack);
        if (itemKey.isEmpty()) itemKey = stack.getItem().toString() + ':' + protectionType(stack);
        String key = action + ':' + itemKey;
        if (!key.equals(overrideKey) || now - lastOverrideClick > 3000) overrideClicks = 0;
        overrideKey = key;
        lastOverrideClick = now;
        overrideClicks++;
        if (overrideClicks >= 3) {
            overrideClicks = 0;
            overrideKey = "";
            notice("Protection override: " + action + " " + stack.getHoverName().getString());
            return false;
        }
        notice("Blocked " + action + " protected item: " + stack.getHoverName().getString()
            + ". Click " + (3 - overrideClicks) + " more " + ((3 - overrideClicks) == 1 ? "time" : "times") + " to override.");
        return true;
    }

    private static boolean isNpcSellMenu(AbstractContainerMenu menu) {
        for (Slot candidate : menu.slots) {
            ItemStack stack = candidate.getItem();
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString();
            if (name.equals("Sell Item") || name.equals("Sell Inventory")) return true;
            var lore = stack.get(DataComponents.LORE);
            if (lore != null) for (Component line : lore.lines())
                if (line.getString().toLowerCase(Locale.ROOT).contains("click to buyback")) return true;
        }
        return false;
    }

    private static boolean blocked(ItemStack stack, String action) {
        notice("Blocked " + action + " protected item: " + stack.getHoverName().getString());
        return true;
    }

    private static void notice(String text) {
        PhoenixConfig cfg = config();
        if (cfg == null || !cfg.itemProtectionNotifications) return;
        long now = System.currentTimeMillis();
        if (now - lastNotice < 250) return;
        lastNotice = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c" + text));
    }

    private static String uuid(ItemStack stack) { return extra(stack).getStringOr("uuid", ""); }
    private static String skyblockId(ItemStack stack) { return extra(stack).getStringOr("id", ""); }
    private static CompoundTag extra(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("ExtraAttributes");
    }
    private static String label(Type type) {
        return switch (type) { case UUID -> "UUID"; case SKYBLOCK_ID -> "SkyBlock ID";
            case STARRED -> "Starred"; case RECOMBOBULATED -> "Recombobulated"; default -> ""; };
    }
    private static PhoenixConfig config() {
        return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().phoenix;
    }
    private static void ensureSets(PhoenixConfig cfg) {
        if (cfg.protectedItemUuids == null) cfg.protectedItemUuids = new java.util.HashSet<>();
        if (cfg.protectedSkyblockIds == null) cfg.protectedSkyblockIds = new java.util.HashSet<>();
    }
}
