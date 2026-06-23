package com.froggylord.constellation.api;

import java.util.Map;

/**
 * single entry point for item pricing — tries lowest BIN first, falls back to
 * bazaar, then npc sell price, then 0. always call this instead of querying
 * BazaarApi or AuctionApi directly.
 */
public final class PriceProvider {

    private PriceProvider() {}

    private static final Map<String, Double> NPC = Map.ofEntries(
        Map.entry("ENCHANTED_BREAD", 60.0),
        Map.entry("ENCHANTED_SUGAR", 160.0),
        Map.entry("ENCHANTED_RAW_CHICKEN", 640.0),
        Map.entry("ENCHANTED_PORK", 1600.0),
        Map.entry("ENCHANTED_RAW_RABBIT", 320.0),
        Map.entry("ENCHANTED_MUTTON", 1600.0),
        Map.entry("ENCHANTED_COOKIE", 500.0),
        Map.entry("ENCHANTED_CAKE", 2500.0),
        Map.entry("ENCHANTED_CARROT", 160.0),
        Map.entry("ENCHANTED_POTATO", 160.0),
        Map.entry("ENCHANTED_PUMPKIN", 320.0),
        Map.entry("ENCHANTED_MELON", 160.0),
        Map.entry("ENCHANTED_CACTUS_GREEN", 480.0),
        Map.entry("ENCHANTED_COCOA", 480.0),
        Map.entry("ENCHANTED_CACTUS", 640.0),
        Map.entry("ENCHANTED_RED_MUSHROOM", 160.0),
        Map.entry("ENCHANTED_BROWN_MUSHROOM", 160.0),
        Map.entry("ENCHANTED_NETHER_STALK", 480.0),
        Map.entry("ENCHANTED_SEEDS", 160.0),
        Map.entry("ENCHANTED_COAL", 160.0),
        Map.entry("ENCHANTED_IRON", 160.0),
        Map.entry("ENCHANTED_GOLD", 320.0),
        Map.entry("ENCHANTED_DIAMOND", 1280.0),
        Map.entry("ENCHANTED_LAPIS_LAZULI", 480.0),
        Map.entry("ENCHANTED_EMERALD", 960.0),
        Map.entry("ENCHANTED_REDSTONE", 480.0),
        Map.entry("ENCHANTED_QUARTZ", 640.0),
        Map.entry("ENCHANTED_OBSIDIAN", 1920.0),
        Map.entry("ENCHANTED_GLOWSTONE_DUST", 640.0),
        Map.entry("ENCHANTED_GRAVEL", 320.0),
        Map.entry("ENCHANTED_ICE", 320.0),
        Map.entry("ENCHANTED_SAND", 640.0),
        Map.entry("ENCHANTED_ENDSTONE", 960.0),
        Map.entry("ENCHANTED_SLIME_BALL", 640.0),
        Map.entry("ENCHANTED_SLIME_BLOCK", 5000.0),
        Map.entry("ENCHANTED_CLAY_BALL", 480.0),
        Map.entry("ENCHANTED_FLINT", 640.0),
        Map.entry("ENCHANTED_ROTTEN_FLESH", 320.0),
        Map.entry("ENCHANTED_BONE", 320.0),
        Map.entry("ENCHANTED_STRING", 320.0),
        Map.entry("ENCHANTED_SPIDER_EYE", 640.0),
        Map.entry("ENCHANTED_GUNPOWDER", 640.0),
        Map.entry("ENCHANTED_FEATHER", 320.0),
        Map.entry("ENCHANTED_LEATHER", 640.0),
        Map.entry("ENCHANTED_RABBIT_HIDE", 1280.0),
        Map.entry("ENCHANTED_BLAZE_POWDER", 640.0),
        Map.entry("ENCHANTED_MAGMA_CREAM", 320.0),
        Map.entry("ENCHANTED_ENDER_PEARL", 320.0),
        Map.entry("ENCHANTED_GHAST_TEAR", 640.0),
        Map.entry("ENCHANTED_EYE_OF_ENDER", 1920.0),
        Map.entry("ENCHANTED_BLAZE_ROD", 1280.0),
        Map.entry("ENCHANTED_HUGE_MUSHROOM_1", 10000.0),
        Map.entry("ENCHANTED_HUGE_MUSHROOM_2", 10000.0),
        Map.entry("ENCHANTED_POISONOUS_POTATO", 160.0),
        Map.entry("ENCHANTED_PAPER", 320.0)
    );

    /** best known price per item — LBIN > bazaar buy > bazaar sell > npc > 0 */
    public static double value(String itemId) {
        // 1. lowest BIN (auction house)
        Double lbin = AuctionApi.getLbin(itemId);
        if (lbin != null && lbin > 0) return lbin;

        // 2. bazaar
        double[] bz = BazaarApi.get(itemId);
        if (bz != null) {
            if (bz[0] > 0) return bz[0]; // instant buy
            if (bz[1] > 0) return bz[1]; // instant sell
        }

        // 3. npc
        Double npc = NPC.get(itemId);
        if (npc != null && npc > 0) return npc;

        return 0;
    }

    /** trigger an async fetch for an item if we don't have recent LBIN data */
    public static void warm(String itemId) {
        if (AuctionApi.getLbin(itemId) == null) {
            AuctionApi.prefetch(itemId);
        }
    }

    public static int npcEntries() { return NPC.size(); }
}
