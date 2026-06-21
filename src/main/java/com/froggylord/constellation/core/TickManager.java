package com.froggylord.constellation.core;

import net.minecraft.client.Minecraft;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class TickManager {

    private record Task(String id, int intervalTicks, Runnable runnable) {}

    private final List<Task> tasks = new CopyOnWriteArrayList<>();
    private int tickCounter = 0;

    public void every(int intervalTicks, String id, Runnable runnable) {
        tasks.add(new Task(id, intervalTicks, runnable));
    }

    public void remove(String id) {
        tasks.removeIf(t -> t.id().equals(id));
    }

    public void onEndTick(Minecraft client) {
        if (client.player == null) return;
        tickCounter++;
        for (Task t : tasks) {
            if (t.intervalTicks > 0 && tickCounter % t.intervalTicks == 0) {
                try { t.runnable().run(); } catch (Exception e) { /* swallow */ }
            }
        }
    }

    public int getTickCount() { return tickCounter; }
}
