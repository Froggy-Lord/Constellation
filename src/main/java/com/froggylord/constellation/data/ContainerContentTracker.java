package com.froggylord.constellation.data;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// ported from Enhanced Storage (GPL-3.0): storage/ContainerContentTracker.java
public final class ContainerContentTracker {
    private static final Set<Integer> RECEIVED = ConcurrentHashMap.newKeySet();
    private ContainerContentTracker() {}
    public static void markReceived(int containerId) { RECEIVED.add(containerId); }
    public static boolean hasReceived(int containerId) { return RECEIVED.contains(containerId); }
    public static void reset() { RECEIVED.clear(); }
}
