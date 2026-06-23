package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import net.minecraft.ChatFormatting;

import java.util.regex.Pattern;

/**
 * registers purse/bazaar/essence chat+tick listeners.
 * fields stay in LyraEconomy so HUD widgets don't break.
 * this is just the listener wiring extracted from the init method.
 */
public final class LyraTracker {

    private LyraTracker() {}

    private static final Pattern ESSENCE = Pattern.compile("([A-Za-z]+) Essence(?: x(\\d+))?");
    private static final Pattern BAZAAR = Pattern.compile("\\[Bazaar] (Bought|Sold|Order Flipped!)[^f]*for ([\\d,.]+) coins");

    public static void init(LyraConfig cfg, LyraEconomy host) {
        // purse tick reader
        ConstellationClient.tick().every(20, "lyra-purse", host::readPurse);

        // essence gain from chat
        if (cfg.essenceShopHelper) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
                if (overlay || !ConstellationClient.loc().onHypixel()) return;
                var em = ESSENCE.matcher(ChatFormatting.stripFormatting(msg.getString()));
                if (em.find()) {
                    int amt = em.group(2) == null ? 1 : Integer.parseInt(em.group(2));
                    host.essenceType = em.group(1);
                    host.essenceSession += amt;
                    host.essenceAt = System.currentTimeMillis();
                }
            });
        }

        // bazaar tx logger from chat
        if (cfg.bazaarUndercutAlert) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
                if (overlay || !ConstellationClient.loc().onHypixel()) return;
                String s = ChatFormatting.stripFormatting(msg.getString());
                var bm = BAZAAR.matcher(s);
                if (bm.find()) {
                    long coins = (long) Double.parseDouble(bm.group(2).replace(",", ""));
                    if (bm.group(1).startsWith("Sold")) host.bazaarSold += coins;
                    else if (bm.group(1).startsWith("Bought")) host.bazaarSpent += coins;
                    host.bazaarAt = System.currentTimeMillis();
                }
            });
        }
    }
}
