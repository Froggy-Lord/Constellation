package com.froggylord.constellation.constellation;

import com.froggylord.constellation.config.CassiopeiaConfig;
import com.froggylord.constellation.chat.ChatPipeline;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * compact chat pipeline — trims redundant hypixel messages so chat is
 * actually readable. extracted from CassiopeiaChat to keep it from
 * becoming a 600-line god-class. one class, one job.
 *
 * this is the template for Phase 5a refactoring — each pipeline stage
 * gets its own file, registered from CassiopeiaChat.init().
 */
public final class CassiopeiaCompact {

    private static final Pattern COIN = Pattern.compile("[\\d,]{4,}");

    private CassiopeiaCompact() {}

    public static void init(CassiopeiaConfig cfg, ChatPipeline pipeline) {
        if (cfg == null) return;

        // hide potion effect spam — "Your active Potion effects have been paused..."
        pipeline.modify(msg -> {
            if (!cfg.compactPotionMessages) return msg;
            String s = msg.getString();
            if (s.contains("Potion effects") || s.contains("Your active")) return null;
            return msg;
        });

        // shorten numbers like 1,234,567 → 1.23M
        pipeline.modify(msg -> cfg.shortenCoins ? shortenNumbers(msg) : msg);

        // compact bestiary milestone announcements
        pipeline.modify(msg -> {
            if (!cfg.compactBestiary) return msg;
            String s = msg.getString();
            if (s.contains("Bestiary") && (s.contains("+") || s.contains("%"))) return null;
            return msg;
        });

        // compact jacob's contest reward claims
        pipeline.modify(msg -> {
            if (!cfg.compactJacobClaim) return msg;
            String s = ChatFormatting.stripFormatting(msg.getString());
            if (s.contains("Jacob") && s.contains("Contest") && (s.contains("claimed") || s.contains("reward")))
                return Component.literal("Jacob's Contest rewards claimed.").withStyle(ChatFormatting.YELLOW);
            return msg;
        });

        // give rare drops a cleaner prefix
        pipeline.modify(msg -> {
            if (!cfg.rareDropFormat) return msg;
            String s = msg.getString();
            if (s.contains("RARE DROP") || s.contains("CRAZY RARE DROP") || s.contains("PET DROP"))
                return Component.literal("[Rare] ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD).append(msg);
            return msg;
        });
    }

    // ported from Skyblocker (LGPL-3.0): src/main/java/de/hysky/skyblocker/utils/TextTransformer.java
    public static Component shortenNumbers(Component message) {
        if (!COIN.matcher(message.getString()).find()) return message;
        MutableComponent result = Component.empty();
        message.visit((style, text) -> {
            Matcher matcher = COIN.matcher(text);
            int end = 0;
            while (matcher.find()) {
                if (matcher.start() > end) result.append(Component.literal(text.substring(end, matcher.start())).withStyle(style));
                result.append(Component.literal(shortNumber(matcher.group())).withStyle(style));
                end = matcher.end();
            }
            if (end < text.length()) result.append(Component.literal(text.substring(end)).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static String shortNumber(String raw) {
        try {
            long n = Long.parseLong(raw.replace(",", ""));
            if (n < 10_000) return raw;
            if (n < 1_000_000) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
            return String.format(Locale.ROOT, "%.2fM", n / 1_000_000.0);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }
}
