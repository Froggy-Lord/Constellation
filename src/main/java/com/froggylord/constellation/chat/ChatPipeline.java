package com.froggylord.constellation.chat;

import com.froggylord.constellation.ConstellationClient;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatPipeline {

    public enum Priority { EARLIEST, NORMAL, LATEST }

    @FunctionalInterface
    public interface AllowFilter { boolean allow(Component message); }

    @FunctionalInterface
    public interface GameHandler { void handle(Component message); }

    @FunctionalInterface
    public interface ModifyHandler { Component modify(Component message); }

    private record AllowEntry(AllowFilter filter, Priority priority) {}
    private record GameEntry(GameHandler handler, Priority priority) {}
    private record ModifyEntry(ModifyHandler handler, Priority priority) {}

    private final List<AllowEntry> allowListeners = new CopyOnWriteArrayList<>();
    private final List<GameEntry> gameListeners = new CopyOnWriteArrayList<>();
    private final List<ModifyEntry> modifyListeners = new CopyOnWriteArrayList<>();

    public void init() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            for (AllowEntry e : allowListeners) {
                if (!e.filter.allow(message)) return false;
            }
            return true;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            for (GameEntry e : gameListeners) {
                try { e.handler.handle(message); } catch (Exception ex) {}
            }
        });

        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            Component modified = message;
            for (ModifyEntry e : modifyListeners) {
                try { modified = e.handler.modify(modified); } catch (Exception ex) {}
            }
            return modified;
        });
    }

    public void allow(AllowFilter filter) { allow(filter, Priority.NORMAL); }
    public void allow(AllowFilter filter, Priority prio) {
        allowListeners.add(new AllowEntry(filter, prio));
        allowListeners.sort(Comparator.comparing(e -> e.priority.ordinal()));
    }

    public void onGame(GameHandler handler) { onGame(handler, Priority.NORMAL); }
    public void onGame(GameHandler handler, Priority prio) {
        gameListeners.add(new GameEntry(handler, prio));
        gameListeners.sort(Comparator.comparing(e -> e.priority.ordinal()));
    }

    public void modify(ModifyHandler handler) { modify(handler, Priority.NORMAL); }
    public void modify(ModifyHandler handler, Priority prio) {
        modifyListeners.add(new ModifyEntry(handler, prio));
        modifyListeners.sort(Comparator.comparing(e -> e.priority.ordinal()));
    }
}
