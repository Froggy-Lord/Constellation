package com.froggylord.constellation.config;

public class CygnusConfig extends BaseConfigGroup {

    { enabled = false; }
    @Override public int currentVersion() { return 0; }

    public boolean calendarHud = true;

    
    public boolean dianaInquisitorAlert = true;
    public boolean dianaInquisitorShare = false; 
    public boolean dianaDropTracker = true;
    public boolean dianaBurrowWaypoints = true; 
    public boolean carnivalHelper = true; 
    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/carnival/CatchAFish.java
    public boolean carnivalCatchFish = true;
    public boolean carnivalFishBox = true;
    public boolean carnivalFishLabel = true;
    public boolean carnivalFishBeam = false;
    public boolean carnivalFishSound = true;
    public boolean carnivalFishThroughWalls = true;
    public int carnivalFishColor = 0xFFFFD700;
    public int carnivalFishRange = 64;
    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/carnival/ZombieShootout.java
    public boolean carnivalZombieShootout = true;
    public boolean carnivalZombieBox = true;
    public boolean carnivalZombieLabel = true;
    public boolean carnivalZombieThroughWalls = true;
    public boolean carnivalLampOutline = true;
    public boolean carnivalLampLabel = false;
    public boolean carnivalLampThroughWalls = false;
    public int carnivalLampColor = 0xFFFF3030;
    public int carnivalDiamondColor = 0xFF00FFFF;
    public int carnivalGoldColor = 0xFFFFD700;
    public int carnivalIronColor = 0xFFC0C0C0;
    public int carnivalWoodColor = 0xFFA52A2A;
    public int carnivalZombieRange = 96;
    public boolean carnivalHud = true;
    public boolean newYearCakeTracker = true; 
    public boolean jerryTimer = true; 
    public boolean seasonDisplay = true; 
    public boolean spookyEventTracker = true; 
    public boolean raffleHelper = true;        
    public boolean mayorPerksDisplay = true;    
    public boolean dianaBurrowGuesser = true;   
    public boolean chimeraAlert = true;          
    public boolean daedalusAlert = true;         // alert when daedalus stick drop...
    public boolean mayorElectionHud = true;      
    public boolean eventNotificationHud = true;   
    public boolean carnivalScoreTracker = true;   
    public boolean lobbySeasonalDecorations = true; 
    public boolean spookyCandyHelper = true;      
    public boolean winterGiftTracker = true;       // track winter gifts opened (cmp...
    public boolean harvestFestivalHelper = true;
    public boolean anniversaryEventHelper = true;
    public boolean yearOfThePigHelper = true;
    public boolean yearOfTheSealHelper = true;
    public boolean greatSpookHelper = true;
}
