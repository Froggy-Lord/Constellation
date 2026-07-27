package com.froggylord.constellation.core;

import net.minecraft.client.Minecraft;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class TickManager {

    private record Task(String id, int intervalTicks, int dueTick, boolean oneShot, Runnable runnable) {}

    private final List<Task> tasks = new CopyOnWriteArrayList<>();
    private int tickCounter = 0;

    public String every(int intervalTicks, String id, Runnable runnable) {
        cancel(id);
        tasks.add(new Task(id, intervalTicks, 0, false, runnable));
        return id;
    }

    // ported from devonian (GPL-3.0): api/Scheduler.kt
    public String once(int delayTicks, String id, Runnable runnable) {
        cancel(id);
        tasks.add(new Task(id, 0, tickCounter + Math.max(0, delayTicks), true, runnable));
        return id;
    }

    public void cancel(String id) {
        tasks.removeIf(t -> t.id().equals(id));
    }

    public void remove(String id) { cancel(id); }

    public void onEndTick(Minecraft client) {
        if (client.player == null) return;
        tickCounter++;
        for (Task t : tasks) {
            if (t.oneShot && tickCounter >= t.dueTick) {
                tasks.remove(t);
                try { t.runnable().run(); } catch (Exception e) {  }
            } else if (!t.oneShot && t.intervalTicks > 0 && tickCounter % t.intervalTicks == 0) {
                try { t.runnable().run(); } catch (Exception e) {  }
            }
        }
    }

    public int getTickCount() { return tickCounter; }
}
