package com.froggylord.constellation.data;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.core.Patterns;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

// ported from devonian (GPL-3.0): api/dungeon/Stages.kt
public final class DungeonState {

    public enum Phase { CLEAR, PUZZLES, TERMINALS, BOSS }

    public record Teammate(String name, String playerClass) {}
    public record DungeonEnter() {}
    public record DungeonStart(long startTime) {}
    public record DungeonComplete(long elapsedMs) {}
    public record RoomEnter(String name, RoomTransform.Direction dir, int cornerX, int cornerZ) {}
    public record RoomLeave(String name) {}
    public record TypedRoom(RoomType type, int cellX, int cellZ, String floor) {}
    public record BossPhaseChange(String previous, String current) {}
    public record FloorChange(String previous, String current) {}
    public record Death(String player, int deaths) {}

    private String floor = "";
    private Phase phase = Phase.CLEAR;
    private String bossPhase = "";
    private boolean inBoss;
    private boolean inDungeon;
    private boolean runStarted;
    private boolean runEnded;
    private long startTime;
    private int deaths;
    private String playerClass = "";
    private List<Teammate> teammates = List.of();
    private String currentRoom = "";
    private RoomTransform.Direction roomDirection = RoomTransform.Direction.NW;
    private int roomCornerX;
    private int roomCornerZ;

    public void init() {
        ConstellationClient.tick().every(4, "dungeon-state", this::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(ChatFormatting.stripFormatting(message.getString()));
            return true;
        });
    }

    private void tick() {
        boolean nowInDungeon = ConstellationClient.loc().inDungeons();
        if (nowInDungeon && !inDungeon) {
            resetRun();
            inDungeon = true;
            ConstellationClient.bus().post(new DungeonEnter());
        } else if (!nowInDungeon && inDungeon) {
            leaveRoom();
            inDungeon = false;
            if (runStarted && !runEnded) complete();
            return;
        }
        if (!nowInDungeon) return;

        List<String> sidebar = ConstellationClient.loc().getSidebarLines();
        boolean sawFloor = false;
        boolean sawTime = false;
        for (String line : sidebar) {
            Matcher floorMatch = Patterns.get("dungeon.floor").matcher(line);
            if (floorMatch.find()) {
                sawFloor = true;
                setFloor(floorMatch.group("f").trim());
            }
            Matcher time = Patterns.get("dungeon.time").matcher(line);
            if (time.find()) {
                sawTime = true;
                int seconds = (time.group("m") == null ? 0 : Integer.parseInt(time.group("m")) * 60)
                    + Integer.parseInt(time.group("s"));
                start(seconds);
            }
            Matcher sidebarDeaths = Patterns.get("dungeon.deaths").matcher(line);
            if (sidebarDeaths.find()) setDeaths(Integer.parseInt(sidebarDeaths.group("d")), "");
        }

        List<Teammate> found = new ArrayList<>();
        String ownName = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().name();
        for (String line : TabList.lines()) {
            Matcher ownClass = Patterns.get("dungeon.class").matcher(line);
            if (ownClass.find()) playerClass = ownClass.group("class");
            Matcher teammate = Patterns.get("dungeon.teammate").matcher(line);
            if (teammate.find()) {
                String name = teammate.group("name");
                String clazz = teammate.group("class");
                found.add(new Teammate(name, clazz));
                if (name.equalsIgnoreCase(ownName)) playerClass = clazz;
            }
        }
        teammates = List.copyOf(found);
        if (ConstellationClient.verify() && (!sawFloor || !sawTime))
            ConstellationClient.verifyNoMatch("dungeon state sidebar floor=" + sawFloor + " time=" + sawTime);
    }

    private void onChat(String message) {
        if (!inDungeon || message == null) return;
        Matcher death = Patterns.get("chat.dungeon.death").matcher(message);
        if (death.matches()) setDeaths(deaths + 1, death.group("name"));
        if (Patterns.get("chat.dungeon.complete").matcher(message).find()) complete();

        if (message.startsWith("[BOSS] ") && !message.startsWith("[BOSS] The Watcher")) {
            inBoss = true;
            phase = Phase.BOSS;
        } else if (message.contains("activated a terminal") || message.contains("completed a terminal")) {
            phase = Phase.TERMINALS;
        }

        if (message.startsWith("[BOSS] Maxor:")) setBossPhase("Maxor");
        else if (message.startsWith("[BOSS] Storm:")) setBossPhase("Storm");
        else if (message.startsWith("[BOSS] Goldor:")) setBossPhase("Goldor");
        else if (message.startsWith("[BOSS] Necron: All this, for nothing")) setBossPhase("Wither King");
        else if (message.startsWith("[BOSS] Necron:")) setBossPhase("Necron");
    }

    private void start(int elapsedSeconds) {
        if (runStarted) return;
        runStarted = true;
        runEnded = false;
        startTime = System.currentTimeMillis() - elapsedSeconds * 1000L;
        ConstellationClient.bus().post(new DungeonStart(startTime));
    }

    private void complete() {
        if (runEnded) return;
        runEnded = true;
        long elapsed = startTime == 0 ? 0 : System.currentTimeMillis() - startTime;
        ConstellationClient.bus().post(new DungeonComplete(elapsed));
    }

    private void setFloor(String next) {
        if (Objects.equals(floor, next)) return;
        String previous = floor;
        floor = next;
        ConstellationClient.bus().post(new FloorChange(previous, next));
    }

    private void setBossPhase(String next) {
        if (Objects.equals(bossPhase, next)) return;
        String previous = bossPhase;
        bossPhase = next;
        inBoss = true;
        phase = next.equals("Goldor") ? Phase.TERMINALS : Phase.BOSS;
        ConstellationClient.bus().post(new BossPhaseChange(previous, next));
    }

    private void setDeaths(int next, String player) {
        if (next <= deaths) return;
        deaths = next;
        ConstellationClient.bus().post(new Death(player, deaths));
    }

    public void enterRoom(String name, RoomTransform.Direction dir, int cornerX, int cornerZ) {
        if (!currentRoom.isEmpty() && !currentRoom.equals(name)) leaveRoom();
        if (currentRoom.equals(name) && roomDirection == dir && roomCornerX == cornerX && roomCornerZ == cornerZ) return;
        currentRoom = name;
        roomDirection = dir;
        roomCornerX = cornerX;
        roomCornerZ = cornerZ;
        ConstellationClient.bus().post(new RoomEnter(name, dir, cornerX, cornerZ));
    }

    public void leaveRoom() {
        if (currentRoom.isEmpty()) return;
        String previous = currentRoom;
        currentRoom = "";
        ConstellationClient.bus().post(new RoomLeave(previous));
    }

    public void enterTypedRoom(RoomType type, int cellX, int cellZ) {
        leaveRoom();
        ConstellationClient.bus().post(new TypedRoom(type, cellX, cellZ, floor));
    }

    private void resetRun() {
        floor = "";
        phase = Phase.CLEAR;
        bossPhase = "";
        inBoss = false;
        runStarted = false;
        runEnded = false;
        startTime = 0;
        deaths = 0;
        playerClass = "";
        teammates = List.of();
        currentRoom = "";
    }

    public String floor() { return floor; }
    public Phase phase() { return phase; }
    public String bossPhase() { return bossPhase; }
    public boolean inBoss() { return inBoss; }
    public boolean runStarted() { return runStarted; }
    public boolean runEnded() { return runEnded; }
    public long startTime() { return startTime; }
    public int deaths() { return deaths; }
    public String playerClass() { return playerClass; }
    public List<Teammate> teammates() { return teammates; }

    /** Dungeon class of a teammate by name (case-insensitive), or "" if not a known teammate. */
    public String classOf(String name) {
        for (Teammate t : teammates) if (t.name().equalsIgnoreCase(name)) return t.playerClass();
        return "";
    }

    public String currentRoom() { return currentRoom; }
    public RoomTransform.Direction roomDirection() { return roomDirection; }
    public int roomCornerX() { return roomCornerX; }
    public int roomCornerZ() { return roomCornerZ; }
}
