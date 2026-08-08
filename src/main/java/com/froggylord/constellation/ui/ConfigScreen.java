package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.*;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.*;

public class ConfigScreen extends Screen {

    static class Module {
        // ported from Athen (BSD-3-Clause): src/main/kotlin/xyz/aerii/athen/config/ui/elements/TextParagraphElement.kt
        // ported from Athen (BSD-3-Clause): src/main/kotlin/xyz/aerii/athen/config/ui/elements/SwitchElement.kt
        record SubOpt(String label, BooleanSupplier get, Consumer<Boolean> set, boolean editable) {}
        final String name, desc, cat;
        final BooleanSupplier get; final Consumer<Boolean> set;
        final List<SubOpt> subs = new ArrayList<>();
        com.froggylord.constellation.config.BaseConfigGroup backing;
        float knob = 0, hover = 0;
        Module(String n, String d, String c, BooleanSupplier g, Consumer<Boolean> s) {
            name = n; desc = d; cat = c; get = g; set = s; knob = g.getAsBoolean() ? 1 : 0;
        }
        
        Module b(String l, BooleanSupplier g, Consumer<Boolean> s) {
            subs.add(new SubOpt(l, g, s, true));
            return this;
        }
        
        Module sub(String l, boolean def) {
            subs.add(new SubOpt(l, () -> def, v -> {}, false));
            return this;
        }
    }

    private final String constellationId;
    private final Screen parent;
    private final List<Module> modules = new ArrayList<>();
    private com.froggylord.constellation.config.BaseConfigGroup activeConfig;

    private String[] cats = {};
    private int selectedCat = 0;
    private Module openModule = null;
    private float modalAnim = 0;

    private float scrollF = 0;
    private int scrollTarget = 0, maxScroll = 0;
    private int lastMx, lastMy;
    private long lastNanos = 0;
    private static final int TB = 34;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 4;

    public ConfigScreen(String constellationId, Screen parent) {
        super(Component.literal("Constellation — " + constellationId));
        this.constellationId = constellationId;
        this.parent = parent;
        buildModules();
    }

    
    
    private static String autoLabel(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) sb.append(' ');
            sb.append(i == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return sb.toString().trim();
    }

    private int panelW(int w) { return Math.min(w - 20, Math.max(Math.min(460, w - 20), w * 68 / 100)); }
    private int panelH(int h) { return Math.min(h - 20, Math.max(Math.min(300, h - 20), h * 78 / 100)); }
    private int sideW(int w) { return Math.max(70, Math.min(100, panelW(w) * 18 / 100)); }
    private int gridTop() { return TB + 24; }
    private int gridX(int w) { return sideW(w) + 12; }
    private int gridRight(int w) { return panelW(w) - 10; }
    private int cols(int w) { int a = gridRight(w) - gridX(w) + 4; return Math.max(1, Math.min(4, a / (210 + 4))); }
    private int cardW(int w) { int c = cols(w); return (gridRight(w) - gridX(w) - (c - 1) * CARD_GAP) / c; }

    private void buildModules() {
        modules.clear();
        var cfg = ConstellationClient.cfg();

        switch (constellationId) {
            case "phoenix" -> {
                cats = new String[]{"Visual", "Gameplay"};
                PhoenixConfig c = cfg.phoenix;
                var visFields = Set.of("fullbright","hideLightning","hideFallingBlocks","hideFireOverlay","hideUnderwaterBlur","disableVignette","disableFog","hideStatusEffects");
                for (var field : PhoenixConfig.class.getFields()) {
                    if (field.getName().equals("enabled") || field.getName().equals("version")) continue;
                    if (field.getType() != boolean.class) continue;
                    String cat = visFields.contains(field.getName()) ? "Visual" : "Gameplay";
                    try {
                        boolean val = field.getBoolean(c);
                        String label = field.getName().replaceAll("([A-Z])", " $1").trim();
                        if (!label.isEmpty()) label = label.substring(0,1).toUpperCase() + label.substring(1);
                        modules.add(new Module(field.getName(), label, cat,
                            () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                            v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} })
                            .b("Master", () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                                v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} })
                            .sub("Also in dungeons", false));
                    } catch (Exception e) {}
                }
            }
            case "cassiopeia" -> {
                cats = new String[]{"Filters", "Chat", "Commands", "Party"};
                CassiopeiaConfig c = cfg.cassiopeia;
                for (var field : CassiopeiaConfig.class.getFields()) {
                    String n = field.getName();
                    if (!n.startsWith("clean")) continue;
                    if (field.getType() != boolean.class) continue;
                    try {
                        boolean val = field.getBoolean(c);
                        String label = n.substring(5).replaceAll("([A-Z])", " $1").trim();
                        if (!label.isEmpty()) label = label.substring(0,1).toUpperCase() + label.substring(1);
                        modules.add(new Module(n, label, "Filters",
                            () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                            v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} })
                            .b("In dungeons", () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                                v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} })
                            .sub("On private island", false));
                    } catch (Exception e) {}
                }
                modules.add(new Module("timestamps", "[HH:MM] on every line", "Chat",
                    () -> c.timestamps, v -> { c.timestamps = v; ConstellationClient.saveConfig(); })
                    .sub("Show seconds", false)
                    .sub("Colour", true));
                modules.add(new Module("clickableLinks", "URLs clickable", "Chat",
                    () -> c.clickableLinks, v -> { c.clickableLinks = v; ConstellationClient.saveConfig(); })
                    .b("Underline", () -> c.clickableLinks, v -> c.clickableLinks = v)
                    .sub("Auto-shorten", false));
                modules.add(new Module("copyOnRightClick", "Right-click to copy", "Chat",
                    () -> c.copyOnRightClick, v -> { c.copyOnRightClick = v; ConstellationClient.saveConfig(); })
                    .b("Copy timestamp", () -> c.copyOnRightClick, v -> c.copyOnRightClick = v)
                    .sub("Copy without colour", false));
                modules.add(new Module("mentionAlert", "Ping when named", "Chat",
                    () -> c.mentionAlert, v -> { c.mentionAlert = v; ConstellationClient.saveConfig(); })
                    .b("Sound", () -> c.mentionAlert, v -> c.mentionAlert = v)
                    .sub("Title", true));
                modules.add(new Module("floorShortcuts", "/f1-/f7 /m1-/m7", "Commands",
                    () -> c.floorShortcuts, v -> { c.floorShortcuts = v; ConstellationClient.saveConfig(); })
                    .b("Floors", () -> c.floorShortcuts, v -> c.floorShortcuts = v)
                    .b("Master mode", () -> c.floorShortcuts, v -> c.floorShortcuts = v));
                modules.add(new Module("warpShortcuts", "/h /i /dh /l", "Commands",
                    () -> c.warpShortcuts, v -> { c.warpShortcuts = v; ConstellationClient.saveConfig(); })
                    .b("Hub", () -> c.warpShortcuts, v -> c.warpShortcuts = v)
                    .b("Island", () -> c.warpShortcuts, v -> c.warpShortcuts = v));
                modules.add(new Module("warpShortener", "/drag → /warp drag", "Commands",
                    () -> c.warpShortener, v -> { c.warpShortener = v; ConstellationClient.saveConfig(); })
                    .b("Enabled", () -> c.warpShortener, v -> c.warpShortener = v)
                    .sub("Show in chat", false));
                modules.add(new Module("partyShortcuts", "/pi /pw /pl ...", "Party",
                    () -> c.partyShortcuts, v -> { c.partyShortcuts = v; ConstellationClient.saveConfig(); })
                    .b("Invite", () -> c.partyShortcuts, v -> c.partyShortcuts = v)
                    .b("Kick", () -> c.partyShortcuts, v -> c.partyShortcuts = v));
                modules.add(new Module("partyTriggers", "!warp !join !dt ...", "Party",
                    () -> c.partyTriggers, v -> { c.partyTriggers = v; ConstellationClient.saveConfig(); })
                    .b("Enabled", () -> c.partyTriggers, v -> c.partyTriggers = v)
                    .b("Safe mode", () -> c.triggerSafeMode, v -> { c.triggerSafeMode = v; ConstellationClient.saveConfig(); }));
            }
            case "orion" -> {
                cats = new String[]{"HUD", "Map", "Secrets", "Party", "Solvers", "Combat", "Boss", "Devices", "Timers"};
                OrionConfig c = cfg.orion;
                modules.add(new Module("scoreHud", "Live 0-300 score", "HUD",
                    () -> c.scoreHud, v -> { c.scoreHud = v; ConstellationClient.saveConfig(); })
                    .b("Milestone alerts", () -> c.scorePings, v -> { c.scorePings = v; ConstellationClient.saveConfig(); })
                    .b("Milestone title", () -> c.scorePingTitle, v -> { c.scorePingTitle = v; ConstellationClient.saveConfig(); })
                    .b("Milestone sound", () -> c.scorePingSound, v -> { c.scorePingSound = v; ConstellationClient.saveConfig(); })
                    .sub("Includes letter grade", true)
                    .sub("S at 270 and S+ at 300", true));
                modules.add(new Module("secretsHud", "Secrets found / total", "HUD",
                    () -> c.secretsHud, v -> { c.secretsHud = v; ConstellationClient.saveConfig(); })
                    .b("Per-room count", () -> c.perRoomCount, v -> { c.perRoomCount = v; ConstellationClient.saveConfig(); })
                    .sub("Displays percentage", true));
                modules.add(new Module("cryptsHud", "Crypt count", "HUD",
                    () -> c.cryptsHud, v -> { c.cryptsHud = v; ConstellationClient.saveConfig(); })
                    .sub("Low-crypt guidance is in Copilot", true));
                modules.add(new Module("deathsHud", "Death counter", "HUD",
                    () -> c.deathsHud, v -> { c.deathsHud = v; ConstellationClient.saveConfig(); })
                    .sub("Score impact appears in the score panel", true));
                modules.add(new Module("puzzlesDisplay", "Puzzle status HUD", "HUD",
                    () -> c.puzzlesDisplay, v -> { c.puzzlesDisplay = v; ConstellationClient.saveConfig(); })
                    .b("Compact counts", () -> c.puzzlesCompact, v -> { c.puzzlesCompact = v; ConstellationClient.saveConfig(); })
                    .sub("Tab-list status", true));
                modules.add(new Module("architectNotifier", "Remind on puzzle failure", "Solvers",
                    () -> c.architectNotifier, v -> { c.architectNotifier = v; ConstellationClient.saveConfig(); })
                    .sub("Clickable get/use actions", true)
                    .sub("Unbound use-draft key in Controls", true)
                    .sub("Only your failures during clear", true));
                modules.add(new Module("smartRefill", "Keybind refills the lowest configured stack", "Party",
                    () -> c.smartRefill, v -> { c.smartRefill = v; ConstellationClient.saveConfig(); })
                    .b("One item per press", () -> c.smartRefillOneAtATime, v -> { c.smartRefillOneAtATime = v; ConstellationClient.saveConfig(); })
                    .sub("Open card to edit items", true));
                modules.add(new Module("dungeonQueueCooldown", "Dungeon creation cooldown and queue position", "Party",
                    () -> c.dungeonQueueCooldown, v -> { c.dungeonQueueCooldown = v; ConstellationClient.saveConfig(); })
                    .b("Block early queue commands", () -> c.dungeonQueueBlockCommands, v -> { c.dungeonQueueBlockCommands = v; ConstellationClient.saveConfig(); })
                    .b("Transfer recovery controls", () -> c.dungeonQueueTransferRecovery, v -> { c.dungeonQueueTransferRecovery = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("springBootsHelper", "Charge height and obstruction preview", "HUD",
                    () -> c.springBootsHelper, v -> { c.springBootsHelper = v; ConstellationClient.saveConfig(); })
                    .b("HUD", () -> c.springBootsHud, v -> { c.springBootsHud = v; ConstellationClient.saveConfig(); })
                    .b("Target box", () -> c.springBootsBox, v -> { c.springBootsBox = v; ConstellationClient.saveConfig(); })
                    .b("Guide line", () -> c.springBootsLine, v -> { c.springBootsLine = v; ConstellationClient.saveConfig(); })
                    .b("Through walls", () -> c.springBootsThroughWalls, v -> { c.springBootsThroughWalls = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("timerHud", "Run timer", "HUD",
                    () -> c.timerHud, v -> { c.timerHud = v; ConstellationClient.saveConfig(); })
                    .b("Show splits", () -> c.splitsHud, v -> { c.splitsHud = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("milestoneHud", "Current class milestone", "HUD",
                    () -> c.milestoneHud, v -> { c.milestoneHud = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("abilityTracker", "Defensive ability tracker", "HUD",
                    () -> c.abilityTracker, v -> { c.abilityTracker = v; ConstellationClient.saveConfig(); })
                    .b("Ready sound", () -> c.abilityReadyDing, v -> { c.abilityReadyDing = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("dungeonCopilot", "Live score and clear guidance", "HUD",
                    () -> c.dungeonCopilot, v -> { c.dungeonCopilot = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("dungeonStats", "Run history, records and terminal splits", "Timers",
                    () -> c.saveDungeonRunHistory, v -> { c.saveDungeonRunHistory = v; ConstellationClient.saveConfig(); })
                    .sub("Open card for records", true));
                modules.add(new Module("roomNameHud", "Current room name", "HUD",
                    () -> c.roomNameHud, v -> { c.roomNameHud = v; ConstellationClient.saveConfig(); })
                    .sub("Cleared indicator", true)
                    .b("Show secrets in room", () -> c.perRoomCount, v -> { c.perRoomCount = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("mimicIndicator", "Mimic killed marker", "HUD",
                    () -> c.mimicIndicator, v -> { c.mimicIndicator = v; ConstellationClient.saveConfig(); })
                    .b("Party message", () -> c.mimicPartyPing, v -> { c.mimicPartyPing = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("dungeonPotionsHud", "Active dungeon potion effects", "HUD",
                    () -> c.dungeonPotionsHud, v -> { c.dungeonPotionsHud = v; ConstellationClient.saveConfig(); })
                    .sub("Shows two shortest active effects", true));
                modules.add(new Module("dungeonMap", "Hypixel map overlay", "Map",
                    () -> c.dungeonMap, v -> { c.dungeonMap = v; ConstellationClient.saveConfig(); })
                    .sub("Room names", true)
                    .sub("Player heads", true)
                    .sub("Auto-hide in boss", true));
                modules.add(new Module("secretWaypoints", "In-world secret boxes", "Secrets",
                    () -> c.secretWaypoints, v -> { c.secretWaypoints = v; ConstellationClient.saveConfig(); })
                    .b("Beams", () -> c.secretBeams, v -> { c.secretBeams = v; ConstellationClient.saveConfig(); })
                    .b("Progressive reveal", () -> c.progressiveReveal, v -> { c.progressiveReveal = v; ConstellationClient.saveConfig(); })
                    .b("One at a time", () -> c.oneSecretAtATime, v -> { c.oneSecretAtATime = v; ConstellationClient.saveConfig(); })
                    .b("Secrets done alert", () -> c.secretsDoneAlert, v -> { c.secretsDoneAlert = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("secretCompassHelper", "Secret Tracker destination locator", "Secrets",
                    () -> c.secretCompassHelper, v -> { c.secretCompassHelper = v; ConstellationClient.saveConfig(); })
                    .b("Tracer", () -> c.secretCompassTracer, v -> { c.secretCompassTracer = v; ConstellationClient.saveConfig(); })
                    .b("Beam", () -> c.secretCompassBeam, v -> { c.secretCompassBeam = v; ConstellationClient.saveConfig(); })
                    .b("Target box", () -> c.secretCompassBox, v -> { c.secretCompassBox = v; ConstellationClient.saveConfig(); })
                    .b("Through walls", () -> c.secretCompassThroughWalls, v -> { c.secretCompassThroughWalls = v; ConstellationClient.saveConfig(); })
                    .b("Duplicate-use guard", () -> c.secretCompassDuplicateGuard, v -> { c.secretCompassDuplicateGuard = v; ConstellationClient.saveConfig(); })
                    .b("Acquisition HUD", () -> c.secretCompassHud, v -> { c.secretCompassHud = v; ConstellationClient.saveConfig(); })
                    .sub("Color via /secretcompass", true));
                modules.add(new Module("routes", "Secret routes", "Secrets",
                    () -> c.routes, v -> { c.routes = v; ConstellationClient.saveConfig(); })
                    .b("Whole remaining route", () -> c.routeWholeRoute, v -> { c.routeWholeRoute = v; ConstellationClient.saveConfig(); })
                    .b("Prefer pearl-clips", () -> c.pearlRoutes, v -> { c.pearlRoutes = v; ConstellationClient.saveConfig(); })
                    .b("Exact-target auto advance", () -> c.routeAutoAdvance, v -> { c.routeAutoAdvance = v; ConstellationClient.saveConfig(); })
                    .b("Recording HUD", () -> c.routeRecordingHud, v -> { c.routeRecordingHud = v; ConstellationClient.saveConfig(); })
                    .b("Online updates next launch", () -> c.secretRoutesOnlineDb, v -> { c.secretRoutesOnlineDb = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("routeLines", "Walking path lines", "Secrets",
                    () -> c.routeLines, v -> { c.routeLines = v; ConstellationClient.saveConfig(); })
                    .b("Through walls", () -> c.routeThroughWalls, v -> { c.routeThroughWalls = v; ConstellationClient.saveConfig(); })
                    .b("Player to current secret", () -> c.routePlayerToSecret, v -> { c.routePlayerToSecret = v; ConstellationClient.saveConfig(); })
                    .b("Player to first etherwarp", () -> c.routePlayerToEtherwarp, v -> { c.routePlayerToEtherwarp = v; ConstellationClient.saveConfig(); })
                    .sub("Steps/colors: /cn route", true));
                modules.add(new Module("routeMarkers", "Route action and secret markers", "Secrets",
                    () -> c.routeMarkers, v -> { c.routeMarkers = v; ConstellationClient.saveConfig(); })
                    .b("Secrets", () -> c.routeRenderSecrets, v -> { c.routeRenderSecrets = v; ConstellationClient.saveConfig(); })
                    .b("Etherwarps", () -> c.routeRenderEtherwarps, v -> { c.routeRenderEtherwarps = v; ConstellationClient.saveConfig(); })
                    .b("Mines", () -> c.routeRenderMines, v -> { c.routeRenderMines = v; ConstellationClient.saveConfig(); })
                    .b("Interacts", () -> c.routeRenderInteracts, v -> { c.routeRenderInteracts = v; ConstellationClient.saveConfig(); })
                    .b("Superbooms", () -> c.routeRenderSuperboom, v -> { c.routeRenderSuperboom = v; ConstellationClient.saveConfig(); })
                    .b("Pearls", () -> c.routeRenderPearls, v -> { c.routeRenderPearls = v; ConstellationClient.saveConfig(); })
                    .b("Labels", () -> c.routeLabels, v -> { c.routeLabels = v; ConstellationClient.saveConfig(); })
                    .b("Filled markers", () -> c.routeFilledMarkers, v -> { c.routeFilledMarkers = v; ConstellationClient.saveConfig(); })
                    .b("Dim future steps", () -> c.routeDistinguishFuture, v -> { c.routeDistinguishFuture = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("echoOnCollect", "Chime on secret", "Secrets",
                    () -> c.echoOnCollect, v -> { c.echoOnCollect = v; ConstellationClient.saveConfig(); })
                    .sub("Plays a pickup chime", true)
                    .b("Custom waypoints", () -> c.customWaypoints, v -> { c.customWaypoints = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("quickCloseDungeonChests", "Close dungeon chests on a key", "Secrets",
                    () -> c.quickCloseDungeonChests, v -> { c.quickCloseDungeonChests = v; ConstellationClient.saveConfig(); })
                    .b("Any keyboard key", () -> c.quickCloseAnyKey, v -> { c.quickCloseAnyKey = v; ConstellationClient.saveConfig(); })
                    .b("Movement keys", () -> c.quickCloseMovementKeys, v -> { c.quickCloseMovementKeys = v; ConstellationClient.saveConfig(); })
                    .b("Crouch key", () -> c.quickCloseCrouchKey, v -> { c.quickCloseCrouchKey = v; ConstellationClient.saveConfig(); })
                    .b("Reward chests too", () -> c.quickCloseRewardChests, v -> { c.quickCloseRewardChests = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("salvageHelper", "Low-value dungeon salvage", "Secrets",
                    () -> c.salvageHelper, v -> { c.salvageHelper = v; ConstellationClient.saveConfig(); })
                    .b("Exclude protected items", () -> c.salvageExcludeProtected, v -> { c.salvageExcludeProtected = v; ConstellationClient.saveConfig(); })
                    .b("Exclude upgraded items", () -> c.salvageExcludeModified, v -> { c.salvageExcludeModified = v; ConstellationClient.saveConfig(); })
                    .b("Mark unknown prices", () -> c.salvageMarkUnknown, v -> { c.salvageMarkUnknown = v; ConstellationClient.saveConfig(); })
                    .sub("Limit: " + String.format(java.util.Locale.US, "%,d", c.salvageMaxValue) + " (/dungeonloot limit)", true));
                modules.add(new Module("sellableDungeonLoot", "Dungeon junk at Ophelia", "Secrets",
                    () -> c.sellableDungeonLoot, v -> { c.sellableDungeonLoot = v; ConstellationClient.saveConfig(); })
                    .b("Include hotbar", () -> c.sellableIncludeHotbar, v -> { c.sellableIncludeHotbar = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("partyFinderGui", "Fancy party finder", "Party",
                    () -> c.partyFinderGui, v -> { c.partyFinderGui = v; ConstellationClient.saveConfig(); })
                    .b("Player stats", () -> c.partyFinderStats, v -> { c.partyFinderStats = v; ConstellationClient.saveConfig(); })
                    .sub("Joinable, dupe, blocked highlights", true)
                    .sub("Party size on listings", true));
                modules.add(new Module("partyGuard", "Party Finder rules and lists", "Party",
                    () -> c.partyGuard, v -> { c.partyGuard = v; ConstellationClient.saveConfig(); })
                    .b("Dry run", () -> c.partyGuardDryRun, v -> { c.partyGuardDryRun = v; ConstellationClient.saveConfig(); })
                    .b("Send reason", () -> c.partyGuardSendReason, v -> { c.partyGuardSendReason = v; ConstellationClient.saveConfig(); })
                    .sub("Open card or /partyguard", true));
                modules.add(new Module("partyMessages", "Useful dungeon party messages", "Party",
                    () -> c.partyMessages, v -> { c.partyMessages = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("streamerMode", "Suppress outgoing Orion messages", "Party",
                    () -> c.streamerMode, v -> { c.streamerMode = v; ConstellationClient.saveConfig(); })
                    .sub("Also suppresses mimic and Prince announcements", true));
                modules.add(new Module("princePartyPing", "Announce F4/M4 Prince kill", "Party",
                    () -> c.princePartyPing, v -> { c.princePartyPing = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("messageEditor", "Edit, search and sort every message", "Party",
                    () -> true, v -> {}));
                modules.add(new Module("autoRequeue", "Automatically requeue after run", "Party",
                    () -> c.autoRequeue, v -> { c.autoRequeue = v; ConstellationClient.saveConfig(); })
                    .b("Cancel if party changes", () -> c.requeueCancelOnPartyChange, v -> { c.requeueCancelOnPartyChange = v; ConstellationClient.saveConfig(); })
                    .b("Respect dt / r", () -> c.requeueDowntime, v -> { c.requeueDowntime = v; ConstellationClient.saveConfig(); })
                    .b("Feedback and controls", () -> c.requeueFeedback, v -> { c.requeueFeedback = v; ConstellationClient.saveConfig(); })
                    .sub("Delay: /requeue delay 0-30", true));
                
                modules.add(new Module("terminalSolvers", "F7 phase-3 terminals", "Solvers",
                    () -> c.terminalSolvers, v -> { c.terminalSolvers = v; ConstellationClient.saveConfig(); })
                    .b("Show numbers", () -> c.terminalNumbers, v -> { c.terminalNumbers = v; ConstellationClient.saveConfig(); })
                    .b("Block wrong clicks", () -> c.blockWrongTerminalClicks, v -> { c.blockWrongTerminalClicks = v; ConstellationClient.saveConfig(); })
                    .b("Middle-click terminals", () -> c.terminalMiddleClick, v -> { c.terminalMiddleClick = v; ConstellationClient.saveConfig(); })
                    .b("Drop key clicks", () -> c.terminalDropKeyClick, v -> { c.terminalDropKeyClick = v; ConstellationClient.saveConfig(); })
                    .b("Hide tooltips", () -> c.terminalDisableTooltips, v -> { c.terminalDisableTooltips = v; ConstellationClient.saveConfig(); })
                    .b("Hide labels", () -> c.terminalHideLabels, v -> { c.terminalHideLabels = v; ConstellationClient.saveConfig(); })
                    .b("Slot background", () -> c.terminalSlotBackground, v -> { c.terminalSlotBackground = v; ConstellationClient.saveConfig(); })
                    .b("Hide completed", () -> c.terminalHideDone, v -> { c.terminalHideDone = v; ConstellationClient.saveConfig(); })
                    .b("Hide solution items", () -> c.terminalHideItems, v -> { c.terminalHideItems = v; ConstellationClient.saveConfig(); })
                    .b("Block bad Rubix direction", () -> c.terminalRubixBlockBadDirection, v -> { c.terminalRubixBlockBadDirection = v; ConstellationClient.saveConfig(); })
                    .b("Click sounds", () -> c.terminalClickSounds, v -> { c.terminalClickSounds = v; ConstellationClient.saveConfig(); })
                    .b("Live terminal progress", () -> c.terminalDisplay, v -> { c.terminalDisplay = v; ConstellationClient.saveConfig(); })
                    .b("Simple progress display", () -> c.terminalDisplaySimple, v -> { c.terminalDisplaySimple = v; ConstellationClient.saveConfig(); })
                    .b("Show section number", () -> c.terminalDisplayShowSection, v -> { c.terminalDisplayShowSection = v; ConstellationClient.saveConfig(); })
                    .b("Terminal breakdown", () -> c.terminalBreakdown, v -> { c.terminalBreakdown = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("blazeSolver", "F3/M3 blaze puzzle", "Solvers",
                    () -> c.blazeSolver, v -> { c.blazeSolver = v; ConstellationClient.saveConfig(); })
                    .sub("Show labels", true));
                modules.add(new Module("simonSaysSolver", "Simon Says — highlight button", "Solvers",
                    () -> c.simonSaysSolver, v -> { c.simonSaysSolver = v; ConstellationClient.saveConfig(); })
                    .sub("Party messages configured separately", true));
                modules.add(new Module("threeWeirdosSolver", "Three Weirdos — correct chest", "Solvers",
                    () -> c.threeWeirdosSolver, v -> { c.threeWeirdosSolver = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("triviaSolver", "Trivia — highlight answer", "Solvers",
                    () -> c.triviaSolver, v -> { c.triviaSolver = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("creeperBeamsSolver", "Creeper Beams — lantern links", "Solvers",
                    () -> c.creeperBeamsSolver, v -> { c.creeperBeamsSolver = v; ConstellationClient.saveConfig(); })
                    .sub("Beam colour", true));
                modules.add(new Module("ticTacToeSolver", "Tic Tac Toe — show best move", "Solvers",
                    () -> c.ticTacToeSolver, v -> { c.ticTacToeSolver = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("waterboardSolver", "Water Board — next lever", "Solvers",
                    () -> c.waterboardSolver, v -> { c.waterboardSolver = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("iceFillSolver", "Ice Fill — walking path", "Solvers",
                    () -> c.iceFillSolver, v -> { c.iceFillSolver = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("silverfishSolver", "Silverfish — maze path", "Solvers",
                    () -> c.silverfishSolver, v -> { c.silverfishSolver = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("teleportMazeSolver", "Teleport Maze — safe pads", "Solvers",
                    () -> c.teleportMazeSolver, v -> { c.teleportMazeSolver = v; ConstellationClient.saveConfig(); }));

                modules.add(new Module("lightsOnSolver", "Lights On device", "Devices",
                    () -> c.lightsOnSolver, v -> { c.lightsOnSolver = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("arrowAlignSolver", "Arrow Align device", "Devices",
                    () -> c.arrowAlignSolver, v -> { c.arrowAlignSolver = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("targetPracticeSolver", "Target Practice device", "Devices",
                    () -> c.targetPracticeSolver, v -> { c.targetPracticeSolver = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("melodyTerminalHelper", "Melody terminal helper", "Devices",
                    () -> c.melodyTerminalHelper, v -> { c.melodyTerminalHelper = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("terminalSimulator", "Terminal simulator command", "Devices",
                    () -> c.terminalSimulator, v -> { c.terminalSimulator = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("goldorInactiveTerminals", "Goldor inactive objectives", "Devices",
                    () -> c.goldorInactiveTerminals, v -> { c.goldorInactiveTerminals = v; ConstellationClient.saveConfig(); })
                    .sub("Armor-stand fallback; requires objective waypoints with fixed positions off", true));
                modules.add(new Module("goldorWaypoints", "Goldor objective waypoints", "Devices",
                    () -> c.goldorWaypoints, v -> { c.goldorWaypoints = v; ConstellationClient.saveConfig(); })
                    .b("Use fixed positions", () -> c.goldorWaypointFixedPositions, v -> { c.goldorWaypointFixedPositions = v; ConstellationClient.saveConfig(); })
                    .b("Terminals", () -> c.goldorWaypointTerminals, v -> { c.goldorWaypointTerminals = v; ConstellationClient.saveConfig(); })
                    .b("Devices", () -> c.goldorWaypointDevices, v -> { c.goldorWaypointDevices = v; ConstellationClient.saveConfig(); })
                    .b("Levers", () -> c.goldorWaypointLevers, v -> { c.goldorWaypointLevers = v; ConstellationClient.saveConfig(); })
                    .b("Hide completed", () -> c.goldorWaypointHideCompleted, v -> { c.goldorWaypointHideCompleted = v; ConstellationClient.saveConfig(); })
                    .b("Only assigned class", () -> c.goldorWaypointClassFilter, v -> { c.goldorWaypointClassFilter = v; ConstellationClient.saveConfig(); })
                    .b("Show assigned class", () -> c.goldorWaypointShowClass, v -> { c.goldorWaypointShowClass = v; ConstellationClient.saveConfig(); })
                    .b("Labels", () -> c.goldorWaypointLabels, v -> { c.goldorWaypointLabels = v; ConstellationClient.saveConfig(); })
                    .b("Beams", () -> c.goldorWaypointBeam, v -> { c.goldorWaypointBeam = v; ConstellationClient.saveConfig(); })
                    .b("Filled boxes", () -> c.goldorWaypointFilled, v -> { c.goldorWaypointFilled = v; ConstellationClient.saveConfig(); })
                    .b("Outlines", () -> c.goldorWaypointOutline, v -> { c.goldorWaypointOutline = v; ConstellationClient.saveConfig(); })
                    .b("Through walls", () -> c.goldorWaypointThroughWalls, v -> { c.goldorWaypointThroughWalls = v; ConstellationClient.saveConfig(); })
                    .sub("Assignments and colors: /goldorwaypoints", true));
                modules.add(new Module("terminalHideCompletion", "Filter Goldor completion titles", "Devices",
                    () -> c.terminalHideCompletion, v -> { c.terminalHideCompletion = v; ConstellationClient.saveConfig(); })
                    .b("Keep your completions", () -> c.terminalCompletionOnlyOwn, v -> { c.terminalCompletionOnlyOwn = v; ConstellationClient.saveConfig(); })
                    .b("Filter title packets", () -> c.terminalCompletionFilterTitles, v -> { c.terminalCompletionFilterTitles = v; ConstellationClient.saveConfig(); })
                    .b("Filter subtitle packets", () -> c.terminalCompletionFilterSubtitles, v -> { c.terminalCompletionFilterSubtitles = v; ConstellationClient.saveConfig(); }));

                modules.add(new Module("bloodTimer", "Blood room timer", "Timers",
                    () -> c.bloodTimer, v -> { c.bloodTimer = v; ConstellationClient.saveConfig(); })
                    .b("Blood camp helper", () -> c.bloodCampHelper, v -> { c.bloodCampHelper = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("watcherBossBar", "Watcher wave boss-bar progress", "Timers",
                    () -> c.watcherBossBar, v -> { c.watcherBossBar = v; ConstellationClient.saveConfig(); })
                    .b("Show progress", () -> c.watcherBossBarShowProgress, v -> { c.watcherBossBarShowProgress = v; ConstellationClient.saveConfig(); })
                    .b("Hide outside Blood", () -> c.watcherBossBarHideNotBlood, v -> { c.watcherBossBarHideNotBlood = v; ConstellationClient.saveConfig(); })
                    .b("Show percentage", () -> c.watcherBossBarShowPercent, v -> { c.watcherBossBarShowPercent = v; if (v) c.watcherBossBarShowRemaining = false; ConstellationClient.saveConfig(); })
                    .b("Show remaining", () -> c.watcherBossBarShowRemaining, v -> { c.watcherBossBarShowRemaining = v; if (v) c.watcherBossBarShowPercent = false; ConstellationClient.saveConfig(); })
                    .sub("Colors: /watcherbar", true));
                modules.add(new Module("fireFreezeTimer", "Professor fire-freeze timer", "Timers",
                    () -> c.fireFreezeTimer, v -> { c.fireFreezeTimer = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("spiritBowTimer", "Thorn spirit bow timer", "Timers",
                    () -> c.spiritBowTimer, v -> { c.spiritBowTimer = v; ConstellationClient.saveConfig(); })
                    .b("Highlight bow", () -> c.spiritBowHighlight, v -> { c.spiritBowHighlight = v; ConstellationClient.saveConfig(); })
                    .b("Bear timer", () -> c.spiritBearTimer, v -> { c.spiritBearTimer = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("lividInvulnerableTimer", "Livid spawn protection timer", "Timers",
                    () -> c.lividInvulnerableTimer, v -> { c.lividInvulnerableTimer = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("saVanishTimer", "Shadow Assassin vanish timer", "Timers",
                    () -> c.saVanishTimer, v -> { c.saVanishTimer = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("terracottaTimer", "F6/M6 Terracotta respawn timers", "Timers",
                    () -> c.terracottaTimer, v -> { c.terracottaTimer = v; ConstellationClient.saveConfig(); })
                    .b("World respawn labels", () -> c.terracottaRespawnLabels, v -> { c.terracottaRespawnLabels = v; ConstellationClient.saveConfig(); })
                    .b("Elapsed phase HUD", () -> c.terracottaPhaseHud, v -> { c.terracottaPhaseHud = v; ConstellationClient.saveConfig(); })
                    .b("Labels through walls", () -> c.terracottaThroughWalls, v -> { c.terracottaThroughWalls = v; ConstellationClient.saveConfig(); })
                    .b("Ready sound", () -> c.terracottaReadySound, v -> { c.terracottaReadySound = v; ConstellationClient.saveConfig(); })
                    .sub("F6 15.0s / M6 12.0s; red, yellow, green", true)
                    .sub("Precision and custom colors: /terracotta", true));

                modules.add(new Module("guardianHealth", "Professor guardian health", "Boss",
                    () -> c.guardianHealth, v -> { c.guardianHealth = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("healerPlatformHighlight", "Goldor healer platform", "Boss",
                    () -> c.healerPlatformHighlight, v -> { c.healerPlatformHighlight = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("m7DragonMarkers", "M7 dragon spawn markers", "Boss",
                    () -> c.m7DragonMarkers, v -> { c.m7DragonMarkers = v; ConstellationClient.saveConfig(); })
                    .b("Arrow stack aimer", () -> c.m7DragonStackAimer, v -> { c.m7DragonStackAimer = v; ConstellationClient.saveConfig(); })
                    .b("Ping compensation", () -> c.m7DragonStackPing, v -> { c.m7DragonStackPing = v; ConstellationClient.saveConfig(); })
                    .b("Shot countdown HUD", () -> c.m7DragonStackHud, v -> { c.m7DragonStackHud = v; ConstellationClient.saveConfig(); })
                    .b("Arrow hit counter", () -> c.m7DragonHitCounter, v -> { c.m7DragonHitCounter = v; ConstellationClient.saveConfig(); })
                    .b("Arrow hit HUD", () -> c.m7DragonHitHud, v -> { c.m7DragonHitHud = v; ConstellationClient.saveConfig(); })
                    .b("Local kill report", () -> c.m7DragonHitReport, v -> { c.m7DragonHitReport = v; ConstellationClient.saveConfig(); })
                    .b("Party kill report", () -> c.m7DragonHitPartyMessage, v -> { c.m7DragonHitPartyMessage = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("m7RelicHighlight", "M7 relic highlighting", "Boss",
                    () -> c.m7RelicHighlight, v -> { c.m7RelicHighlight = v; ConstellationClient.saveConfig(); })
                    .b("Relic timer", () -> c.m7RelicTimer, v -> { c.m7RelicTimer = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("witherHighlight", "F7/M7 Wither boss highlight", "Boss",
                    () -> c.witherHighlight, v -> { c.witherHighlight = v; ConstellationClient.saveConfig(); })
                    .b("Outline", () -> c.witherHighlightOutline, v -> { c.witherHighlightOutline = v; ConstellationClient.saveConfig(); })
                    .b("Filled box", () -> c.witherHighlightFill, v -> { c.witherHighlightFill = v; ConstellationClient.saveConfig(); })
                    .b("Through walls", () -> c.witherHighlightThroughWalls, v -> { c.witherHighlightThroughWalls = v; ConstellationClient.saveConfig(); })
                    .b("Boss and health label", () -> c.witherHighlightLabel, v -> { c.witherHighlightLabel = v; ConstellationClient.saveConfig(); })
                    .b("Beam", () -> c.witherHighlightBeam, v -> { c.witherHighlightBeam = v; ConstellationClient.saveConfig(); })
                    .b("Hide invisible", () -> c.witherHighlightHideInvisible, v -> { c.witherHighlightHideInvisible = v; ConstellationClient.saveConfig(); })
                    .b("Exclude armor summon", () -> c.witherHighlightExcludeArmorSummon, v -> { c.witherHighlightExcludeArmorSummon = v; ConstellationClient.saveConfig(); })
                    .b("Maxor", () -> c.witherHighlightMaxor, v -> { c.witherHighlightMaxor = v; ConstellationClient.saveConfig(); })
                    .b("Storm", () -> c.witherHighlightStorm, v -> { c.witherHighlightStorm = v; ConstellationClient.saveConfig(); })
                    .b("Goldor", () -> c.witherHighlightGoldor, v -> { c.witherHighlightGoldor = v; ConstellationClient.saveConfig(); })
                    .b("Necron", () -> c.witherHighlightNecron, v -> { c.witherHighlightNecron = v; ConstellationClient.saveConfig(); })
                    .b("Wither King phase", () -> c.witherHighlightWitherKing, v -> { c.witherHighlightWitherKing = v; ConstellationClient.saveConfig(); })
                    .sub("Colors, width and range: /witherhighlight", true));
                modules.add(new Module("chestProfitCalc", "Dungeon chest profit", "Boss",
                    () -> c.chestProfitCalc, v -> { c.chestProfitCalc = v; ConstellationClient.saveConfig(); })
                    .b("Include essence", () -> c.chestProfitUseEssence, v -> { c.chestProfitUseEssence = v; ConstellationClient.saveConfig(); })
                    .b("Compact item list", () -> c.chestProfitCompact, v -> { c.chestProfitCompact = v; ConstellationClient.saveConfig(); })
                    .b("Subtract chest keys", () -> c.chestProfitSubtractKey, v -> { c.chestProfitSubtractKey = v; ConstellationClient.saveConfig(); })
                    .b("Show unknown prices", () -> c.chestProfitShowUnknown, v -> { c.chestProfitShowUnknown = v; ConstellationClient.saveConfig(); })
                    .b("Persistent run HUD", () -> c.chestProfitHud, v -> { c.chestProfitHud = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("dungeonBreakerDisplay", "Dungeon breaker charges", "Boss",
                    () -> c.dungeonBreakerDisplay, v -> { c.dungeonBreakerDisplay = v; ConstellationClient.saveConfig(); }));
                
                modules.add(new Module("starredMobs", "Starred / objective mobs", "Combat",
                    () -> c.starredMobs, v -> { c.starredMobs = v; ConstellationClient.saveConfig(); })
                    .sub("Line-of-sight outline", true));
                modules.add(new Module("minibossHighlights", "Dungeon miniboss highlights", "Combat",
                    () -> c.minibossHighlights, v -> { c.minibossHighlights = v; ConstellationClient.saveConfig(); })
                    .sub("Adventurer, Assassin, Midas and Spirit Bear", true));
                modules.add(new Module("deathmiteHighlight", "Deathmite warning", "Combat",
                    () -> c.deathmiteHighlight, v -> { c.deathmiteHighlight = v; ConstellationClient.saveConfig(); })
                    .b("Tracer", () -> c.deathmiteTracer, v -> { c.deathmiteTracer = v; ConstellationClient.saveConfig(); })
                    .sub("Dark-red box and label", true));
                modules.add(new Module("felHighlight", "Inactive Fel highlight", "Combat",
                    () -> c.felHighlight, v -> { c.felHighlight = v; ConstellationClient.saveConfig(); })
                    .b("Tracer", () -> c.felTracer, v -> { c.felTracer = v; ConstellationClient.saveConfig(); })
                    .b("Keep after activation", () -> c.felHighlightActive, v -> { c.felHighlightActive = v; ConstellationClient.saveConfig(); })
                    .sub("Magenta box and label", true));
                modules.add(new Module("hideSkeletonSkulls", "Hide idle Skeleton Skulls", "Combat",
                    () -> c.hideSkeletonSkulls, v -> { c.hideSkeletonSkulls = v; ConstellationClient.saveConfig(); })
                    .b("Highlight moving skulls", () -> c.highlightMovingSkeletonSkulls, v -> { c.highlightMovingSkeletonSkulls = v; ConstellationClient.saveConfig(); })
                    .sub("Moving skulls remain visible", true));
                modules.add(new Module("hideSoulweaverSkulls", "Hide Soulweaver skulls", "Combat",
                    () -> c.hideSoulweaverSkulls, v -> { c.hideSoulweaverSkulls = v; ConstellationClient.saveConfig(); })
                    .sub("Exact haunted-skull texture only", true));
                modules.add(new Module("secretBats", "Secret bat highlights", "Combat",
                    () -> c.secretBats, v -> { c.secretBats = v; ConstellationClient.saveConfig(); })
                    .sub("Only 100-HP secret bats", true)
                    .sub("F4/M4 boss bats excluded", true));
                modules.add(new Module("lividFinder", "F5/M5 — highlight real Livid", "Combat",
                    () -> c.lividFinder, v -> { c.lividFinder = v; ConstellationClient.saveConfig(); })
                    .sub("Green outline and label", true));
                modules.add(new Module("teammateBoxes", "Teammate highlights", "Combat",
                    () -> c.teammateBoxes, v -> { c.teammateBoxes = v; ConstellationClient.saveConfig(); })
                    .sub("Names and class initials", true)
                    .sub("Colours follow dungeon class", true));
                modules.add(new Module("dropEsp", "Dropped items ESP", "Combat",
                    () -> c.dropEsp, v -> { c.dropEsp = v; ConstellationClient.saveConfig(); })
                    .sub("Show labels", true)
                    .sub("Distance", true));
                modules.add(new Module("blessingDisplay", "Blessing levels HUD", "Combat",
                    () -> c.blessingDisplay, v -> { c.blessingDisplay = v; ConstellationClient.saveConfig(); })
                    .sub("Compact", true));
                modules.add(new Module("doorTracker", "Wither/blood key + door ESP", "Combat",
                    () -> c.doorTracker, v -> { c.doorTracker = v; ConstellationClient.saveConfig(); })
                    .sub("Keys include a beam", true)
                    .sub("Door colours", true));
                modules.add(new Module("shadowAssassinAlert", "Shadow Assassin target alert", "Combat",
                    () -> c.shadowAssassinAlert, v -> { c.shadowAssassinAlert = v; ConstellationClient.saveConfig(); })
                    .sub("Title and Wither sound", true));
                modules.add(new Module("rareDropAlerts", "Rare dungeon drop alerts", "Combat",
                    () -> c.rareDropAlerts, v -> { c.rareDropAlerts = v; ConstellationClient.saveConfig(); })
                    .sub("Ice Spray and M7 Skeleton Master chestplate", true));
                modules.add(new Module("rareRoomAlerts", "Rare dungeon room alerts", "Combat",
                    () -> c.rareRoomAlerts, v -> { c.rareRoomAlerts = v; ConstellationClient.saveConfig(); })
                    .sub("Trinity, Tomioka, Duncan and Empty", true));
                modules.add(new Module("mageBeamCleaner", "Replace mage beam particles with a line", "Combat",
                    () -> c.mageBeamCleaner, v -> { c.mageBeamCleaner = v; ConstellationClient.saveConfig(); })
                    .b("Hide all dungeon firework particles", () -> c.mageBeamHideParticles, v -> { c.mageBeamHideParticles = v; ConstellationClient.saveConfig(); })
                    .b("Depth check", () -> c.mageBeamDepthCheck, v -> { c.mageBeamDepthCheck = v; ConstellationClient.saveConfig(); })
                    .sub("Duration/points/color: /magebeam", true));
                modules.add(new Module("etherwarpHelper", "Etherwarp target box (advisory)", "Combat",
                    () -> c.etherwarpHelper, v -> { c.etherwarpHelper = v; ConstellationClient.saveConfig(); })
                    .sub("Green valid / red no headroom", true));
                modules.add(new Module("spiritLeapHelper", "Custom Spirit Leap interface", "Combat",
                    () -> c.spiritLeapHelper, v -> { c.spiritLeapHelper = v; ConstellationClient.saveConfig(); })
                    .b("Replace the vanilla menu", () -> c.spiritLeapCustomGui, v -> { c.spiritLeapCustomGui = v; ConstellationClient.saveConfig(); })
                    .b("Static role slots", () -> c.spiritLeapStaticSlots, v -> { c.spiritLeapStaticSlots = v; ConstellationClient.saveConfig(); })
                    .b("Act on mouse press", () -> c.spiritLeapClickOnPress, v -> { c.spiritLeapClickOnPress = v; ConstellationClient.saveConfig(); })
                    .b("Show class", () -> c.spiritLeapShowClass, v -> { c.spiritLeapShowClass = v; ConstellationClient.saveConfig(); })
                    .b("Show dead players", () -> c.spiritLeapShowDead, v -> { c.spiritLeapShowDead = v; ConstellationClient.saveConfig(); })
                    .b("Class leap keys (1-5)", () -> c.spiritLeapKeybinds, v -> { c.spiritLeapKeybinds = v; ConstellationClient.saveConfig(); })
                    .sub("Sorting, scale, color and custom order: /leapgui", true));
                modules.add(new Module("leapCounter", "F7/M7 players leaped HUD", "Combat",
                    () -> c.leapCounter, v -> { c.leapCounter = v; ConstellationClient.saveConfig(); })
                    .b("Completion alert", () -> c.leapCounterAlert, v -> { c.leapCounterAlert = v; ConstellationClient.saveConfig(); })
                    .b("Alert sound", () -> c.leapCounterSound, v -> { c.leapCounterSound = v; ConstellationClient.saveConfig(); }));
                modules.add(new Module("lowHealthAlert", "Low-health title + sound", "Combat",
                    () -> c.lowHealthAlert, v -> { c.lowHealthAlert = v; ConstellationClient.saveConfig(); })
                    .sub("Threshold: " + c.lowHealthPercent + "%", true));
                modules.add(new Module("spiritMaskTracker", "Spirit Mask state", "Combat",
                    () -> c.spiritMaskTracker, v -> { c.spiritMaskTracker = v; ConstellationClient.saveConfig(); })
                    .b("Only in dungeons", () -> c.spiritMaskOnlyDungeons, v -> { c.spiritMaskOnlyDungeons = v; ConstellationClient.saveConfig(); })
                    .b("Used alert", () -> c.spiritMaskUsedAlert, v -> { c.spiritMaskUsedAlert = v; ConstellationClient.saveConfig(); })
                    .b("Ready alert", () -> c.spiritMaskReadyAlert, v -> { c.spiritMaskReadyAlert = v; ConstellationClient.saveConfig(); })
                    .b("Ready chat", () -> c.spiritMaskReadyChat, v -> { c.spiritMaskReadyChat = v; ConstellationClient.saveConfig(); })
                    .b("HUD", () -> c.spiritMaskHud, v -> { c.spiritMaskHud = v; ConstellationClient.saveConfig(); })
                    .b("Item cooldown", () -> c.spiritMaskItemCooldown, v -> { c.spiritMaskItemCooldown = v; ConstellationClient.saveConfig(); })
                    .sub("All alert, HUD, item, timing and template options: /spiritmask", true));
            }

            // builds modules from every bool...
            default -> {
                BaseConfigGroup c = cfg.getSubConfig(constellationId);
                if (c != null) {
                    cats = new String[]{"Features"};
                    for (var field : c.getClass().getFields()) {
                        String n = field.getName();
                        if (n.equals("enabled") || n.equals("version")) continue;
                        if (field.getType() != boolean.class) continue;
                        try {
                            boolean val = field.getBoolean(c);
                            String label = autoLabel(n);
                            modules.add(new Module(n, label, "Features",
                                () -> { try { return field.getBoolean(c); } catch (Exception e) { return false; } },
                                v -> { try { field.setBoolean(c, v); ConstellationClient.saveConfig(); } catch (Exception e) {} }));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        
        if (cats.length == 0) cats = new String[]{"General"};
        selectedCat = Math.clamp(selectedCat, 0, cats.length - 1);

        
        activeConfig = ConstellationClient.instance().configManager().getGroup(constellationId);
        for (Module m : modules) {
            m.backing = activeConfig;
            m.knob = m.get.getAsBoolean() ? 1 : 0;
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        int fullW = mc.getWindow().getGuiScaledWidth(), fullH = mc.getWindow().getGuiScaledHeight();
        SpaceBackground.renderConfig(g, fullW, fullH, delta);
        int w = panelW(fullW), h = panelH(fullH);
        int px = (fullW - w) / 2, py = (fullH - h) / 2;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now; lastMx = mx; lastMy = my;

        g.fill(0, 0, fullW, fullH, 0x880A0A14);
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        mx -= px; my -= py;

        g.fill(0, 0, w, h, 0xF212121F);

        int sw = sideW(w), navY0 = TB + 4;
        g.fill(0, 0, sw, h, 0xFF0E0E1A);
        g.fill(sw - 1, 0, sw, h, ConstellationTheme.ACCENT_DIM);
        for (int i = 0; i < cats.length; i++) {
            int y = navY0 + i * 24;
            boolean sel = i == selectedCat, hov = mx >= 0 && mx < sw && my >= y && my < y + 24;
            if (sel) g.fill(0, y, sw, y + 24, 0xFF1A1A28);
            else if (hov) g.fill(0, y, sw, y + 24, 0xFF252535);
            if (sel) g.fill(0, y, 3, y + 24, ConstellationTheme.ACCENT);
            g.text(mc.font, cats[i], 12, y + 7, sel ? ConstellationTheme.ACCENT_BRIGHT : (hov ? ConstellationTheme.TEXT : ConstellationTheme.TEXT_MUTED), false);
        }

        g.fill(0, 0, w, TB, 0xFF0E0E1A);
        g.fill(0, TB - 1, w, TB, ConstellationTheme.ACCENT);
        g.text(mc.font, constellationId, 12, 10, ConstellationTheme.ACCENT_BRIGHT, false);
        String esc = "esc to close  ·  right-click for settings";
        g.text(mc.font, esc, w - mc.font.width(esc) - 10, 12, ConstellationTheme.TEXT_MUTED, false);

        var vis = visibleModules();
        int cols = cols(w), cardW = cardW(w);
        int rows = (vis.size() + cols - 1) / cols;
        int contentH = rows * (CARD_H + CARD_GAP);
        int viewH = h - gridTop() - 6;
        maxScroll = Math.max(0, contentH - viewH);
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
        if (scrollTarget < 0) scrollTarget = 0;
        scrollF += (scrollTarget - scrollF) * Math.min(1, dt * 16);
        if (Math.abs(scrollF - scrollTarget) < 0.5f) scrollF = scrollTarget;

        g.text(mc.font, cats[selectedCat], gridX(w), TB + 8, ConstellationTheme.TEXT, false);
        g.text(mc.font, vis.size() + " modules", gridX(w) + mc.font.width(cats[selectedCat]) + 10, TB + 9, ConstellationTheme.TEXT_MUTED, false);

        g.enableScissor(sw, gridTop(), w, h);
        int gx = gridX(w), gTop = gridTop(), sc = Math.round(scrollF);
        for (int idx = 0; idx < vis.size(); idx++) {
            Module m = vis.get(idx);
            int colI = idx % cols, rowI = idx / cols;
            int cx = gx + colI * (cardW + CARD_GAP);
            int cy = gTop + rowI * (CARD_H + CARD_GAP) - sc;
            boolean hov = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H && my >= gTop;
            drawCard(g, m, cx, cy, cardW, hov, dt);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int tx = w - 5, barH = Math.max(20, viewH * viewH / contentH);
            g.fill(tx, gTop, tx + 3, gTop + viewH, 0xFF15111f);
            g.fill(tx, gTop + (int)((viewH - barH) * (scrollF / maxScroll)), tx + 3, gTop + (int)((viewH - barH) * (scrollF / maxScroll)) + barH, ConstellationTheme.ACCENT_DIM);
        }

        
        boolean modalUp = openModule != null;
        modalAnim += ((modalUp ? 1f : 0f) - modalAnim) * Math.min(1, dt * 14);
        if (modalAnim > 0.01f && openModule != null) {
            int mx2 = w/2 - 180, my2 = h/2 - 120;
            g.fill(mx2, my2, mx2 + 360, my2 + 240, 0xF21A1A28);
            g.fill(mx2, my2, mx2 + 360, my2 + 3, ConstellationTheme.ACCENT);
            g.text(mc.font, openModule.name.replaceAll("([A-Z])", " $1").trim(), mx2 + 10, my2 + 10, ConstellationTheme.ACCENT_BRIGHT, false);
            g.text(mc.font, openModule.desc, mx2 + 10, my2 + 24, ConstellationTheme.TEXT_MUTED, false);
            int oy = my2 + 44;
            for (var sub : openModule.subs) {
                boolean on = sub.get.getAsBoolean();
                String prefix = sub.editable ? (on ? "> " : "  ") : "- ";
                int colour = sub.editable ? (on ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT_MUTED)
                    : ConstellationTheme.TEXT_MUTED;
                g.text(mc.font, prefix + sub.label, mx2 + 10, oy, colour, false);
                oy += 18;
            }
            g.text(mc.font, "click to close", mx2 + 360 - mc.font.width("click to close") - 10, my2 + 230, ConstellationTheme.TEXT_MUTED, false);
        }

        g.fill(0, 0, w, 1, ConstellationTheme.ACCENT);
        g.fill(0, h - 1, w, h, ConstellationTheme.ACCENT_DIM);
        g.fill(0, 0, 1, h, ConstellationTheme.ACCENT_DIM);
        g.fill(w - 1, 0, w, h, ConstellationTheme.ACCENT_DIM);

        // subtle pulsing dot — matches HubScreen
        long dotPulse = (System.currentTimeMillis() / 2000) % 2;
        int dotAlpha = dotPulse == 0 ? 100 : 50;
        g.fill(w + (fullW - w) / 2 - 4, fullH - 10, w + (fullW - w) / 2, fullH - 6, (dotAlpha << 24) | ConstellationTheme.ACCENT);
        g.pose().popMatrix();
    }

    private void drawCard(GuiGraphicsExtractor g, Module m, int cx, int cy, int cardW, boolean hov, float dt) {
        boolean on = m.get.getAsBoolean();
        m.knob += ((on ? 1f : 0f) - m.knob) * Math.min(1, dt * 16);
        m.hover += ((hov ? 1f : 0f) - m.hover) * Math.min(1, dt * 14);
        g.fill(cx, cy, cx + cardW, cy + CARD_H, on ? 0xFF222240 : (hov ? 0xFF252535 : 0xFF1A1A28));
        int knobCol = lerp(0xFF333333, ConstellationTheme.ACCENT, m.knob);
        g.fill(cx, cy, cx + cardW, cy + 3, knobCol);
        String name = m.name.replaceAll("([A-Z])", " $1").trim();
        if (!name.isEmpty()) name = name.substring(0, 1).toUpperCase() + name.substring(1);
        if (name.length() > 18) name = name.substring(0, 17) + "…";
        Minecraft mc = Minecraft.getInstance();
        g.text(mc.font, name, cx + 5, cy + 8, m.knob > 0.5f ? ConstellationTheme.ACCENT_BRIGHT : ConstellationTheme.TEXT, false);
        g.text(mc.font, m.desc, cx + 5, cy + 22, ConstellationTheme.TEXT_MUTED, false);
        
        if (!m.subs.isEmpty()) {
            long editable = m.subs.stream().filter(sub -> sub.editable).count();
            String badge = editable > 0 ? editable + " opts" : "details";
            g.text(mc.font, badge, cx + cardW - mc.font.width(badge) - 5, cy + 32, ConstellationTheme.ACCENT_DIM, false);
        }
    }

    private List<Module> visibleModules() {
        if (cats.length == 0) return modules;
        List<Module> out = new ArrayList<>();
        for (Module m : modules) if (m.cat.equals(cats[selectedCat])) out.add(m);
        return out;
    }

    

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = lastMx, my = lastMy;
        int fullW = Minecraft.getInstance().getWindow().getGuiScaledWidth(), fullH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int w = panelW(fullW), h = panelH(fullH);
        int px = (fullW - w) / 2, py = (fullH - h) / 2;
        mx -= px; my -= py;

        
        if (openModule != null) {
            int mx2 = w/2 - 180, my2 = h/2 - 120, oy = my2 + 44;
            for (var sub : openModule.subs) {
                if (mx >= mx2 + 10 && mx <= mx2 + 350 && my >= oy && my < oy + 18) {
                    if (sub.editable) {
                        sub.set.accept(!sub.get.getAsBoolean());
                        ConstellationClient.saveConfig();
                    }
                    return true;
                }
                oy += 18;
            }
            openModule = null;
            return true;
        }

        
        int sw = sideW(w), navY0 = TB + 4;
        for (int i = 0; i < cats.length; i++) {
            if (mx >= 0 && mx < sw && my >= navY0 + i * 24 && my < navY0 + i * 24 + 24) {
                selectedCat = i; openModule = null; scrollTarget = 0; return true;
            }
        }

        
        var vis = visibleModules();
        int cols = cols(w), cardW = cardW(w);
        int gx = gridX(w), gTop = gridTop(), sc = Math.round(scrollF);
        boolean rightClick = event.button() == 1;
        for (int idx = 0; idx < vis.size(); idx++) {
            Module m = vis.get(idx);
            int cx = gx + (idx % cols) * (cardW + CARD_GAP);
            int cy = gTop + (idx / cols) * (CARD_H + CARD_GAP) - sc;
            if (mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H && my >= gTop) {
                if (m.name.equals("messageEditor")) {
                    Minecraft.getInstance().setScreenAndShow(new PartyMessageScreen(this));
                    return true;
                }
                if (rightClick && m.name.equals("partyGuard")) {
                    Minecraft.getInstance().setScreenAndShow(new PartyGuardScreen(this));
                    return true;
                }
                if (rightClick && m.name.equals("smartRefill")) {
                    Minecraft.getInstance().setScreenAndShow(new SmartRefillScreen(this));
                    return true;
                }
                if (rightClick && m.name.equals("dungeonStats")) {
                    Minecraft.getInstance().setScreenAndShow(new DungeonStatsScreen(this));
                    return true;
                }
                if (rightClick && m.name.equals("spiritLeapHelper")) {
                    Minecraft.getInstance().setScreenAndShow(new SpiritLeapSettingsScreen(this));
                    return true;
                }
                if (rightClick) {
                    
                    if (!m.subs.isEmpty()) openModule = (openModule == m) ? null : m;
                } else {
                    // left-click: toggle master switch
                    boolean next = !m.get.getAsBoolean();
                    m.set.accept(next);
                    m.knob = next ? 1 : 0;
                    ConstellationClient.saveConfig();
                }
                return true;
            }
        }

        return super.mouseClicked(event, dbl);
    }

    
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (openModule != null) return true;
        scrollTarget -= (int)(scrollY * 30);
        scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (openModule != null) { openModule = null; return true; }
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        var p = parent;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(p));
    }

    private static int lerp(int a, int b, float t) {
        t = Math.clamp(t, 0, 1);
        int ar = (a>>16)&0xFF, ag = (a>>8)&0xFF, ab = a&0xFF;
        int br = (b>>16)&0xFF, bg = (b>>8)&0xFF, bb = b&0xFF;
        return 0xFF000000 | ((int)(ar+(br-ar)*t)<<16) | ((int)(ag+(bg-ag)*t)<<8) | (int)(ab+(bb-ab)*t);
    }
}
