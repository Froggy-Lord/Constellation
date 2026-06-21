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
}
public boolean rareDropAlerts = true;
