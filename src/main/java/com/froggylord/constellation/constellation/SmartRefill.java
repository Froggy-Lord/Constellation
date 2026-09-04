package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.ui.SmartRefillScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// inventory counting and refill amounts ported from Devonian (GPL-3.0): features/dungeons/RefillGFSCommands.kt
// cross-checked with NoFrills (GPL-3.0): misc/Utils.java
// one-command pacing cross-checked with NoammAddons (CC0-1.0): features/impl/dungeon/AutoGFS.kt
public final class SmartRefill {
    private static OrionConfig cfg;
    private static KeyMapping key;
    private static long lastSendTick = -1000;
    private static boolean initialized;

    private SmartRefill() {}

    public static void init(OrionConfig config) {
        cfg = config;
        ensure();
        if (initialized) return;
        initialized = true;
        key = ConstellationClient.instance().keys().register("smart_refill", InputConstants.UNKNOWN.getValue());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) refill();
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(root("smartrefill"));
        dispatcher.register(root("refill"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> root(String name) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal(name)
            .executes(c -> refill())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("config").executes(c -> open()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("all").executes(c -> refillAll()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("id", StringArgumentType.word())
                    .executes(c -> toggle(StringArgumentType.getString(c, "id")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("set")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("id", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("amount", IntegerArgumentType.integer(0, 2304))
                        .executes(c -> set(StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "amount"))))));
    }

    public static int refill() {
        if (!ready()) return 0;
        List<Need> needs = needs();
        if (needs.isEmpty()) { local("Configured items are already full."); return 1; }
        Need selected = needs.stream().max(Comparator.comparingDouble(Need::missingRatio).thenComparingInt(Need::missing)).orElseThrow();
        send(selected);
        return 1;
    }

    public static int refillAll() {
        if (!ready()) return 0;
        List<Need> needs = needs();
        if (needs.isEmpty()) { local("Configured items are already full."); return 1; }
        if (cfg.smartRefillOneAtATime) {
            local("One-at-a-time mode is enabled; pulling the lowest stack only.");
            return refill();
        }
        Need first = needs.stream().max(Comparator.comparingDouble(Need::missingRatio)).orElseThrow();
        send(first);
        int delay = Math.max(10, cfg.smartRefillCooldownTicks);
        int index = 1;
        for (Need need : needs) {
            if (need == first) continue;
            int step = index++;
            ConstellationClient.tick().once(delay * step, "smart-refill-" + step, () -> {
                if (ready(false)) send(need);
            });
        }
        return 1;
    }

    private static void send(Need need) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        lastSendTick = ConstellationClient.tick().getTickCount();
        mc.player.connection.sendCommand("gfs " + need.query + " " + need.missing());
        local("Requested " + need.missing() + " " + display(need.id) + " (" + need.current + "/" + need.target + ").");
    }

    private static List<Need> needs() {
        ensure();
        Map<String, Integer> counts = counts();
        List<Need> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : cfg.smartRefillTargets.entrySet()) {
            String id = normalize(entry.getKey());
            if (!cfg.smartRefillEnabled.contains(id) || entry.getValue() <= 0) continue;
            int current = counts.getOrDefault(id, 0), target = Math.clamp(entry.getValue(), 0, 2304);
            if (current < target) result.add(new Need(id, id.toLowerCase(Locale.ROOT).replace('_', ' '), current, target));
        }
        return result;
    }

    private static Map<String, Integer> counts() {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Integer> result = new LinkedHashMap<>();
        if (mc.player == null) return result;
        for (ItemStack stack : mc.player.getInventory()) {
            String id = skyblockId(stack);
            if (!id.isEmpty()) result.merge(id, stack.getCount(), Integer::sum);
        }
        return result;
    }

    private static String skyblockId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        CompoundTag extra = data.copyTag().getCompoundOrEmpty("ExtraAttributes");
        return normalize(extra.getStringOr("id", ""));
    }

    public static int set(String rawId, int amount) {
        ensure(); String id = normalize(rawId);
        if (amount == 0) { cfg.smartRefillTargets.remove(id); cfg.smartRefillEnabled.remove(id); }
        else { cfg.smartRefillTargets.put(id, amount); cfg.smartRefillEnabled.add(id); }
        ConstellationClient.saveConfig(); local((amount == 0 ? "Removed " : "Set ") + display(id) + (amount == 0 ? "." : " target to " + amount + '.')); return 1;
    }

    public static int toggle(String rawId) {
        ensure(); String id = normalize(rawId);
        if (!cfg.smartRefillTargets.containsKey(id)) { local("Unknown item. Use /refill set " + id + " <amount> first."); return 0; }
        boolean enabled = cfg.smartRefillEnabled.remove(id);
        if (!enabled) cfg.smartRefillEnabled.add(id);
        ConstellationClient.saveConfig(); local(display(id) + (enabled ? " disabled." : " enabled.")); return 1;
    }

    public static int status() {
        ensure(); Map<String, Integer> count = counts();
        for (Map.Entry<String, Integer> e : cfg.smartRefillTargets.entrySet()) {
            String id = normalize(e.getKey());
            local((cfg.smartRefillEnabled.contains(id) ? "on  " : "off ") + display(id) + ": " + count.getOrDefault(id, 0) + "/" + e.getValue());
        }
        return 1;
    }

    public static int open() { Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new SmartRefillScreen(null))); return 1; }
    public static Map<String, Integer> targets() { ensure(); return new LinkedHashMap<>(cfg.smartRefillTargets); }
    public static boolean enabled(String id) { ensure(); return cfg.smartRefillEnabled.contains(normalize(id)); }
    public static void change(String id, int delta) { set(id, Math.clamp(cfg.smartRefillTargets.getOrDefault(normalize(id), 0) + delta, 0, 2304)); }

    private static boolean ready() { return ready(true); }
    private static boolean ready(boolean feedback) {
        Minecraft mc = Minecraft.getInstance();
        if (cfg == null || !cfg.enabled || !cfg.smartRefill || !ConstellationClient.loc().onHypixel() || mc.player == null || mc.player.connection == null) return false;
        long left = cfg.smartRefillCooldownTicks - (ConstellationClient.tick().getTickCount() - lastSendTick);
        if (left > 0) { if (feedback) local("Wait " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "s before another refill."); return false; }
        return true;
    }

    private static void ensure() {
        if (cfg == null) return;
        if (cfg.smartRefillTargets == null) cfg.smartRefillTargets = new LinkedHashMap<>();
        if (cfg.smartRefillEnabled == null) cfg.smartRefillEnabled = new LinkedHashSet<>();
        cfg.smartRefillTargets.replaceAll((id, amount) -> Math.clamp(amount == null ? 0 : amount, 0, 2304));
        cfg.smartRefillEnabled = cfg.smartRefillEnabled.stream().map(SmartRefill::normalize).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalize(String id) { return id == null ? "" : id.trim().toUpperCase(Locale.ROOT).replace(' ', '_'); }
    private static String display(String id) { String s = normalize(id).toLowerCase(Locale.ROOT).replace('_', ' '); return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§bRefill §8> §f" + text)); }
    private record Need(String id, String query, int current, int target) { int missing() { return target - current; } double missingRatio() { return target <= 0 ? 0 : (double) missing() / target; } }
}
