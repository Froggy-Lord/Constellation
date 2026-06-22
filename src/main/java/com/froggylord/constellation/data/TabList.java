package com.froggylord.constellation.data;

import com.froggylord.constellation.mixin.PlayerTabOverlayAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TabList {

    private TabList() {}

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener net = mc.getConnection();
        if (net == null) return out;
        List<PlayerInfo> sorted;
        try {
            sorted = net.getOnlinePlayers().stream()
                .sorted(PlayerTabOverlayAccessor.constellation$ordering())
                .toList();
        } catch (Exception e) {
            return out;
        }
        for (PlayerInfo info : sorted) {
            Component name = info.getTabListDisplayName();
            if (name == null) continue;
            String s = ChatFormatting.stripFormatting(name.getString());
            if (s == null) continue;
            out.add(s.trim());
        }
        return out;
    }

    public static Matcher find(List<String> lines, Pattern pattern) {
        for (String line : lines) {
            Matcher m = pattern.matcher(line);
            if (m.find()) return m;
        }
        return null;
    }
}
