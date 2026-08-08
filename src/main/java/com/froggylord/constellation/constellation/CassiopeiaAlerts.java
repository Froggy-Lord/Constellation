package com.froggylord.constellation.constellation;

import com.froggylord.constellation.config.CassiopeiaConfig;
import com.froggylord.constellation.chat.ChatPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * chat alerts — mention pings, timestamps, clickable links, auto-gg,
 * inventory full warning, sea creature spawn alert.
 * extracted from CassiopeiaChat for Phase 5a splitting.
 */
public final class CassiopeiaAlerts {

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE);

    private CassiopeiaAlerts() {}

    public static void init(CassiopeiaConfig cfg, ChatPipeline pipeline) {
        if (cfg == null) return;

        // mention alert — ping when someone says your name
        pipeline.onGame(msg -> {
            if (!cfg.mentionAlert) return;
            String s = msg.getString().toLowerCase(Locale.ROOT);
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            String name = mc.player.getName().getString().toLowerCase(Locale.ROOT);
            if (!name.isEmpty() && s.contains(name)) {
                mc.player.sendOverlayMessage(Component.literal("§eMentioned in chat"));
                mc.player.playSound(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 0.6f, 1.2f);
            }
        });

        // timestamps — dim [HH:MM] prefix on every message
        var fmt = DateTimeFormatter.ofPattern("HH:mm");
        pipeline.modify(msg -> {
            if (!cfg.timestamps) return msg;
            String time = "§7[" + LocalTime.now().format(fmt) + "]§r ";
            return Component.literal(time).append(msg);
        });

        // clickable links — make http/https urls blue and underlined
        pipeline.modify(msg -> {
            if (!cfg.clickableLinks) return msg;
            if (!URL.matcher(msg.getString()).find()) return msg;
            return linkUrls(msg);
        });

        // ported from devonian (GPL-3.0): features/dungeons/AutoRequeueDungeons.kt
        // completion reminder only, never sends chat for the player
        pipeline.onGame(msg -> {
            if (!cfg.autoGG || !msg.getString().trim().equals("> EXTRA STATS <")) return;
            var mc = Minecraft.getInstance();
            if (mc.player != null)
                mc.player.sendSystemMessage(Component.literal("§aRun complete. Say gg when ready."));
        });

        // inventory full warning
        pipeline.onGame(msg -> {
            String s = msg.getString();
            if (s.contains("Your inventory is full") || s.contains("cannot fit") || s.contains("inventory full")) {
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§cINVENTORY FULL!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.5f);
                }
            }
        });

        // legendary sea creature spawn alert (always on — config is in HydraConfig)
        pipeline.onGame(msg -> {
                String s = msg.getString();
                if (s.contains("A legendary Sea Creature has spawned") || s.contains("Legendary Sea Creature")) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.hud.resetTitleTimes();
                        mc.gui.hud.setTitle(Component.literal("§bLEGENDARY SEA CREATURE!"));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 1.0f);
                    }
                }
            });
    }

    // ported from Skyblocker (LGPL-3.0): src/main/java/de/hysky/skyblocker/utils/TextTransformer.java
    public static Component linkUrls(Component message) {
        MutableComponent result = Component.empty();
        message.visit((style, text) -> {
            Matcher matcher = URL.matcher(text);
            int end = 0;
            while (matcher.find()) {
                if (matcher.start() > end) result.append(Component.literal(text.substring(end, matcher.start())).withStyle(style));
                String raw = matcher.group();
                int keep = raw.length();
                while (keep > 0 && ".,;:!?)]}".indexOf(raw.charAt(keep - 1)) >= 0) keep--;
                String link = raw.substring(0, keep);
                try {
                    Style linkStyle = style.withColor(ChatFormatting.BLUE).withUnderlined(true);
                    if (style.getClickEvent() == null) linkStyle = linkStyle.withClickEvent(new ClickEvent.OpenUrl(URI.create(link)));
                    result.append(Component.literal(link).withStyle(linkStyle));
                } catch (IllegalArgumentException ignored) {
                    result.append(Component.literal(link).withStyle(style));
                }
                if (keep < raw.length()) result.append(Component.literal(raw.substring(keep)).withStyle(style));
                end = matcher.end();
            }
            if (end < text.length()) result.append(Component.literal(text.substring(end)).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
