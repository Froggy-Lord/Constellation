package com.froggylord.constellation.config;

import java.util.ArrayList;
import java.util.List;

public class LyraConfig extends BaseConfigGroup {

    { enabled = false; }
    @Override public int currentVersion() { return 0; }

    public boolean purseHud = true;
    public boolean coinSession = true;
    public boolean coinSessionRate = true;
    public boolean coinRecentChange = true;
    public boolean coinSessionResetOnConnect = true;
    public boolean coinHudCompact = false;
    public int coinChangeHoldTicks = 60;
    public int coinHudDecimals = 1;
    public String purseHudStyle = "Purse: §6{purse}";
    public String coinSessionStyle = "Session: {color}{change}";
    public String coinRateStyle = "Rate: {color}{rate}/h";
    public String coinChangeStyle = "Recent: {color}{change}";
    public String bitsHudStyle = "Bits: §b{bits}";
    public boolean bitsHud = false;
    public boolean quiverHud = false;

    // compact text drawn on the item...
    public boolean slotText = true;
    public boolean slotTextPetLevel = true;
    public boolean slotTextStars = true;
    public boolean slotTextCakeYear = true;
    public boolean slotTextEnchantLevel = true;
    public boolean slotTextPotionLevel = true;
    public boolean slotTextMinionLevel = true;
    public boolean slotTextRancherSpeed = true;
    public boolean slotTextScaleToFit = true;

    public boolean inventorySearch = true;
    public boolean inventorySearchLore = true;
    public boolean inventorySearchId = true;
    public boolean inventorySearchIgnoreCase = true;
    public boolean inventorySearchDimNonMatches = true;
    public boolean inventorySearchHighlightMatches = false;
    public boolean inventorySearchPlayerSlots = true;
    public boolean inventorySearchContainerSlots = true;
    public boolean inventorySearchClickablePrompt = true;
    public boolean inventorySearchCtrlK = false;
    public boolean inventorySearchRememberQuery = true;
    public boolean inventorySearchCalculator = true;
    public int inventorySearchDimColor = 0xB0000000;
    public int inventorySearchHighlightColor = 0x8055FF55;

    public boolean inventoryButtons = true;
    public boolean inventoryButtonsTop = true;
    public boolean inventoryButtonsBottom = true;
    public boolean inventoryButtonsShowTooltips = true;
    public boolean inventoryButtonsHighlightCurrent = true;
    public boolean inventoryButtonsHoverAnimation = true;
    public boolean inventoryButtonsCloseAfterCommand = true;
    public boolean inventoryButtonsOnlyPlayerInventory = false;
    public boolean inventoryButtonsHideInCreative = false;
    public int inventoryButtonsSize = 26;
    public int inventoryButtonsGap = 0;
    public int inventoryButtonsOffset = 8;
    public int inventoryButtonsTooltipDelayMs = 250;
    public int inventoryButtonsColor = 0xE61A1A28;
    public int inventoryButtonsHoverColor = 0xF0303050;
    public int inventoryButtonsHighlightColor = 0xF04A356A;
    public List<InventoryButtonEntry> inventoryButtonEntries = new ArrayList<>();

    public static class InventoryButtonEntry {
        public String icon = "minecraft:barrier";
        public String command = "";
        public String title = "";
        public String tooltip = "";
        public boolean enabled = true;

        public InventoryButtonEntry() {}
        public InventoryButtonEntry(String icon, String command, String title, String tooltip) {
            this.icon = icon;
            this.command = command;
            this.title = title;
            this.tooltip = tooltip;
        }
    }

    
    public boolean tooltipBazaar = true;
    public boolean tooltipPrices = true;
    public boolean tooltipLowestBin = true;
    public boolean tooltipNpcPrice = true;
    public boolean tooltipPriceLoading = true;
    public boolean tooltipStackBreakdown = true;
    public boolean tooltipItemInfo = true;
    public boolean tooltipObtainedDate = true;
    public int tooltipPriceDecimals = 1;

    
    public boolean tooltipSkyblockId = true;
    public boolean tooltipReforge = true;
    public boolean tooltipHotPotato = true;
    public boolean tooltipStars = true;
    public boolean tooltipEnchantCount = true;
    public boolean tooltipRecomb = true;
    public boolean tooltipMissingEnchants = true;
    public boolean backpackPreview = true;
    public boolean backpackPreviewWithoutShift = true;
    public boolean backpackPreviewPersist = true;
    public boolean backpackPreviewShowValue = true;
    public boolean backpackPreviewShowCount = true;
    public boolean backpackPreviewSlotText = true;
    public int backpackPreviewScalePercent = 100;
    public int backpackPreviewBackground = 0xF0101018;
    public boolean containerValue = true;
    public boolean containerValueButton = true;
    public boolean containerValueAutomatic = false;
    public boolean containerValueOwnInventory = false;
    public boolean containerValueInDungeons = false;
    public boolean containerValueShowBreakdown = true;
    public boolean containerValueShowIcons = true;
    public boolean containerValueHighlightSlots = true;
    public boolean containerValueCompact = true;
    public boolean containerValueAscending = false;
    public boolean containerValueIgnoreSoulbound = false;
    public boolean containerValueUseSellPrice = true;
    public int containerValueMaxItems = 10;
    public int containerValueHideBelow = 0;
    public int containerValueCompleteColor = 0xFF55FF55;
    public int containerValueIncompleteColor = 0xFF55AAFF;
    public boolean bazaarOrderHelper = true;
    public boolean bazaarQuickQuantities = true;
    public boolean bazaarQuickClipboard = true;
    public boolean bazaarQuickCloseOnUse = false;
    public int bazaarQuickQuantity1 = 32;
    public int bazaarQuickQuantity2 = 16;
    public int bazaarQuickQuantity3 = 71000;
    public boolean bazaarOrderStatus = true;
    public boolean bazaarOrderFilledMarker = true;
    public boolean bazaarOrderExpiredMarker = true;
    public boolean bazaarOrderExpiringMarker = true;
    public int bazaarFilledColor = 0xFF55FF55;
    public int bazaarPartialColor = 0xFFFFFF55;
    public int bazaarExpiredColor = 0xFFFF5555;
    public int bazaarExpiringColor = 0xFFFFAA00;
    public boolean bazaarOrderTracker = true;
    public boolean bazaarOrderTrackerShowAmount = true;
    public boolean bazaarOrderTrackerShowCount = true;
    public boolean bazaarReorderClipboard = true;
    public boolean tooltipAttributes = true; 
    public boolean tooltipSalvageable = true; 
    public boolean profileCommand = true; 
    public boolean auctionHelper = true;
    public boolean auctionOutbidAlert = true;
    public boolean auctionSoldAlert = true;
    public boolean tooltipAttributeShards = true; 
    public boolean bazaarUndercutAlert = true; 
    public boolean bazaarBookmarks = true; 
    public boolean tooltipItemQuality = true; 
    public boolean accessoryDisplay = true;   
    public boolean bazaarPriceHistory = true; 
    public boolean auctionPriceCompare = true;
    public boolean auctionPriceTooltip = true;
    public boolean auctionPriceHighlight = true;
    public boolean auctionAutoCopyPrice = true;
    public boolean auctionCopyOnlyCompleteEstimate = true;
    public boolean auctionProtectListings = true;
    public boolean auctionProtectPurchases = true;
    public boolean auctionUseIncompleteEstimate = false;
    public boolean auctionHighlightUnderbid = true;
    public boolean auctionHighlightOverpriced = true;
    public int auctionGoodPercent = 80;
    public int auctionVeryGoodPercent = 50;
    public int auctionBadPercent = 120;
    public int auctionVeryBadPercent = 200;
    public int auctionUnderbidPercent = 80;
    public int auctionOverbidPercent = 120;
    public int auctionMinimumDifference = 10000;
    public int auctionOverrideClicks = 3;
    public int auctionGoodColor = 0x8055FF55;
    public int auctionVeryGoodColor = 0x8055FFFF;
    public int auctionBadColor = 0x80FFAA00;
    public int auctionVeryBadColor = 0x80FF5555;
    public int auctionSoldColor = 0x8055FF55;
    public int auctionExpiredColor = 0x80FF5555;
    public int auctionUnderbidColor = 0x80FFAA00;
    public boolean inventoryValueHud = true;   
    public boolean essenceShopHelper = true;
    public boolean salvageHelper = true;
    public boolean exoticArmorIdentifier = true;
    public boolean trueHexDisplay = true;
    public boolean museumDonationStatus = true;
    public boolean missingAccessoryHelper = true;
}
