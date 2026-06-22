package com.froggylord.constellation.config;

/** Config for the Lyra constellation (economy + inventory). */
public class LyraConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean purseHud = true;
    public boolean coinSession = true;
    public boolean bitsHud = false;
    public boolean quiverHud = false;

    // compact text drawn on the item itself in inventories
    public boolean slotText = true;
    public boolean slotTextPetLevel = true;
    public boolean slotTextStars = true;
    public boolean slotTextCakeYear = true;

    // live bazaar buy/sell in tooltips (public Hypixel feed, no key)
    public boolean tooltipBazaar = true;

    // item tooltips (read straight off the item's hidden SkyBlock data, no API needed)
    public boolean tooltipSkyblockId = true;
    public boolean tooltipReforge = true;
    public boolean tooltipHotPotato = true;
    public boolean tooltipStars = true;
    public boolean tooltipEnchantCount = true;
    public boolean tooltipRecomb = true;
    public boolean tooltipMissingEnchants = true;
    public boolean backpackPreview = true; // shift-hover shows contents
    public boolean tooltipAttributes = true; // show item attributes (Mana Pool, Breeze, etc.)
    public boolean tooltipSalvageable = true; // mark museum-donated items safe to salvage
    public boolean profileCommand = true; // /profile shows quick stats summary
    public boolean auctionOutbidAlert = true; // highlight outbid messages (cmp. Skyblocker)
    public boolean auctionSoldAlert = true;   // highlight sold/expired messages (cmp. Skyblocker)
    public boolean tooltipAttributeShards = true; // show attribute shard details (cmp. Skyblocker)
    public boolean bazaarUndercutAlert = true; // alert when your bazaar offer gets undercut (cmp. Skyblocker)
    public boolean bazaarBookmarks = true; // save + recall favourite bazaar items
    public boolean tooltipItemQuality = true; // show item quality (50/50, etc.) — cmp. Firmament
    public boolean accessoryDisplay = true;   // show accessory bag slots from sidebar (cmp. Skyblocker)
}
