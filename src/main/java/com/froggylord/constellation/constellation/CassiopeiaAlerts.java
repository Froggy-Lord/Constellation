package com.froggylord.constellation.constellation;

import com.froggylord.constellation.config.CassiopeiaConfig;
import com.froggylord.constellation.chat.ChatPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * chat alerts — mention pings, timestamps, clickable links, auto-gg,
 * inventory full warning, sea creature spawn alert.
 * extracted from CassiopeiaChat for Phase 5a splitting.
 */
public final class CassiopeiaAlerts {

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
            String s = msg.getString();
            if (!s.contains("http://") && !s.contains("https://")) return msg;
            s = s.replaceAll("(https?://[^\\s]+)", "§9$1§r");
            return Component.literal(s);
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
}
