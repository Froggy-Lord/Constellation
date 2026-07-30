package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/chocolatefactory/hitman/HitmanSlots.kt
public final class AurigaHitmanCosts {
    public record State(int owned, int total, long paid, long remaining, List<Long> next, boolean menuOpen, boolean synced) {}

    private static final Pattern TITLE = Pattern.compile("^Rabbit Hitman$");
    private static final Pattern COST = Pattern.compile(".*Cost ([\\d,]+) Coins.*");
    // ported from SkyHanni Repo (MIT): constants/HoppityEggLocations.json hitman_costs
    private static final List<Long> COSTS = List.of(
        1_000L, 10_000L, 100_000L, 200_000L, 400_000L, 600_000L, 800_000L,
        1_000_000L, 1_500_000L, 2_000_000L, 3_000_000L, 4_000_000L, 5_000_000L,
        6_000_000L, 7_000_000L, 8_500_000L, 10_000_000L, 12_000_000L,
        14_000_000L, 16_000_000L, 18_000_000L, 20_000_000L, 22_000_000L,
        24_000_000L, 26_000_000L, 28_000_000L, 30_000_000L, 32_000_000L);

    private static AurigaConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static String fingerprint = "";
    private static State state = empty(false);

    private AurigaHitmanCosts() {}

    public static void init(AurigaConfig config) {
        cfg = config;
        normalize();
        restore();
        ScreenEvents.AFTER_INIT.register((client, opened, width, height) -> {
            if (!(opened instanceof AbstractContainerScreen<?> container)
                || !TITLE.matcher(clean(container.getTitle().getString())).matches()) return;
            screen = container;
            update(container);
            ScreenEvents.afterTick(opened).register(ignored -> update(container));
            ScreenEvents.remove(opened).register(ignored -> {
                if (screen == container) {
                    screen = null;
                    fingerprint = "";
                    restore();
                }
            });
        });
    }

    private static void update(AbstractContainerScreen<?> container) {
        if (!active() || container != screen) return;
        String nextFingerprint = container.getMenu().slots.stream()
            .filter(slot -> !playerSlot(slot))
            .map(slot -> clean(slot.getItem().getHoverName().getString()) + lore(slot.getItem()))
            .toList().toString();
        if (nextFingerprint.equals(fingerprint)) return;
        fingerprint = nextFingerprint;

        int menuSlots = 0;
        int left = 0;
        ArrayList<Long> observed = new ArrayList<>();
        for (Slot slot : container.getMenu().slots) {
            if (playerSlot(slot) || slot.index < 0 || slot.index > 53) continue;
            menuSlots++;
            if (border(slot.index) || slot.getItem().isEmpty()) continue;
            for (String line : lore(slot.getItem())) {
                var matcher = COST.matcher(line);
                if (!matcher.matches()) continue;
                left++;
                observed.add(number(matcher.group(1)));
                break;
            }
        }
        if (menuSlots < 54) {
            state = withMenu(state, true, false);
            return;
        }
        int owned = Math.clamp(COSTS.size() - left, 0, COSTS.size());
        if (!observed.isEmpty() && !pricesMatch(owned, observed)) {
            state = withMenu(state, true, false);
            return;
        }
        String profile = profile();
        Integer old = cfg.chocolateFactoryHitmanOwnedSlots.put(profile, owned);
        state = calculate(owned, true, true);
        if (cfg.chocolateFactoryHitmanPersistProfiles && !Integer.valueOf(owned).equals(old))
            ConstellationClient.saveConfig();
    }

    private static boolean pricesMatch(int owned, List<Long> observed) {
        List<Long> expected = COSTS.subList(owned, COSTS.size());
        return expected.containsAll(observed) && observed.stream().distinct().count() == observed.size();
    }

    private static State calculate(int owned, boolean menu, boolean synced) {
        int safe = Math.clamp(owned, 0, COSTS.size());
        long paid = COSTS.subList(0, safe).stream().mapToLong(Long::longValue).sum();
        long remaining = COSTS.subList(safe, COSTS.size()).stream().mapToLong(Long::longValue).sum();
        int rows = cfg == null ? 5 : Math.clamp(cfg.chocolateFactoryHitmanNextRows, 1, 10);
        return new State(safe, COSTS.size(), paid, remaining,
            List.copyOf(COSTS.subList(safe, Math.min(COSTS.size(), safe + rows))), menu, synced);
    }

    private static void restore() {
        if (cfg == null) return;
        normalize();
        state = calculate(cfg.chocolateFactoryHitmanOwnedSlots.getOrDefault(profile(), 0), false, false);
    }

    public static State state() { return state; }
    public static AurigaConfig config() { return cfg; }
    public static boolean visible() {
        return active() && (state.menuOpen || cfg.chocolateFactoryHitmanShowOutsideMenu)
            && (state.synced || cfg.chocolateFactoryHitmanOwnedSlots.containsKey(profile()));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hitmancosts")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(context -> clear()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("next")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("rows", IntegerArgumentType.integer(1, 10))
                    .executes(context -> rows(IntegerArgumentType.getInteger(context, "rows")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("Slots " + state.owned + "/" + state.total + ", paid " + coins(state.paid)
            + ", remaining " + coins(state.remaining) + (state.synced ? ", menu synced." : ", cached."));
        return 1;
    }

    private static int rows(int value) {
        cfg.chocolateFactoryHitmanNextRows = value;
        state = calculate(state.owned, state.menuOpen, state.synced);
        ConstellationClient.saveConfig();
        local("Next-slot rows set to " + value + ".");
        return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = bool(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.chocolateFactoryHitmanCosts = value;
            case "hud" -> cfg.chocolateFactoryHitmanCostsHud = value;
            case "purchased" -> cfg.chocolateFactoryHitmanShowPurchased = value;
            case "paid" -> cfg.chocolateFactoryHitmanShowPaid = value;
            case "remaining" -> cfg.chocolateFactoryHitmanShowRemaining = value;
            case "remainingcost" -> cfg.chocolateFactoryHitmanShowRemainingCost = value;
            case "next" -> cfg.chocolateFactoryHitmanShowNext = value;
            case "outside" -> cfg.chocolateFactoryHitmanShowOutsideMenu = value;
            case "persist" -> cfg.chocolateFactoryHitmanPersistProfiles = value;
            default -> { local("Unknown Hitman cost option."); return 0; }
        }
        ConstellationClient.saveConfig();
        return status();
    }

    private static int clear() {
        cfg.chocolateFactoryHitmanOwnedSlots.remove(profile());
        ConstellationClient.saveConfig();
        state = empty(screen != null);
        local("Current profile Hitman slot cache cleared.");
        return 1;
    }

    public static String coins(long value) { return String.format(Locale.US, "%,d Coins", value); }
    private static State withMenu(State value, boolean menu, boolean synced) {
        return new State(value.owned, value.total, value.paid, value.remaining, value.next, menu, synced);
    }
    private static State empty(boolean menu) { return new State(0, COSTS.size(), 0, COSTS.stream().mapToLong(Long::longValue).sum(), List.of(), menu, false); }
    private static boolean border(int index) { return index <= 8 || index >= 45 || index % 9 == 0 || (index + 1) % 9 == 0; }
    private static List<String> lore(ItemStack stack) { ItemLore value=stack.get(DataComponents.LORE);return value==null?List.of():value.lines().stream().map(line->clean(line.getString())).toList(); }
    private static String clean(String value) { String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.strip(); }
    private static long number(String value) { try{return Long.parseLong(value.replace(",",""));}catch(Exception ignored){return 0;} }
    private static boolean playerSlot(Slot slot) { Minecraft mc=Minecraft.getInstance();return mc.player!=null&&slot.container==mc.player.getInventory(); }
    private static boolean active() { return cfg!=null&&cfg.enabled&&cfg.chocolateFactoryHitmanCosts&&ConstellationClient.loc().onHypixel(); }
    private static String profile() { String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT); }
    private static Boolean bool(String value) { return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;}; }
    private static void normalize() { if(cfg.chocolateFactoryHitmanOwnedSlots==null)cfg.chocolateFactoryHitmanOwnedSlots=new LinkedHashMap<>();cfg.chocolateFactoryHitmanNextRows=Math.clamp(cfg.chocolateFactoryHitmanNextRows,1,10); }
    private static void local(String text) { Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a76[Hitman Costs] \u00a7f"+text)); }
}
