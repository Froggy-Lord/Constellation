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

    // item tooltips (read straight off the item's hidden SkyBlock data, no API needed)
    public boolean tooltipSkyblockId = true;
    public boolean tooltipReforge = true;
    public boolean tooltipHotPotato = true;
    public boolean tooltipStars = true;
    public boolean tooltipEnchantCount = true;
    public boolean tooltipRecomb = true;
}
