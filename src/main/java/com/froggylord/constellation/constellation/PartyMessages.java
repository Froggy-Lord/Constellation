package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PartyMessages {
    public record Definition(String id, String name, String category, String defaultTemplate, List<String> variables) {}

    private static final List<Definition> DEFINITIONS = List.of(
        def("party-composition", "Party composition", "Run", "{warnings}", "warnings"),
        def("score-270", "270 score", "Run", "{score} score at {time}", "score", "time", "floor"),
        def("score-300", "300 score", "Run", "{score} score at {time}", "score", "time", "floor"),
        def("mimic", "Mimic killed", "Clear", "Mimic Killed!"),
        def("prince", "Prince killed", "Clear", "Prince Killed!"),
        def("blaze-done", "Blaze complete", "Puzzles", "Blaze done"),
        def("ttt-done", "Tic Tac Toe complete", "Puzzles", "Tic Tac Toe done"),
        def("draft-used", "Architect Draft used", "Puzzles", "Used Draft to reset {puzzle}", "puzzle"),
        def("dragon-hits", "M7 dragon arrows", "Boss", "{dragon}: {hits} arrows in {time}s", "dragon", "hits", "time"),
        def("simon-progress", "Simon progress", "Devices", "SS {round}/{total}", "round", "total"),
        def("simon-broken", "Simon broken", "Devices", "SS Broke!"),
        def("simon-restarted", "Simon restarted", "Devices", "SS Started Again!"),
        def("melody-start", "Melody start", "Devices", "Melody Terminal start!"),
        def("melody-progress", "Melody progress", "Devices", "Melody {progress}%", "progress"),
        def("watcher-move", "Watcher movement", "Blood", "Watcher will move in {seconds}s", "seconds"),
        def("leap", "Spirit Leap", "Boss", "Leaped to {leaped-player}!", "leaped-player"),
        def("carry-progress", "Carry progress", "Carries", "{player}: {completed}/{total} {target}",
            "player", "target", "completed", "total", "paid", "expected", "price", "rate"),
        def("fishing-death", "Fishing boss death", "Fishing", "--> I was killed, please wait for me until I come back <--", "boss"),
        def("nessie-destination", "Nessie destination", "Fishing", "Nessie is swimming to the {destination} cave.", "destination"),
        def("trophy-frog-discovery", "Trophy Frog discovery", "Fishing", "FROG DISCOVERED! {details}", "details", "name", "grade", "type"),
        def("trophy-fish-discovery", "Trophy Fish discovery", "Fishing", "TROPHY FISH DISCOVERED! {details}", "details", "name", "grade", "type"),
        def("lootshare", "Lootshare call", "Fishing", "Lootshare!"),
        livid("red"), livid("yellow"), livid("lime"), livid("green"), livid("blue"),
        livid("magenta"), livid("purple"), livid("gray"), livid("white")
    );
    private static final Map<String, Definition> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Long> LAST_SENT = new HashMap<>();

    static { for (Definition definition : DEFINITIONS) BY_ID.put(definition.id(), definition); }

    private PartyMessages() {}

    public static List<Definition> definitions() { return DEFINITIONS; }

    public static boolean enabled(String id) {
        var cfg = ConstellationClient.cfg().orion;
        if (cfg != null && cfg.partyMessageEnabled == null) cfg.partyMessageEnabled = new HashMap<>();
        return cfg != null && cfg.partyMessageEnabled.getOrDefault(id, true);
    }

    public static void setEnabled(String id, boolean enabled) {
        if (ConstellationClient.cfg().orion.partyMessageEnabled == null)
            ConstellationClient.cfg().orion.partyMessageEnabled = new HashMap<>();
        ConstellationClient.cfg().orion.partyMessageEnabled.put(id, enabled);
        ConstellationClient.saveConfig();
    }

    public static String template(String id) {
        Definition definition = BY_ID.get(id);
        if (definition == null) return "";
        if (ConstellationClient.cfg().orion.partyMessageTemplates == null)
            ConstellationClient.cfg().orion.partyMessageTemplates = new HashMap<>();
        return ConstellationClient.cfg().orion.partyMessageTemplates.getOrDefault(id, definition.defaultTemplate());
    }

    public static void setTemplate(String id, String template) {
        Definition definition = BY_ID.get(id);
        if (definition == null) return;
        String clean = template.replace('\n', ' ').replace('\r', ' ');
        if (clean.length() > 120) clean = clean.substring(0, 120);
        if (ConstellationClient.cfg().orion.partyMessageTemplates == null)
            ConstellationClient.cfg().orion.partyMessageTemplates = new HashMap<>();
        ConstellationClient.cfg().orion.partyMessageTemplates.put(id, clean);
        ConstellationClient.saveConfig();
    }

    public static void resetTemplate(String id) {
        if (ConstellationClient.cfg().orion.partyMessageTemplates == null)
            ConstellationClient.cfg().orion.partyMessageTemplates = new HashMap<>();
        ConstellationClient.cfg().orion.partyMessageTemplates.remove(id);
        ConstellationClient.saveConfig();
    }

    public static void send(String id, Map<String, ?> variables) {
        send(id, variables, true);
    }

    public static void sendAnywhere(String id, Map<String, ?> variables) {
        send(id, variables, false, id);
    }

    public static boolean trySendAnywhere(String id, Map<String, ?> variables) {
        return sendResult(id, variables, false, id);
    }

    private static void send(String id, Map<String, ?> variables, boolean dungeonOnly) {
        send(id, variables, dungeonOnly, id);
    }

    public static void sendAnywhere(String id, Map<String, ?> variables, String cooldownKey) {
        send(id, variables, false, id + ":" + cooldownKey.toLowerCase(java.util.Locale.ROOT));
    }

    public static String preview(String id, Map<String, ?> variables) {
        if (!BY_ID.containsKey(id)) return "";
        String text = template(id);
        for (var variable : variables.entrySet())
            text = text.replace("{" + variable.getKey() + "}", String.valueOf(variable.getValue()));
        text = text.replace('\n', ' ').replace('\r', ' ').trim();
        return text.substring(0, Math.min(120, text.length()));
    }

    public static boolean canSendAnywhere(String id) {
        var cfg = ConstellationClient.cfg().orion;
        return BY_ID.containsKey(id) && cfg != null && cfg.partyMessages && enabled(id)
            && !cfg.streamerMode && ConstellationClient.loc().onHypixel();
    }

    private static void send(String id, Map<String, ?> variables, boolean dungeonOnly, String cooldownKey) {
        sendResult(id, variables, dungeonOnly, cooldownKey);
    }

    private static boolean sendResult(String id, Map<String, ?> variables, boolean dungeonOnly, String cooldownKey) {
        var cfg = ConstellationClient.cfg().orion;
        Definition definition = BY_ID.get(id);
        if (definition == null || !canSendAnywhere(id)
            || dungeonOnly && !ConstellationClient.loc().inDungeons()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return false;
        long now = System.currentTimeMillis();
        if (now - LAST_SENT.getOrDefault(cooldownKey, 0L) < 3_000) return false;
        String text = preview(id, variables);
        if (text.isEmpty()) return false;
        LAST_SENT.put(cooldownKey, now);
        mc.player.connection.sendCommand("pc " + text);
        return true;
    }

    public static void send(String id) { send(id, Map.of()); }
    public static void reset() { LAST_SENT.clear(); }

    private static Definition def(String id, String name, String category, String template, String... variables) {
        return new Definition(id, name, category, template, List.of(variables));
    }

    private static Definition livid(String colour) {
        return def("livid-" + colour, "Livid: " + colour, "Livid", "{color} Livid", "color", "livid");
    }
}
