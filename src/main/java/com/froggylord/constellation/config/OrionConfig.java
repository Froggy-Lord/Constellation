package com.froggylord.constellation.config;

import com.froggylord.constellation.hud.HudPosition;

public class OrionConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    // HUD elements
    public boolean scoreHud = true;
    public boolean secretsHud = true;
    public boolean cryptsHud = true;
    public boolean deathsHud = true;
    public boolean timerHud = true;
    public boolean roomNameHud = true;
    public boolean mimicIndicator = true;
    public boolean splitsHud = false;

    // score milestone pings (270 = S, 300 = S+)
    public boolean scorePings = true;
    public boolean scorePingTitle = true;
    public boolean scorePingSound = true;

    // map
    public boolean dungeonMap = true;
    public int mapScale = 2; // 1-5
    public int mapX = 1;     // top-left, screen %
    public int mapY = 2;

    // secrets
    public boolean secretWaypoints = true;
    public boolean progressiveReveal = true;
    public boolean oneSecretAtATime = false;
    public boolean routes = true;
    public boolean pearlRoutes = false;
    public boolean routeLines = true;
    public boolean customWaypoints = false;
    public boolean echoOnCollect = true;
    public boolean perRoomCount = true;

    // party finder
    public boolean partyFinderGui = true;
    public int minSecrets = 0;
    public String classFilter = "";

    // auto requeue
    public boolean autoRequeue = false;
    public int requeueDelaySec = 3;
    public boolean requeueSafeMode = true;

    // combat esp (depth-tested — moving entities must respect walls per Hypixel rules)
    public boolean starredMobs = true;
    public boolean secretBats = true;
    public boolean teammateBoxes = false;

    // defensive ability tracker (Bonzo / Spirit / Phoenix cooldowns)
    public boolean abilityTracker = true;
    public boolean abilityReadyDing = true;
    public boolean lowHealthAlert = true;
    public boolean spiritLeapHelper = true;
    public boolean dungeonCopilot = true;
    public boolean doorTracker = true;
    public boolean mimicPartyPing = true;
    public boolean streamerMode = false;
    public boolean rareDropAlerts = true;

    // terminal solvers (F7/M7 phase 3) — highlight only, never auto-click
    public boolean terminalSolvers = true;
    public boolean terminalNumbers = true;
    public boolean blockWrongTerminalClicks = false;

    // F3/M3 blaze puzzle — box lowest + highest HP blaze, number the rest
    public boolean blazeSolver = true;

    // dungeon blessings picked up this run
    public boolean blessingDisplay = true;
    public boolean spiritLeapHelperMenu = true;

    // box useful dropped items on the floor (spirit leap, decoy, training weights...)
    public boolean dropEsp = true;

    // puzzle solvers — Simon Says, Three Weirdos, Trivia, Creeper Beams
    public boolean simonSaysSolver = true;
    public boolean threeWeirdosSolver = true;
    public boolean triviaSolver = true;
    public boolean creeperBeamsSolver = true;

    // F5/M5 Livid fight — show only the real clone, hide the 8 fakes
    public boolean lividFinder = true;

    // TicTacToe solver (F7/M7) — minimax best move
    public boolean ticTacToeSolver = true;

    // Fire Freeze staff cooldown timer (shows 5.7s countdown)
    public boolean fireFreezeTimer = true;

    // F3/M3 Guardian health display and Shadow Assassin warning
    public boolean guardianHealth = true;
    public boolean shadowAssassinAlert = true;

    // miniboss highlights + rare room alerts
    public boolean minibossHighlights = true;
    public boolean rareRoomAlerts = true;
    public boolean mageBeamCleaner = true; // clean line instead of firework particles (cmp. Skyblocker MageBeamRenderer)
    public boolean spiritBowTimer = true;  // show cooldown when spirit bow is picked up (cmp. Skyblocker)
    public boolean melodyTerminalHelper = true; // highlight next note in Melody terminal (cmp. Skyblocker)
    public boolean saVanishTimer = true;    // SA vanish countdown (cmp. Skyblocker BossManager)
    public boolean dungeonPotionsHud = true; // active dungeon potion levels (cmp. Skyblocker)
    public boolean chestProfitCalc = true;    // show dungeon chest total value (cmp. Skyblocker ChestValue)
    public boolean m7DragonMarkers = true;    // highlight M7 dragon priority (cmp. Skyblocker M7Dragons)
    public boolean dungeonCopilot = true;     // suggest next action in dungeon run (cmp. Skyblocker DungeonCopilot)
    public boolean secretChimeCustom = true;  // custom sound on secret collection (cmp. Skyblocker)
}
