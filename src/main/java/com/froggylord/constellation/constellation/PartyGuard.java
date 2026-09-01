package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.DungeonProfileApi;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.ui.PartyGuardScreen;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from devonian (GPL-3.0): features/dungeons/AutoKick.kt
// cross-checked with Athen (BSD-3-Clause): modules/impl/dungeon/PartyFinder.kt
// cross-checked with NoammAddons (CC0-1.0): features/impl/dungeon/PartyFinder.kt
public final class PartyGuard {
    private static final Pattern JOIN = Pattern.compile(
        "^Party Finder > (?:\\[[^]]+] ?)?(\\w{1,16}) joined the dungeon group! \\(.*\\)$");
    private static final Pattern FLOOR = Pattern.compile("(?:Currently Selected: |Floor: )Floor ([IV]+)");
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Integer> generations = new ConcurrentHashMap<>();
    private static OrionConfig cfg;
    private static String detectedFloor = "";
    private static boolean detectedMaster;
    private static boolean leaderKnown;
    private static boolean initialized;

    private PartyGuard() {}

    public static void init(OrionConfig config) {
        cfg = config;
        ensureSets();
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(message.getString());
            return true;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            String title = container.getTitle().getString();
            if (!title.equals("Group Builder") && !title.equals("Party Finder")) return;
            ScreenEvents.afterExtract(screen).register((ignored, graphics, mouseX, mouseY, delta) -> observe(container));
        });
    }

    private static void onChat(String raw) {
        if (cfg == null || raw == null) return;
        String message = net.minecraft.ChatFormatting.stripFormatting(raw).trim();
        if (message.equals("Queueing your party...")) leaderKnown = true;
        if (message.contains("You are not the party leader") || message.contains("The party was disbanded")) leaderKnown = false;
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains(" left the party") || lower.contains(" was removed from the party") || lower.startsWith("kicked ")) {
            for (String player : generations.keySet()) if (lower.contains(player)) invalidate(player);
        }
        Matcher join = JOIN.matcher(message);
        if (!join.matches() || !active()) return;
        String player = join.group(1);
        String key = player.toLowerCase(Locale.ROOT);
        int generation = generations.merge(key, 1, Integer::sum);
        evaluate(player, generation);
    }

    private static void evaluate(String player, int generation) {
        String key = player.toLowerCase(Locale.ROOT);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || key.equals(mc.player.getGameProfile().name().toLowerCase(Locale.ROOT))) return;
        ensureSets();
        if (cfg.partyGuardWhitelist.contains(key)) {
            local("Allowed " + player + ": whitelisted.", 0xFF55FF55);
            return;
        }
        if (cfg.partyGuardBlacklist.contains(key)) {
            decide(player, generation, null, List.of("blacklisted"));
            return;
        }
        if (!leaderKnown) {
            local("Did not evaluate " + player + ": party leadership is not confirmed.", 0xFFFFFF55);
            return;
        }
        DungeonProfileApi.Profile profile = DungeonProfileApi.get(player);
        if (profile != null) {
            decide(player, generation, profile, reasons(profile));
            return;
        }
        if (!pending.add(key)) return;
        DungeonProfileApi.request(List.of(player));
        poll(player, generation, 0);
    }

    private static void poll(String player, int generation, int attempt) {
        ConstellationClient.tick().once(10, "party-guard-" + player.toLowerCase(Locale.ROOT), () -> {
            if (!current(player, generation)) { pending.remove(player.toLowerCase(Locale.ROOT)); return; }
            DungeonProfileApi.Profile profile = DungeonProfileApi.get(player);
            if (profile != null) {
                pending.remove(player.toLowerCase(Locale.ROOT));
                decide(player, generation, profile, reasons(profile));
            } else if (attempt < 20 && active()) {
                DungeonProfileApi.request(List.of(player));
                poll(player, generation, attempt + 1);
            } else {
                pending.remove(player.toLowerCase(Locale.ROOT));
                local("Allowed " + player + ": profile data unavailable.", 0xFFFFFF55);
            }
        });
    }

    private static List<String> reasons(DungeonProfileApi.Profile profile) {
        List<String> reasons = new ArrayList<>();
        if (cfg.partyGuardMinCata > 0 && profile.cata() < cfg.partyGuardMinCata)
            reasons.add("Cata " + round(profile.cata()) + "/" + cfg.partyGuardMinCata);
        if (cfg.partyGuardMinSecrets > 0 && profile.secrets() < cfg.partyGuardMinSecrets)
            reasons.add("Secrets " + compact(profile.secrets()) + "/" + compact(cfg.partyGuardMinSecrets));
        if (cfg.partyGuardMinAverageSecrets > 0 && profile.averageSecrets() < cfg.partyGuardMinAverageSecrets)
            reasons.add("Avg " + round(profile.averageSecrets()) + "/" + round(cfg.partyGuardMinAverageSecrets));
        if (cfg.partyGuardMinMagicalPower > 0 && profile.magicalPower() < cfg.partyGuardMinMagicalPower)
            reasons.add("MP " + profile.magicalPower() + "/" + cfg.partyGuardMinMagicalPower);
        Floor floor = floor();
        if (cfg.partyGuardMaxPbSeconds > 0 && floor != null) {
            String pb = DungeonProfileApi.personalBest(profile, floor.master, floor.number);
            int seconds = seconds(pb);
            if (seconds < 0 && cfg.partyGuardKickMissingPb) reasons.add(floor.name() + " PB none");
            else if (seconds > cfg.partyGuardMaxPbSeconds) reasons.add(floor.name() + " PB " + pb + "/" + time(cfg.partyGuardMaxPbSeconds));
        }
        return reasons;
    }

    private static void decide(String player, int generation, DungeonProfileApi.Profile profile, List<String> reasons) {
        ensureSets();
        String key = player.toLowerCase(Locale.ROOT);
        if (!current(player, generation) || cfg.partyGuardWhitelist.contains(key)) return;
        if (reasons.isEmpty()) {
            local("Allowed " + player + profileSummary(profile), 0xFF55FF55);
            return;
        }
        String joined = String.join(", ", reasons);
        if (cfg.partyGuardDryRun || !leaderKnown) {
            local("Would kick " + player + ": " + joined, 0xFFFFAA00);
            manualKick(player);
            return;
        }
        local("Kicking " + player + ": " + joined, 0xFFFF5555);
        if (cfg.partyGuardSendReason) {
            String text = template(cfg.partyGuardKickMessage, player, joined, profile);
            String command = cfg.partyGuardPrivateReason ? "msg " + player + " " + text : "pc " + text;
            send(command);
        }
        ConstellationClient.tick().once(Math.max(2, Math.min(100, cfg.partyGuardKickDelayTicks)),
            "party-guard-kick-" + key, () -> {
                if (active() && leaderKnown && current(player, generation) && !cfg.partyGuardWhitelist.contains(key))
                    send("party kick " + player);
            });
    }

    private static String template(String value, String player, String reasons, DungeonProfileApi.Profile profile) {
        Floor floor = floor();
        String pb = profile == null || floor == null ? "-" : DungeonProfileApi.personalBest(profile, floor.master, floor.number);
        String result = (value == null ? "{player}, kicked: {reasons}" : value)
            .replace("{player}", player).replace("{reasons}", reasons)
            .replace("{cata}", profile == null ? "-" : round(profile.cata()))
            .replace("{secrets}", profile == null ? "-" : Integer.toString(profile.secrets()))
            .replace("{average}", profile == null ? "-" : round(profile.averageSecrets()))
            .replace("{mp}", profile == null ? "-" : Integer.toString(profile.magicalPower()))
            .replace("{pb}", pb).replace("{floor}", floor == null ? "-" : floor.name())
            .replace('\n', ' ').replace('\r', ' ');
        return result.substring(0, Math.min(120, result.length()));
    }

    private static void observe(AbstractContainerScreen<?> screen) {
        boolean master = detectedMaster;
        String found = detectedFloor;
        for (Slot slot : screen.getMenu().slots) for (String line : lore(slot)) {
            if (line.contains("Currently Selected: Master Mode The Catacombs") || line.contains("Dungeon: Master Mode The Catacombs")) master = true;
            if (line.contains("Currently Selected: The Catacombs") || line.equals("Dungeon: The Catacombs")) master = false;
            Matcher match = FLOOR.matcher(line);
            if (match.matches()) found = Integer.toString(roman(match.group(1)));
        }
        detectedMaster = master;
        detectedFloor = found;
    }

    public static int addList(boolean whitelist, String player) {
        ensureSets();
        String key = player.toLowerCase(Locale.ROOT);
        Set<String> add = whitelist ? cfg.partyGuardWhitelist : cfg.partyGuardBlacklist;
        Set<String> remove = whitelist ? cfg.partyGuardBlacklist : cfg.partyGuardWhitelist;
        remove.remove(key);
        add.add(key);
        ConstellationClient.saveConfig();
        local(player + " added to " + (whitelist ? "whitelist" : "blacklist") + '.', 0xFF55FFFF);
        return 1;
    }

    public static int removeList(String player) {
        ensureSets();
        String key = player.toLowerCase(Locale.ROOT);
        boolean changed = cfg.partyGuardWhitelist.remove(key) | cfg.partyGuardBlacklist.remove(key);
        if (changed) ConstellationClient.saveConfig();
        local(changed ? player + " removed from party guard lists." : player + " was not listed.", 0xFF55FFFF);
        return changed ? 1 : 0;
    }

    public static int list() {
        ensureSets();
        local("Whitelist: " + (cfg.partyGuardWhitelist.isEmpty() ? "none" : String.join(", ", cfg.partyGuardWhitelist)), 0xFF55FFFF);
        local("Blacklist: " + (cfg.partyGuardBlacklist.isEmpty() ? "none" : String.join(", ", cfg.partyGuardBlacklist)), 0xFF55FFFF);
        return 1;
    }

    public static int open() {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new PartyGuardScreen(null)));
        return 1;
    }

    public static int check(String player) {
        String key = player.toLowerCase(Locale.ROOT);
        int generation = generations.merge(key, 1, Integer::sum);
        evaluate(player, generation);
        return 1;
    }

    private static boolean current(String player, int generation) {
        return active() && generations.getOrDefault(player.toLowerCase(Locale.ROOT), -1) == generation;
    }

    private static void invalidate(String player) {
        generations.merge(player.toLowerCase(Locale.ROOT), 1, Integer::sum);
        pending.remove(player.toLowerCase(Locale.ROOT));
    }

    private static void reset() {
        pending.clear();
        generations.clear();
        detectedFloor = "";
        detectedMaster = false;
        leaderKnown = false;
    }

    private static void manualKick(String player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§8[§cKick " + player + "§8]")
            .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/party kick " + player))));
    }

    private static void send(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) mc.player.connection.sendCommand(command);
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.partyGuard && ConstellationClient.loc().onHypixel();
    }

    private static Floor floor() {
        String configured = cfg.partyGuardFloor == null ? "AUTO" : cfg.partyGuardFloor.trim().toUpperCase(Locale.ROOT);
        String value = configured.equals("AUTO") ? (detectedFloor.isEmpty() ? "" : (detectedMaster ? "M" : "F") + detectedFloor) : configured;
        if (!value.matches("[FM][1-7]")) return null;
        return new Floor(value.charAt(0) == 'M', value.charAt(1) - '0');
    }

    private static String[] lore(Slot slot) {
        ItemLore lore = slot.getItem().get(DataComponents.LORE);
        if (lore == null) return new String[0];
        return lore.lines().stream().map(Component::getString).toArray(String[]::new);
    }

    private static int seconds(String value) {
        if (value == null || value.equals("-")) return -1;
        try {
            String[] parts = value.split(":");
            if (parts.length == 2) return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1].replaceAll("\\D.*", ""));
            return Integer.parseInt(value) / (value.length() > 4 ? 1000 : 1);
        } catch (Exception ignored) { return -1; }
    }

    private static String profileSummary(DungeonProfileApi.Profile profile) {
        return profile == null ? "" : " (C" + round(profile.cata()) + ", " + compact(profile.secrets()) + " secrets, " + profile.magicalPower() + " MP)";
    }

    private static String compact(int value) { return value >= 1000 ? String.format(Locale.ROOT, "%.1fk", value / 1000.0) : Integer.toString(value); }
    private static String round(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String time(int seconds) { return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60); }
    private static int roman(String value) { return switch (value) { case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5; case "VI" -> 6; case "VII" -> 7; default -> 0; }; }
    private static void ensureSets() {
        if (cfg == null) return;
        if (cfg.partyGuardWhitelist == null) cfg.partyGuardWhitelist = new java.util.HashSet<>();
        if (cfg.partyGuardBlacklist == null) cfg.partyGuardBlacklist = new java.util.HashSet<>();
    }
    private static void local(String text, int colour) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§bParty Guard §8> §f" + text)
            .withStyle(style -> style.withColor(colour & 0xFFFFFF)));
    }
    private record Floor(boolean master, int number) { String name() { return (master ? "M" : "F") + number; } }
}
