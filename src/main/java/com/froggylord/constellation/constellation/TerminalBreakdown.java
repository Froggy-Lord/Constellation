package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.DungeonState;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Devonian (GPL-3.0): features/dungeons/f7/TerminalBreakdown.kt
public final class TerminalBreakdown {
    // ported from Devonian (GPL-3.0): api/dungeon/Stages.kt (TerminalSection)
    private static final Pattern COMPLETION = Pattern.compile(
        "^(\\w{1,16}) (?:activated a (terminal|lever)|completed a (device))! \\((\\d+)/(\\d+)\\)$");
    private static final int[] EXPECTED_TERMINALS = {4, 5, 4, 4};
    private static final Comparator<TermData> ORDER = Comparator
        .comparingInt((TermData data) -> -data.terminals)
        .thenComparingInt(data -> -data.devices)
        .thenComparingInt(data -> -data.levers)
        .thenComparing(data -> data.name);
    private static final Map<String, TermData> DATA = new LinkedHashMap<>();
    private static final Section[] SECTIONS = {
        new Section(0), new Section(1), new Section(2), new Section(3)
    };
    private static int currentSection = -1;
    private static boolean phaseActive;
    private static boolean initialized;
    private static long phaseStartedNanos;
    private static long lastGateNanos;
    private static final List<Long> terminalTimes = new ArrayList<>();
    private static List<Long> lastTerminalTimes = List.of();

    private TerminalBreakdown() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConstellationClient.bus().subscribe(DungeonState.DungeonEnter.class, ignored -> reset(true));
        ConstellationClient.bus().subscribe(DungeonState.DungeonStart.class, ignored -> reset(true));
        ConstellationClient.bus().subscribe(DungeonState.FloorChange.class, event -> {
            if (event.current() == null || !event.current().endsWith("7")) reset(true);
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(message.getString());
            return true;
        });
    }

    private static void onChat(String message) {
        if (!isF7()) return;
        if (message.equals("[BOSS] Goldor: Who dares trespass into my domain?")) {
            startPhase(true);
            return;
        }
        if (message.equals("The Core entrance is opening!")) {
            if (!phaseActive) return;
            captureForRun();
            if (ConstellationClient.cfg().orion.terminalBreakdown) show();
            reset(false);
            return;
        }
        if (message.equals("The gate has been destroyed!")) {
            if (!phaseActive) return;
            long now = System.nanoTime();
            if (lastGateNanos != 0 && now - lastGateNanos < 1_000_000_000L) return;
            lastGateNanos = now;
            Section section = current();
            if (section != null) {
                section.gateDestroyed = true;
                advanceIfDone();
            }
            return;
        }

        Matcher match = COMPLETION.matcher(message);
        if (!match.matches()) return;
        if (!phaseActive) startPhase(false);
        String type = match.group(2) == null ? match.group(3) : match.group(2);
        Completion completion = new Completion(match.group(1), type,
            Integer.parseInt(match.group(4)), Integer.parseInt(match.group(5)));
        if (!apply(completion)) return;

        if (phaseStartedNanos != 0) {
            terminalTimes.add((System.nanoTime() - phaseStartedNanos) / 1_000_000L);
        }
        TermData data = DATA.computeIfAbsent(completion.player, TermData::new);
        switch (completion.type) {
            case "terminal" -> data.terminals++;
            case "lever" -> data.levers++;
            case "device" -> data.devices++;
        }
        advanceIfDone();
    }

    private static void startPhase(boolean timingKnown) {
        reset(false);
        phaseActive = true;
        currentSection = 0;
        phaseStartedNanos = timingKnown ? System.nanoTime() : 0;
    }

    // ported from Devonian (GPL-3.0): api/dungeon/Stages.kt (TerminalSection.onChat)
    private static boolean apply(Completion completion) {
        Section section = current();
        if (section == null) return false;
        if (completion.index == section.lastIndex) {
            if (completion.player.equals(section.lastPlayer)) return false;
            if (completion.type.equals("device")) {
                switch (currentSection) {
                    case 0 -> {
                        if (!SECTIONS[3].deviceDone) SECTIONS[3].deviceDone = true;
                        else if (!SECTIONS[1].deviceDone) SECTIONS[1].deviceDone = true;
                        else SECTIONS[2].deviceDone = true;
                    }
                    case 1 -> {
                        if (!SECTIONS[2].deviceDone) SECTIONS[2].deviceDone = true;
                        else SECTIONS[3].deviceDone = true;
                    }
                    case 2 -> SECTIONS[3].deviceDone = true;
                    default -> { }
                }
                return true;
            } else if (section.lastType.equals("device")) {
                section.deviceDone = false;
            }
        } else if (completion.index == 2 && section.lastIndex == 0) {
            section.deviceDone = true;
        } else if (completion.index == 1 && !completion.type.equals("device")) {
            section.deviceDone = false;
        }

        switch (completion.type) {
            case "terminal" -> section.terminalsDone++;
            case "lever" -> section.leversDone++;
            case "device" -> {
                if (section.deviceDone && currentSection == 1) SECTIONS[3].deviceDone = true;
                section.deviceDone = true;
            }
            default -> { return false; }
        }
        section.lastPlayer = completion.player;
        section.lastIndex = completion.index;
        section.lastType = completion.type;
        return true;
    }

    private static void advanceIfDone() {
        Section section = current();
        if (section == null || !section.complete() || currentSection >= 3) return;
        currentSection++;
    }

    // ported from Devonian (GPL-3.0): features/dungeons/f7/TerminalDisplay.kt
    public static String hudText() {
        var cfg = ConstellationClient.cfg().orion;
        Section section = current();
        if (!cfg.terminalDisplay || !phaseActive || !isF7() || section == null) return null;
        String prefix = cfg.terminalDisplayShowSection ? "§6S" + (currentSection + 1) + " §r" : "";
        if (cfg.terminalDisplaySimple) {
            int done = section.terminalsDone + section.leversDone + (section.deviceDone ? 1 : 0);
            return prefix + (section.gateDestroyed ? "§a" : "§c") + done + "/" + (section.expectedTerminals + 3);
        }
        return prefix + colour(section.terminalsDone, section.expectedTerminals) + "Terms: "
            + section.terminalsDone + "/" + section.expectedTerminals
            + " §7| " + colour(section.leversDone, 2) + "Levers: " + section.leversDone + "/2"
            + " §7| " + (section.deviceDone ? "§aDevice: yes" : "§cDevice: no")
            + " §7| " + (section.gateDestroyed ? "§aGate: yes" : "§cGate: no");
    }

    public static boolean isActive() {
        return phaseActive && isF7();
    }

    public static int currentSectionIndex() {
        return currentSection;
    }

    public static Matcher completionMatcher(String message) {
        return COMPLETION.matcher(message);
    }

    private static String colour(int value, int expected) {
        return value >= expected ? "§a" : value == 0 ? "§c" : "§e";
    }

    private static Section current() {
        return currentSection >= 0 && currentSection < SECTIONS.length ? SECTIONS[currentSection] : null;
    }

    private static void reset(boolean clearLast) {
        DATA.clear();
        for (Section section : SECTIONS) section.reset();
        currentSection = -1;
        phaseActive = false;
        phaseStartedNanos = 0;
        lastGateNanos = 0;
        terminalTimes.clear();
        if (clearLast) lastTerminalTimes = List.of();
    }

    public static void captureForRun() { lastTerminalTimes = List.copyOf(terminalTimes); }
    public static List<Long> lastTerminalTimes() { return List.copyOf(lastTerminalTimes); }

    private static void show() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || DATA.isEmpty()) return;
        Map<String, TermData> all = new LinkedHashMap<>(DATA);
        for (DungeonState.Teammate teammate : ConstellationClient.dungeon().teammates()) {
            all.computeIfAbsent(teammate.name(), TermData::new);
        }
        List<TermData> sorted = new ArrayList<>(all.values());
        sorted.sort(ORDER);
        mc.player.sendSystemMessage(Component.literal("§fTerminal Breakdown:"));
        for (TermData data : sorted) {
            mc.player.sendSystemMessage(Component.literal("§b" + data.name
                + "§f: Terms §a" + data.terminals
                + " §7| §fDevices §a" + data.devices
                + " §7| §fLevers §a" + data.levers));
        }
    }

    private static boolean isF7() {
        String floor = ConstellationClient.dungeon().floor();
        return ConstellationClient.loc().inDungeons() && ConstellationClient.dungeon().inBoss()
            && floor != null && floor.endsWith("7");
    }

    private record Completion(String player, String type, int index, int total) {}

    private static final class Section {
        private final int expectedTerminals;
        private int terminalsDone;
        private int leversDone;
        private boolean deviceDone;
        private boolean gateDestroyed;
        private String lastPlayer = "";
        private int lastIndex;
        private String lastType = "";

        private Section(int index) {
            expectedTerminals = EXPECTED_TERMINALS[index];
            gateDestroyed = index == 3;
        }

        private void reset() {
            terminalsDone = 0;
            leversDone = 0;
            deviceDone = false;
            gateDestroyed = expectedTerminals == EXPECTED_TERMINALS[3] && this == SECTIONS[3];
            lastPlayer = "";
            lastIndex = 0;
            lastType = "";
        }

        private boolean complete() {
            return terminalsDone >= expectedTerminals && leversDone >= 2 && deviceDone && gateDestroyed;
        }
    }

    private static final class TermData {
        private final String name;
        private int terminals;
        private int levers;
        private int devices;

        private TermData(String name) { this.name = name; }
    }
}
