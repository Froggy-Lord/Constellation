package com.froggylord.constellation.config;

public class LyraConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean purseHud = true;
    public boolean coinSession = true;
    public boolean bitsHud = false;
    public boolean quiverHud = false;

    // compact text drawn on the item...
    public boolean slotText = true;
    public boolean slotTextPetLevel = true;
    public boolean slotTextStars = true;
    public boolean slotTextCakeYear = true;

    
    public boolean tooltipBazaar = true;

    
    public boolean tooltipSkyblockId = true;
    public boolean tooltipReforge = true;
    public boolean tooltipHotPotato = true;
    public boolean tooltipStars = true;
    public boolean tooltipEnchantCount = true;
    public boolean tooltipRecomb = true;
    public boolean tooltipMissingEnchants = true;
    public boolean backpackPreview = true; 
    public boolean tooltipAttributes = true; 
    public boolean tooltipSalvageable = true; 
    public boolean profileCommand = true; 
    public boolean auctionOutbidAlert = true; 
    public boolean auctionSoldAlert = true;   // highlight sold/expired message...
    public boolean tooltipAttributeShards = true; 
    public boolean bazaarUndercutAlert = true; 
    public boolean bazaarBookmarks = true; 
    public boolean tooltipItemQuality = true; 
    public boolean accessoryDisplay = true;   
    public boolean bazaarPriceHistory = true; 
    public boolean auctionPriceCompare = true; // compare ah prices against baza...
    public boolean inventoryValueHud = true;   
    public boolean essenceShopHelper = true;
    public boolean salvageHelper = true;       // highlight items safe to salvage
}
