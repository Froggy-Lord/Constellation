package com.froggylord.constellation.config;

import com.froggylord.constellation.hud.HudPosition;

public class OrionConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    
    public boolean scoreHud = true;
    public boolean secretsHud = true;
    public boolean cryptsHud = true;
    public boolean deathsHud = true;
    public boolean timerHud = true;
    public boolean roomNameHud = true;
    public boolean mimicIndicator = true;
    public boolean splitsHud = false;

    
    public boolean scorePings = true;
    public boolean scorePingTitle = true;
    public boolean scorePingSound = true;

    
    public boolean dungeonMap = true;
    public int mapScale = 2; 
    public int mapX = 1;     
    public int mapY = 2;

    
    public boolean secretWaypoints = true;
    public boolean progressiveReveal = true;
    public boolean oneSecretAtATime = false;
    public boolean routes = true;
    public boolean pearlRoutes = false;
    public boolean routeLines = true;
    public boolean customWaypoints = false;
    public boolean echoOnCollect = true;
    public boolean perRoomCount = true;

    
    public boolean partyFinderGui = true;
    public int minSecrets = 0;
    public String classFilter = "";

    
    public boolean autoRequeue = false;
    public int requeueDelaySec = 3;
    public boolean requeueSafeMode = true;

    
    public boolean starredMobs = true;
    public boolean secretBats = true;
    public boolean teammateBoxes = false;

    
    public boolean abilityTracker = true;
    public boolean abilityReadyDing = true;
    public boolean lowHealthAlert = true;
    public boolean spiritLeapHelper = true;
    public boolean dungeonCopilot = true;
    public boolean doorTracker = true;
    public boolean mimicPartyPing = true;
    public boolean streamerMode = false;
    public boolean rareDropAlerts = true;

    
    public boolean terminalSolvers = true;
    public boolean terminalNumbers = true;
    public boolean blockWrongTerminalClicks = false;

    
    public boolean blazeSolver = true;

    
    public boolean blessingDisplay = true;
    public boolean spiritLeapHelperMenu = true;

    
    public boolean dropEsp = true;

    // puzzle solvers
    public boolean simonSaysSolver = true;
    public boolean threeWeirdosSolver = true;
    public boolean triviaSolver = true;
    public boolean creeperBeamsSolver = true;

    
    public boolean lividFinder = true;

    
    public boolean ticTacToeSolver = true;

    
    public boolean fireFreezeTimer = true;

    
    public boolean guardianHealth = true;
    public boolean shadowAssassinAlert = true;

    
    public boolean minibossHighlights = true;
    public boolean rareRoomAlerts = true;
    public boolean mageBeamCleaner = true; 
    public boolean spiritBowTimer = true;  
    public boolean melodyTerminalHelper = true; 
    public boolean saVanishTimer = true;    
    public boolean dungeonPotionsHud = true; 
    public boolean chestProfitCalc = true;    
    public boolean m7DragonMarkers = true;    
    public boolean secretChimeCustom = true;  
    public boolean waterboardSolver = true;    // highlight water puzzle gates (...
    public boolean iceFillSolver = true;       
    public boolean teleportMazeSolver = true;  
    public boolean silverfishSolver = true;    // highlight silverfish bfs path ...
    public boolean lightsOnSolver = true;     
    public boolean arrowAlignSolver = true;    
    public boolean targetPracticeSolver = true;
    public boolean terminalSimulator = true;
    public boolean melodyTerminalHelper = true;
}
