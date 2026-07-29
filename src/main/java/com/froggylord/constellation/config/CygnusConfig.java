package com.froggylord.constellation.config;

public class CygnusConfig extends BaseConfigGroup {

    { enabled = false; }
    @Override public int currentVersion() { return 0; }

    public boolean calendarHud = true;
    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/events/EventNotifications.java
    public boolean eventCalendar = true;
    public boolean eventCalendarHud = true;
    public boolean eventCalendarShowActive = true;
    public boolean eventCalendarShowLocation = true;
    public boolean eventCalendarShowSkyblockDate = true;
    public boolean eventCalendarShowYear = true;
    public boolean eventCalendarChat = true;
    public boolean eventCalendarTitle = true;
    public boolean eventCalendarSound = true;
    public boolean eventCalendarOnlySkyblock = true;
    public boolean eventCalendarShowFetchState = false;
    public int eventCalendarRows = 3;
    public int eventCalendarRefreshMinutes = 30;
    public int eventCalendarColor = 0xFFFFAA00;
    public int eventCalendarActiveColor = 0xFF55FF55;
    public String eventCalendarReminderSeconds = "300,60";
    public String eventCalendarIncludes = "";
    public String eventCalendarExcludes = "Jacob's Farming Contest,Cult of the Fallen Star";

    
    public boolean dianaInquisitorAlert = true;
    public boolean dianaInquisitorShare = false; 
    public boolean dianaDropTracker = true;
    public boolean dianaBurrowWaypoints = true; 
    // ported from Devonian (GPL-3.0): features/diana/BurrowWaypoint.kt
    public boolean dianaBurrowBox = true;
    public boolean dianaBurrowBeam = true;
    public boolean dianaBurrowLabel = true;
    public boolean dianaBurrowDistance = true;
    public boolean dianaBurrowThroughWalls = true;
    public int dianaBurrowStartColor = 0xFF00FF00;
    public int dianaBurrowMobColor = 0xFFFF3030;
    public int dianaBurrowTreasureColor = 0xFFFFFF00;
    public int dianaBurrowRange = 256;
    public int dianaBurrowLifetimeSeconds = 300;
    // ported from Devonian (GPL-3.0): features/diana/DianaMobTracker.kt
    public boolean dianaMobTracker = true;
    public boolean dianaMobHud = true;
    public boolean dianaMobHudAll = false;
    public boolean dianaPersistentStats = true;
    public java.util.Map<String,Integer> dianaMobCounts = new java.util.LinkedHashMap<>();
    // ported from Devonian (GPL-3.0): features/diana/DianaDropTracker.kt
    public boolean dianaDropHud = true;
    public boolean dianaDropHudAll = false;
    public java.util.Map<String,Integer> dianaDropCounts = new java.util.LinkedHashMap<>();
    public long dianaCoinsDug = 0;
    public boolean dianaRareDropSound = true;
    public boolean dianaRareDropTitle = true;
    public boolean dianaInquisitorWaypoint = true;
    public boolean dianaInquisitorSound = true;
    public boolean dianaInquisitorTitle = true;
    public int dianaInquisitorColor = 0xFFFFAA00;
    public int dianaInquisitorWaypointSeconds = 180;
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
    // ported from Devonian (GPL-3.0): api/MayorApi.kt
    public boolean mayorState = true;
    public boolean mayorHud = true;
    public boolean mayorShowMinister = true;
    public boolean mayorShowPerks = true;
    public boolean mayorShowPerkDescriptions = false;
    public boolean mayorShowElectionLeader = true;
    public boolean mayorShowElectionVotes = true;
    public boolean mayorShowFetchState = false;
    public boolean mayorChangeChat = true;
    public boolean mayorChangeTitle = true;
    public boolean mayorChangeSound = true;
    public boolean mayorOnlySkyblock = true;
    public int mayorRefreshMinutes = 21;
    public int mayorHudPerkLimit = 6;
    public int mayorColor = 0xFFFFAA00;
    public String mayorPerkIncludes = "";
    public String mayorPerkExcludes = "";
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
