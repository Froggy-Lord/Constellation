package com.froggylord.constellation.network;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Clientbound packet observation bus.
 * Listeners receive every clientbound packet; they filter by type themselves.
 * The ClientPacketListenerMixin fires packets through here.
 */
public class PacketBus {

    private final List<Consumer<Object>> listeners = new CopyOnWriteArrayList<>();

    public void register(Consumer<Object> listener) {
        listeners.add(listener);
    }

    public void unregister(Consumer<Object> listener) {
        listeners.remove(listener);
    }

    @SuppressWarnings("unchecked")
    public void fire(Object packet) {
        for (Consumer<Object> l : listeners) {
            try { l.accept(packet); } catch (Exception ignored) {}
        }
    }
}
