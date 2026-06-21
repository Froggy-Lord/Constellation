package com.froggylord.constellation.config;

import java.util.*;

/**
 * Config for the Cassiopeia constellation (chat + commands).
 */
public class CassiopeiaConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    // chat cleaner — per-category toggles for spam
    public boolean cleanBlocksInWay = true;
    public boolean cleanNotEnoughMana = true;
    public boolean cleanCantTeleport = true;
    public boolean cleanBossDialogue = true;
    public boolean cleanBlessings = true;
    public boolean cleanMilestones = true;
    public boolean cleanSalvage = true;
    public boolean cleanKillCombo = true;
    public boolean cleanAbilityReady = true;
    public boolean cleanIncomingDamage = true;
    public boolean cleanDividers = true;
    public boolean cleanStashNag = true;
    public boolean cleanTrapTrips = true;
    public boolean cleanTeleportFlavour = true;
    public boolean cleanDungeonBuff = true;
    public boolean cleanWitherDoor = true;

    // chat enhancements
    public boolean timestamps = true;
    public boolean compactChat = false;
    public boolean clickableLinks = true;
    public boolean copyOnRightClick = true;
    public boolean mentionAlert = true;
    public boolean containerChat = false;

    // spam filter — lines containing any of these are hidden
    public List<String> spamFilters = Arrays.asList(
        "[BOSS] The Watcher",
        "blessing of",
        "MILESTONE",
        "SALVAGE",
        "Kill Combo",
        "Potion effects",
        "Your active Potion effects have been paused",
        "A Minecart with",
        "slow down",
        "no longer have an active"
    );

    // commands
    public boolean floorShortcuts = true;
    public boolean warpShortcuts = true;
    public boolean partyShortcuts = true;
    public boolean warpShortener = true; // /drag -> /warp drag, etc.

    // party triggers
    public boolean partyTriggers = true;
    public int triggerCooldownSec = 3;
    public boolean triggerSafeMode = false; // only leader/friends can trigger in safe mode
    public List<String> triggerWhitelist = new ArrayList<>();
}
