package com.froggylord.constellation.config;

import java.util.*;

public class CassiopeiaConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    // chat cleaner — per-category to...
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
    public boolean cleanEmpty = true;
    public boolean cleanWarping = true;
    public boolean cleanWelcome = true;
    public boolean cleanGuildExp = true;
    public boolean cleanFriendJoin = true;
    public boolean cleanWinterGift = true;
    public boolean cleanWatchdog = true;
    public boolean cleanProfileJoin = true;
    public boolean cleanFireSale = true;
    public boolean cleanDiana = true;
    public boolean cleanHoppity = true;
    public boolean cleanSacrifice = true;
    public boolean cleanParkour = true;
    public boolean cleanTeleportPads = true;
    public boolean cleanAds = true;
    public boolean cleanShowOff = true;
    public boolean cleanAutopet = true;
    public boolean cleanCombo = true;
    public boolean cleanMimic = true;
    public boolean cleanDeath = true;
    public boolean cleanHeal = true;
    public boolean cleanAOTE = true;
    public boolean cleanImplosion = true;
    public boolean cleanAbilityCooldown = true;
    public boolean autoGG = true; // local completion reminder only
    public boolean autoTip = true;
    public boolean actionBarCleaner = false;

    
    public boolean timestamps = true;
    public boolean compactChat = false;
    public boolean clickableLinks = true;
    public boolean copyOnRightClick = true;
    public boolean mentionAlert = true;
    public boolean containerChat = false;
    public boolean shortenCoins = true;
    public boolean compactPotionMessages = true;
    public boolean compactBestiary = true;
    public boolean rareDropFormat = true;
    public boolean compactJacobClaim = true;

    // spam filter — lines containing...
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

    
    public boolean floorShortcuts = true;
    public boolean warpShortcuts = true;
    public boolean partyShortcuts = true;
    public boolean warpShortener = true; // /drag -> /warp drag, etc

    
    public boolean partyTriggers = true;
    public int triggerCooldownSec = 3;
    public boolean triggerSafeMode = false;
    public List<String> triggerWhitelist = new ArrayList<>();
}
