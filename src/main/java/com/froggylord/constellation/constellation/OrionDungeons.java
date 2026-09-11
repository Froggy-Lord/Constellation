package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.data.DungeonData;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import com.froggylord.constellation.hud.PuzzleHudWidget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class OrionDungeons extends BaseConstellation {

    @Override public String id() { return "orion"; }
    @Override public String displayName() { return "Orion"; }
    @Override public String description() { return "dungeon stuff"; }

    private OrionConfig cfg;
    private boolean wasInDungeon = false;
    private int doorsOpened = 0;
    // party-ping guards: fire at most once per run and never on our own party echo (see onChat below)
    private boolean mimicPinged = false;
    private boolean princePinged = false;
    private static long fireFreezeMs = 0;
    private static long spiritBowUntil = 0;
    private static long saVanishUntil = 0;

    private static void rareRoomAlert(String room, String colour) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            // use the title hud - sendSystemMessage re-fires the chat receive event and our own
            // listener re-matches "trinity"/etc and loops forever (stackoverflow)
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTitle(Component.literal(colour + "Rare room: " + room));
            mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
        }
    }

    @Override
    public void init(InitContext ctx) {
        cfg = (OrionConfig) getConfig();
        if (cfg == null) return;

        
        DungeonData.load();
        SecretWaypoints.init();
        SecretCompassHelper.init(cfg);
        Routes.init();
        BossTickTimers.init();
        TerminalBreakdown.init();
        DungeonPartyAlerts.init();
        ArchitectNotifier.init(cfg);
        DungeonMilestone.init();
        M7RelicTimer.init();
        SpiritBearTimer.init();
        SpiritMaskState.init(cfg);
        DungeonBreakerCharges.init();
        LividInvulnerableTimer.init();
        LeapCounter.init();
        TerracottaTimer.init();

        
        OrionTerminals.init(cfg);
        TeleportMazeOverlay.init();
        
        OrionSpiritLeap.init(cfg);
        PartyFinderOverlay.init(cfg);
        AutoRequeue.init(cfg);
        DungeonQueueHelper.init(cfg);
        PartyGuard.init(cfg);
        SmartRefill.init(cfg);
        SpringBootsHelper.init(cfg);
        MageBeamHelper.init();
        
        DoorHighlighter.init();
        // puzzle solvers — simon says, t...
        OrionPuzzles.init(cfg);
        
        ChestProfitCalc.init(cfg);
        // f5/m5 livid finder — hide wron...
        LividFinder.init();

        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) SecretWaypoints.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) SecretCompassHelper.draw(ctx2); });
        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) Routes.draw(ctx2); });
        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) CombatEsp.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) MageBeamHelper.draw(ctx2); });
        // f3/m3 blaze puzzle — mark lowe...
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) BlazeSolver.draw(ctx2); });
        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) DropEsp.draw(ctx2); });
        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) DoorHighlighter.draw(ctx2); });
        // advisory etherwarp target box — render only, never warps
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) EtherwarpHelper.draw(ctx2); });
        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) OrionPuzzles.drawBeams(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) ThreeWeirdosSolver.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) QuizSolver.draw(ctx2); });
        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) M7Dragons.draw(ctx2); });
        M7Dragons.init();
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) M7RelicHighlight.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) WitherHighlight.draw(ctx2); });

        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) TerracottaTimer.draw(ctx2); });
        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) GoldorWaypoints.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) HealerPlatformHighlight.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) TargetPracticeSolver.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) ArrowAlignDevice.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) LightsOnDevice.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) TeleportMazeOverlay.draw(ctx2); });
        // water puzzle gate highlighter
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) WaterPuzzleHelper.draw(ctx2); });
        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) IceFillHelper.draw(ctx2); });
        
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) BoulderSolver.draw(ctx2); });
        // silverfish solver — highlight ...
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) SilverfishSolver.draw(ctx2); });
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) SpringBootsHelper.draw(ctx2); });

        LividFinder.init();
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) LividFinder.draw(ctx2); });

        BloodTimer.init();
        BloodCampHelper.init();
        registerRenderer(ctx2 -> { if (isEnabled() && cfg.enabled) BloodCampHelper.draw(ctx2); });


        // sees boss dialogue even if the...
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!isEnabled() || !cfg.enabled) return true;
            if (!overlay && ConstellationClient.loc().inDungeons()) {
                String s = message.getString();
                // ported from Odin (BSD-3-Clause): features/impl/dungeon/LeapMenu.kt
                if (s.matches("^You have teleported to \\w{1,16}!$"))
                    PartyMessages.send("leap", java.util.Map.of("leaped-player",
                        s.substring("You have teleported to ".length(), s.length() - 1)));
                if (cfg.ticTacToeSolver && RoomMatch.isMatched()
                    && RoomMatch.currentRoom().contains("tic-tac-toe")
                    && s.matches("^PUZZLE SOLVED! \\w{1,16} tied Tic Tac Toe! Good job!$"))
                    PartyMessages.send("ttt-done");
                com.froggylord.constellation.data.DungeonScore.onChat(s);
                com.froggylord.constellation.data.RunStats.onChat(s);
                com.froggylord.constellation.data.DefensiveTracker.onChat(s);
                SecretWaypoints.onChat(s);
                if (cfg.blessingDisplay) OrionBlessings.onChat(s);
                if (cfg.bloodTimer || cfg.bloodCampHelper || cfg.partyMessages) BloodTimer.onChat(s);
                
                if (cfg.rareRoomAlerts) {
                    String low = s.toLowerCase(java.util.Locale.ROOT);
                    if (low.contains("trinity")) rareRoomAlert("Trinity", "§d");
                    else if (low.contains("tomioka")) rareRoomAlert("Tomioka", "§b");
                    else if (low.contains("duncan")) rareRoomAlert("Duncan", "§6");
                    else if (low.contains("this room seems") && low.contains("empty")) rareRoomAlert("Empty Room", "§8");
                }
                
                // ported from Odin (BSD-3-Clause): features/impl/dungeon/Mimic.kt
                if (cfg.mimicPartyPing && !cfg.streamerMode && !mimicPinged
                    && (s.endsWith("Mimic dead!") || s.endsWith("Mimic Killed!")) && !isPartyEcho(s)) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        PartyMessages.send("mimic");
                        mimicPinged = true;
                    }
                }
                // prince mini-boss (F4/M4) — the hypixel bonus-score line is authoritative and never
                // matches our own "pc Prince Killed!" echo, but we still guard once-per-run + skip echoes.
                if (cfg.princePartyPing && !cfg.streamerMode && !princePinged
                    && s.equals("A Prince falls. +1 Bonus Score") && !isPartyEcho(s)) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        PartyMessages.send("prince");
                        princePinged = true;
                    }
                }
                // spirit bow — pickup starts a 3...
                if (cfg.spiritBowTimer && s.contains("Spirit Bow") && s.contains("picked up"))
                    spiritBowUntil = System.currentTimeMillis() + 30_000;

                // ported from devonian (GPL-3.0): features/dungeons/FireFreezeTimer.kt
                if (cfg.fireFreezeTimer
                    && s.equals("[BOSS] The Professor: Oh? You found my Guardians' one weakness?")
                    && ConstellationClient.dungeon().floor().endsWith("3")) {
                    fireFreezeMs = System.currentTimeMillis() + 5500;
                }

                
                if (cfg.shadowAssassinAlert && s.contains("Shadow Assassin") && (s.contains("targeted") || s.contains("targeting"))) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(Component.literal("§5Shadow Assassin!"));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.5f, 0.8f);
                    }
                    
                    if (cfg.saVanishTimer) saVanishUntil = System.currentTimeMillis() + 20_000;
                }

                
                if (s.contains("Wither Key") || s.contains("Blood Key")) {
                    if (s.contains("picked up")) {
                        if (s.contains("Wither")) doorsOpened++;
                        var mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.gui.hud.resetTitleTimes();
                            mc.gui.hud.setTitle(Component.literal("§cKey: " + s.trim()));
                        }
                    }
                }
                // wither door open — notify party
                if (s.contains("opened a Wither door") || s.contains("opened a Blood door")) {
                    doorsOpened++;
                    if ((cfg.bloodTimer || cfg.bloodCampHelper) && s.contains("Blood door")) BloodTimer.onBloodDoor();
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(Component.literal("§5Door: " + s.trim()));
                    }
                }
                
                if (cfg.rareDropAlerts && isRareDungeonDrop(s)) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(Component.literal("§6RARE DROP: " + s.trim()));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.8f);
                    }
                }
                
                if (s.contains("Trinity") || s.contains("Tomioka") || s.contains("Duncan")) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(Component.literal("§d" + s.trim()));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);
                    }
                }
            }
            return true;
        });

        // dungeon copilot — occasional c...
        every(200, "orion-copilot", () -> {
            if (!isEnabled() || cfg == null || !cfg.enabled || !cfg.dungeonCopilot || !ConstellationClient.loc().inDungeons()) return;
            var mc2 = Minecraft.getInstance();
            if (mc2.player == null) return;
            int s = com.froggylord.constellation.data.DungeonScore.score();
            String grade = com.froggylord.constellation.data.DungeonScore.grade();
            if (s >= 270) mc2.player.sendSystemMessage(Component.literal("§aCopilot: Score is " + s + " (" + grade + ") — looking good!"));
            else if (s >= 230) mc2.player.sendSystemMessage(Component.literal("§eCopilot: " + s + " — find more secrets for S+"));
            else mc2.player.sendSystemMessage(Component.literal("§cCopilot: " + s + " — need secrets + crypts for higher score"));
        });

        
        every(2, "orion-room-match", () -> {
            if (!isEnabled() || cfg == null || !cfg.enabled) return;
            if (ConstellationClient.loc().inDungeons()) {
                RoomMatch.update();
                com.froggylord.constellation.data.SkeletonScraper.tick();
                com.froggylord.constellation.data.DefensiveTracker.tick();
                wasInDungeon = true;
            } else if (wasInDungeon) {
                
                doorsOpened = 0;
                mimicPinged = false;
                princePinged = false;
                com.froggylord.constellation.data.RunStats.finishRun();
                com.froggylord.constellation.data.DungeonSplits.finishRun();
                com.froggylord.constellation.data.MapSegments.reset();
                RoomMatch.resetCache();
                com.froggylord.constellation.data.DungeonScore.reset();
                com.froggylord.constellation.data.DefensiveTracker.reset();
                OrionBlessings.reset();
                PartyMessages.reset();
                wasInDungeon = false;
                
            }
        });

        
        every(20, "orion-score", () -> {
            if (!isEnabled() || cfg == null || !cfg.enabled) return;
            if (ConstellationClient.loc().inDungeons()) com.froggylord.constellation.data.DungeonScore.update();
        });
    }

    // ported from Skyblocker (LGPL-3.0-or-later):
    // skyblock/special/DungeonsSpecialEffects.java (rare dungeon reward names)
    private static boolean isRareDungeonDrop(String value) {
        return value.contains("Recombobulator 3000") || value.contains("Giant's Sword")
            || value.contains("Necron's Handle") || value.contains("Shiny Necron's Handle")
            || value.contains("Shadow Fury") || value.contains("Dark Claymore")
            || value.contains("Spirit Mask") || value.contains("Necron Dye")
            || value.contains("Master Skull - Tier 5") || value.contains("Shadow Warp")
            || value.contains("Wither Shield") || value.contains("Implosion")
            || value.contains("Master Star") || value.contains("Wither Chestplate")
            || value.contains("Precursor Eye") || value.contains("Shadow Assassin Chestplate")
            || value.contains("Diamond Head");
    }

    @Override
    protected void onDisable() {
        RoomMatch.resetCache();
    }

    @Override
    public void registerHud(HudManager hud) {
        if (cfg == null) return;
        Minecraft mc = Minecraft.getInstance();

        
        hud.register(new HudWidget("orion-sa", "SA",
                () -> {
                    long left = saVanishUntil - System.currentTimeMillis();
                    if (left <= 0) return null;
                    return "§5Vanish " + (left / 1000) + "s";
                },
                HudPosition.of(6, 36), () -> cfg.saVanishTimer));
        hud.register(new HudWidget("orion-spiritbow", "SpiritBow",
                () -> {
                    long left = spiritBowUntil - System.currentTimeMillis();
                    if (left <= 0) return null;
                    return "§bBow " + (left / 1000) + "s";
                },
                HudPosition.of(6, 38), () -> cfg.spiritBowTimer));
        hud.register(new HudWidget("orion-spiritbear", "SpiritBear",
                SpiritBearTimer::hudText,
                HudPosition.of(6, 39), () -> cfg.spiritBearTimer));
        hud.register(new HudWidget("orion-livid-invulnerable", "LividInvulnerable",
                LividInvulnerableTimer::hudText,
                HudPosition.of(6, 41), () -> cfg.lividInvulnerableTimer));
        hud.register(new HudWidget("orion-freeze", "Freeze",
                () -> {
                    long left = fireFreezeMs - System.currentTimeMillis();
                    if (left <= 0) return null;
                    return "§bFreeze " + String.format("%.1fs", left / 1000.0);
                },
                HudPosition.of(6, 40), () -> cfg.fireFreezeTimer));
        hud.register(new HudWidget("orion-dpotions", "DungeonPotions",
                () -> {
                    if (!ConstellationClient.loc().inDungeons()) return null;
                    if (mc.player == null) return null;
                    var effects = mc.player.getActiveEffects();
                    if (effects.isEmpty()) return null;
                    StringBuilder sb = new StringBuilder("§5Potions ");
                    int shown = 0;
                    for (var e : effects) {
                        if (shown++ > 0) sb.append(" ");
                        int lvl = e.getAmplifier() + 1;
                        int dur = e.getDuration() / 20;
                        sb.append("§d").append(e.getEffect().value().getDisplayName().getString()).append(" §f").append(lvl).append(" §7").append(dur).append("s");
                        if (shown >= 2) break;
                    }
                    return sb.toString();
                },
                HudPosition.of(6, 32), () -> cfg.dungeonPotionsHud));
        hud.register(new HudWidget("orion-dungeon-breaker", "DungeonBreaker",
                DungeonBreakerCharges::hudText,
                HudPosition.of(6, 44), () -> cfg.dungeonBreakerDisplay));
        hud.register(new HudWidget("orion-spring-boots", "SpringBoots",
                SpringBootsHelper::hudText,
                HudPosition.of(25, 46), () -> cfg.springBootsHelper && cfg.springBootsHud));
        hud.register(new HudWidget("orion-queue-cooldown", "QueueCooldown",
                DungeonQueueHelper::hudText,
                HudPosition.of(25, 48), () -> cfg.dungeonQueueCooldown));
        hud.register(new HudWidget("orion-route-recording", "RouteRecording",
                Routes::recordingHudText,
                HudPosition.of(25, 50), () -> cfg.routeRecordingHud));
        hud.register(new HudWidget("orion-chest-profit", "ChestProfit",
                ChestProfitCalc::hudText,
                HudPosition.of(25, 52), () -> cfg.chestProfitCalc && cfg.chestProfitHud));
        hud.register(new com.froggylord.constellation.hud.BlessingsHudWidget(
                "orion-blessings", HudPosition.of(6, 46), () -> cfg.blessingDisplay));
        // consolidated skyhanni-style score panel: live 0-300 score + grade and a compact
        // breakdown (secrets %, crypts, deaths, room completion). replaces the old single line.
        hud.register(new com.froggylord.constellation.hud.ScoreHudWidget(
                "orion-score", HudPosition.of(6, 54), () -> cfg.scoreHud));
        hud.register(new PuzzleHudWidget(
            "orion-puzzles", HudPosition.of(6, 74), () -> cfg.puzzlesDisplay, () -> cfg.puzzlesCompact));
        hud.register(new HudWidget("orion-secrets", "Secrets",
                () -> !scoreReady() ? null : com.froggylord.constellation.data.DungeonScore.secretPercent() + "%",
                HudPosition.of(6, 66), () -> cfg.secretsHud));
        hud.register(new HudWidget("orion-secret-compass", "SecretCompass",
                SecretCompassHelper::hudText,
                HudPosition.of(25, 66), () -> cfg.secretCompassHelper && cfg.secretCompassHud));
        hud.register(new HudWidget("orion-m7phase", "M7Phase",
                () -> {
                    String p = ConstellationClient.dungeon().bossPhase();
                    return p.isEmpty() ? null : "§5" + p;
                },
                HudPosition.of(6, 42), () -> cfg.m7DragonMarkers));
        hud.register(new HudWidget("orion-m7-stack", "DragonStack",
                M7Dragons::stackHudText,
                HudPosition.of(6, 43), () -> cfg.m7DragonStackAimer && cfg.m7DragonStackHud));
        hud.register(new HudWidget("orion-m7-hits", "DragonHits",
                M7Dragons::hitHudText,
                HudPosition.of(6, 44), () -> cfg.m7DragonHitCounter && cfg.m7DragonHitHud));
        hud.register(new HudWidget("orion-m7-relic-timer", "Relics",
                M7RelicTimer::hudText,
                HudPosition.of(6, 44), () -> cfg.m7RelicTimer));
        hud.register(new HudWidget("orion-crypts", "Crypts",
                () -> !scoreReady() ? null : String.valueOf(com.froggylord.constellation.data.DungeonScore.crypts()),
                HudPosition.of(6, 78), () -> cfg.cryptsHud));
        hud.register(new HudWidget("orion-deaths", "Deaths",
                () -> !scoreReady() ? null : String.valueOf(ConstellationClient.dungeon().deaths()),
                HudPosition.of(6, 90), () -> cfg.deathsHud));
        hud.register(new HudWidget("orion-timer", "Timer",
                () -> !scoreReady() ? null : formatTime(com.froggylord.constellation.data.DungeonScore.timeSeconds()),
                HudPosition.of(25, 54), () -> cfg.timerHud));
        hud.register(new HudWidget("orion-boss-ticks", "BossTicks",
                BossTickTimers::hudText,
                HudPosition.of(25, 62), () -> cfg.timerHud));
        hud.register(new HudWidget("orion-milestone", "Milestone",
                DungeonMilestone::hudText,
                HudPosition.of(25, 70), () -> cfg.milestoneHud));
        hud.register(new HudWidget("orion-terminal-display", "Terminals",
                TerminalBreakdown::hudText,
                HudPosition.of(6, 60), () -> cfg.terminalDisplay));
        hud.register(new HudWidget("orion-leap-counter", "Leaps",
            LeapCounter::hudText, HudPosition.of(6, 64), () -> cfg.leapCounter));
        hud.register(new HudWidget("orion-terracotta", "Terracotta",
            TerracottaTimer::hudText, HudPosition.of(6, 68), () -> cfg.terracottaTimer && cfg.terracottaPhaseHud));
        // themed run panel: deaths + blood/boss/clear splits with per-floor PB comparison
        hud.register(new com.froggylord.constellation.hud.SplitsHudWidget(
                "orion-splits", HudPosition.of(25, 78), () -> cfg.splitsHud));
        hud.register(new HudWidget("orion-room", "Room",
                () -> {
                    if (!inDungeon()) return null;
                    return ConstellationClient.dungeon().currentRoom().isEmpty() ? "-" : ConstellationClient.dungeon().currentRoom();
                },
                HudPosition.of(25, 86), () -> cfg.roomNameHud));
        hud.register(new HudWidget("orion-mimic", "Mimic",
                () -> {
                    if (!scoreReady() || !com.froggylord.constellation.data.DungeonScore.isMimicFloor()) return null;
                    return com.froggylord.constellation.data.DungeonScore.mimicKilled() ? "§adead" : "§calive";
                },
                HudPosition.of(45, 54), () -> cfg.mimicIndicator));
        hud.register(new HudWidget("orion-doors", "Doors",
                () -> scoreReady() ? "§5Doors " + doorsOpened : null,
                HudPosition.of(45, 62), () -> cfg.mimicIndicator));
        hud.register(new HudWidget("orion-roomsecrets", "Secrets",
                () -> {
                    if (!inDungeon() || !RoomMatch.isMatched()) return null;
                    return SecretWaypoints.collectedCount() + "/" + SecretWaypoints.totalCount();
                },
                HudPosition.of(45, 70), () -> cfg.perRoomCount));
        hud.register(new HudWidget("orion-blood", "Blood",
                () -> inDungeon() ? BloodTimer.hudText() : null,
                HudPosition.of(45, 78), () -> cfg.bloodTimer));
        hud.register(new HudWidget("orion-defensive", "Defensive",
                () -> inDungeon() ? com.froggylord.constellation.data.DefensiveTracker.hudLine() : null,
                HudPosition.of(45, 86), () -> cfg.abilityTracker));
        hud.register(new HudWidget("orion-spiritmask", "Spirit Mask",
                SpiritMaskState::hudText,
                HudPosition.of(45, 94), () -> cfg.spiritMaskTracker && cfg.spiritMaskHud));
        hud.register(new HudWidget("orion-copilot", "Copilot",
                () -> {
                    if (!scoreReady()) return null;
                    int c = com.froggylord.constellation.data.DungeonScore.crypts();
                    int pct = com.froggylord.constellation.data.DungeonScore.secretPercent();
                    int s = com.froggylord.constellation.data.DungeonScore.score();
                    int d = com.froggylord.constellation.data.DungeonScore.deaths();
                    if (d > 0) return "§c" + d + " death" + (d > 1 ? "s" : "") + " – careful!";
                    if (c < 5 && pct < 100) return "§eFind " + (5 - c) + " crypts & secrets";
                    if (pct < 70) return "§cNeed secrets: " + pct + "%";
                    if (s >= 300) return "§aS+ secured!";
                    if (s >= 270) return "§6S — " + (300 - s) + " more for S+";
                    if (c < 4) return "§e" + (5 - c) + " crypts missing";
                    return "§aOn track for S+";
                },
                HudPosition.of(65, 54), () -> cfg.dungeonCopilot));
        
        hud.register(new com.froggylord.constellation.hud.MapHudElement());
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        ArchitectNotifier.registerCommands(dispatcher);
        DungeonQueueHelper.registerCommands(dispatcher);
        SecretCompassHelper.registerCommands(dispatcher);
        DungeonLootHelper.registerCommands(dispatcher);
        MageBeamHelper.registerCommands(dispatcher);
        OrionSpiritLeap.registerCommands(dispatcher);
        TerracottaTimer.registerCommands(dispatcher);
        GoldorWaypoints.registerCommands(dispatcher);
        WitherHighlight.registerCommands(dispatcher);
        WatcherBossBar.registerCommands(dispatcher);
        SpiritMaskState.registerCommands(dispatcher);
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dwaypoint")
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("add")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                        "name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(ctx -> CustomDungeonWaypoints.add(
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove")
                .executes(ctx -> CustomDungeonWaypoints.removeNearest()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list")
                .executes(ctx -> CustomDungeonWaypoints.list()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("export")
                .executes(ctx -> CustomDungeonWaypoints.exportRoom()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("import")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                        "json", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(ctx -> CustomDungeonWaypoints.importRoom(
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "json"))))));

        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("termsim")
            .executes(ctx -> {
                Minecraft.getInstance().execute(TerminalSimulatorScreen::openMenu);
                return 1;
            }));

        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("requeue")
            .executes(ctx -> AutoRequeue.status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("now")
                .executes(ctx -> AutoRequeue.schedule(true)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cancel")
                .executes(ctx -> AutoRequeue.cancel()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status")
                .executes(ctx -> AutoRequeue.status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("delay")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                        "seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 30))
                    .executes(ctx -> AutoRequeue.delay(
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds"))))));

        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("partyguard")
            .executes(ctx -> PartyGuard.open())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("config").executes(ctx -> PartyGuard.open()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(ctx -> PartyGuard.list()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("check")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                        "player", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> PartyGuard.check(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("whitelist")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                        "player", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> PartyGuard.addList(true, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("blacklist")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                        "player", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> PartyGuard.addList(false, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                        "player", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> PartyGuard.removeList(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player"))))));

        SmartRefill.registerCommands(dispatcher);

        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dungeonstats")
            .executes(ctx -> com.froggylord.constellation.data.RunStats.open())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("export").executes(ctx -> com.froggylord.constellation.data.RunStats.export()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("limit")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                    "runs", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 10000))
                    .executes(ctx -> com.froggylord.constellation.data.RunStats.limit(
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "runs")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("folder")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                    "path", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(ctx -> com.froggylord.constellation.data.RunStats.folder(
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "path")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                    "floor", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> com.froggylord.constellation.data.RunStats.clear(
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "floor"))))));
        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("roomdebug")
            .executes(ctx -> {
                var loc = ConstellationClient.loc();
                int roomCount = com.froggylord.constellation.data.DungeonRoomData.roomCount();
                String msg = "§e[Orion debug]§r\n"
                    + "§7data loaded:§r " + DungeonData.isLoaded() + " (" + roomCount + " rooms)\n"
                    + "§7onHypixel:§r " + loc.onHypixel() + "\n"
                    + "§7inDungeons:§r " + loc.inDungeons() + "\n"
                    + "§7area:§r " + loc.area() + "\n"
                    + "§7sidebar lines:§r " + loc.getSidebarLines().size() + "\n"
                    + "§7current room:§r '" + RoomMatch.currentRoom() + "'";
                var mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
                
                for (String line : loc.getSidebarLines()) {
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§8| §7" + line));
                }
                return 1;
            }));

        
        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dndebug")
            .executes(ctx -> {
                var mc = Minecraft.getInstance();
                if (mc.player == null) return 0;
                mc.player.sendSystemMessage(Component.literal("§e=== Constellation Debug ==="));
                mc.player.sendSystemMessage(Component.literal("§7area: §f" + ConstellationClient.loc().area() + " §7inDungeon: §f" + ConstellationClient.loc().inDungeons()));
                mc.player.sendSystemMessage(Component.literal("§7room: §f" + com.froggylord.constellation.data.RoomMatch.currentRoom() + " §7matched: §f" + com.froggylord.constellation.data.RoomMatch.isMatched()));
                mc.player.sendSystemMessage(Component.literal("§7score: §f" + com.froggylord.constellation.data.DungeonScore.score() + " §7grade: §f" + com.froggylord.constellation.data.DungeonScore.grade()));
                mc.player.sendSystemMessage(Component.literal("§7HP: §f" + com.froggylord.constellation.core.ActionBar.health() + "/" + com.froggylord.constellation.core.ActionBar.maxHealth()));
                mc.player.sendSystemMessage(Component.literal("§7sidebar lines:"));
                for (String l : ConstellationClient.loc().getSidebarLines()) {
                    mc.player.sendSystemMessage(Component.literal("§8  " + l));
                }
                return 1;
            }));

        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dungeonstats")
            .executes(ctx -> { com.froggylord.constellation.data.RunStats.printSession(); return 1; }));
        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("key")
            .executes(ctx -> {
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    var stack = mc.player.getMainHandItem();
                    String name = stack.getHoverName().getString();
                    mc.player.sendSystemMessage(Component.literal("§eKey: " + name));
                }
                return 1;
            }));

        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("roomscan")
            .executes(ctx -> {
                String dbg = RoomMatch.debugScan();
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§e[Orion scan]§r " + dbg));
                    mc.player.sendSystemMessage(Component.literal("§7→ room: '" + RoomMatch.currentRoom() + "'"));
                }
                return 1;
            }));

        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("roomcapture")
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                    "name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(ctx -> {
                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                    String msg = com.froggylord.constellation.data.SkeletonScraper.capture(name);
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[scraper]§r " + msg));
                    return 1;
                })));

        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("map")
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scale")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                        "size", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                    .executes(ctx -> {
                        int size = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "size");
                        cfg.mapScale = size;
                        ConstellationClient.saveConfig();
                        var mc = Minecraft.getInstance();
                        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[Orion]§r map scale → " + size));
                        return 1;
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
                .executes(ctx -> {
                    cfg.dungeonMap = !cfg.dungeonMap;
                    ConstellationClient.saveConfig();
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[Orion]§r map " + (cfg.dungeonMap ? "on" : "off")));
                    return 1;
                })));
    }

    private static boolean inDungeon() {
        return ConstellationClient.loc().inDungeons();
    }

    /**
     * true if a chat line is our own party-chat echo — a "Party >" line, or one that contains the
     * local player's name. guards the mimic/prince pings against re-matching the very message we
     * just sent ("Party > IGN: Mimic dead!" ends with "Mimic dead!"), which would loop forever.
     */
    private static boolean isPartyEcho(String s) {
        if (s.contains("Party >")) return true;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String own = mc.player.getGameProfile().name();
        return own != null && !own.isEmpty() && s.contains(own);
    }

    
    private static boolean scoreReady() {
        return inDungeon() && com.froggylord.constellation.data.DungeonScore.isActive();
    }

    private static String formatTime(int secs) {
        return secs / 60 + ":" + String.format("%02d", secs % 60);
    }
}
