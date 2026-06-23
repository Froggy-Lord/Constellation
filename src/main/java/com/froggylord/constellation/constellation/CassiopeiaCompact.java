package com.froggylord.constellation.constellation;

import com.froggylord.constellation.config.CassiopeiaConfig;
import com.froggylord.constellation.chat.ChatPipeline;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * compact chat pipeline — trims redundant hypixel messages so chat is
 * actually readable. extracted from CassiopeiaChat to keep it from
 * becoming a 600-line god-class. one class, one job.
 *
 * this is the template for Phase 5a refactoring — each pipeline stage
 * gets its own file, registered from CassiopeiaChat.init().
 */
public final class CassiopeiaCompact {

    private CassiopeiaCompact() {}

    public static void init(CassiopeiaConfig cfg, ChatPipeline pipeline) {
        if (cfg == null) return;

        // hide potion effect spam — "Your active Potion effects have been paused..."
        if (cfg.compactPotionMessages) {
            pipeline.modify(msg -> {
                String s = msg.getString();
                if (s.contains("Potion effects") || s.contains("Your active")) return null;
                return msg;
            });
        }

        // shorten numbers like 1,234,567 → 1.23M
        if (cfg.shortenCoins) {
            var COIN = java.util.regex.Pattern.compile("[\\d,]{4,}");
            pipeline.modify(msg -> {
                String s = msg.getString();
                if (s.length() < 10) return msg;
                String rep = COIN.matcher(s).replaceAll(mr -> {
                    try {
                        long n = Long.parseLong(mr.group().replace(",", ""));
                        if (n < 10000) return mr.group();
                        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
                        return String.format("%.2fM", n / 1_000_000.0);
                    } catch (Exception e) { return mr.group(); }
                });
                return rep.equals(s) ? msg : Component.literal(rep);
            });
        }

        // compact bestiary milestone announcements
        if (cfg.compactBestiary) {
            pipeline.modify(msg -> {
                String s = msg.getString();
                if (s.contains("Bestiary") && (s.contains("+") || s.contains("%"))) return null;
                return msg;
            });
        }

        // compact jacob's contest reward claims
        if (cfg.compactJacobClaim) {
            pipeline.modify(msg -> {
                String s = ChatFormatting.stripFormatting(msg.getString());
                if (s.contains("Jacob") && s.contains("Contest") && (s.contains("claimed") || s.contains("reward")))
                    return Component.literal("§e🏆 Jacob's Contest rewards claimed!");
                return msg;
            });
        }

        // give rare drops a cleaner prefix
        if (cfg.rareDropFormat) {
            pipeline.modify(msg -> {
                String s = msg.getString();
                if (s.contains("RARE DROP") || s.contains("CRAZY RARE DROP") || s.contains("PET DROP"))
                    return Component.literal("§5§l★ §r" + s);
                return msg;
            });
        }
    }
}
