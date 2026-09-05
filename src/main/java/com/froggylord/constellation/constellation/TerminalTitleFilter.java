package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;

/** Exact packet-local filter for the repetitive F7/M7 Goldor completion title. */
public final class TerminalTitleFilter {
    private TerminalTitleFilter() {}

    public static boolean shouldHide(Component component, boolean subtitle) {
        OrionConfig cfg = ConstellationClient.cfg().orion;
        if (cfg == null || !cfg.enabled || !cfg.terminalHideCompletion) return false;
        if (subtitle ? !cfg.terminalCompletionFilterSubtitles : !cfg.terminalCompletionFilterTitles) return false;
        if (!TerminalBreakdown.isActive()) return false;

        String clean = ChatFormatting.stripFormatting(component.getString()).trim();
        Matcher match = TerminalBreakdown.completionMatcher(clean);
        if (!match.matches()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (cfg.terminalCompletionOnlyOwn && mc.player != null
            && match.group(1).equalsIgnoreCase(mc.player.getGameProfile().name())) return false;
        return true;
    }

}
