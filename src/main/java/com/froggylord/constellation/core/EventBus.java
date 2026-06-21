package com.froggylord.constellation.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Typed event bus. Listeners subscribe by event class.
 * Events are fired synchronously on the calling thread.
 * Any listener can cancel a CancellableEvent by calling cancel().
 */
public class EventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> Collections.synchronizedList(new ArrayList<>()))
                 .add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T> void post(T event) {
        List<Consumer<?>> list = listeners.get(event.getClass());
        if (list == null) return;

        // iterate over a snapshot so listeners can un/sub during post
        for (Consumer<?> c : list.toArray(new Consumer[0])) {
            try {
                ((Consumer<T>) c).accept(event);
            } catch (Exception e) {
                com.froggylord.constellation.ConstellationClient.LOGGER
                    .error("EventBus error in listener for {}: {}", event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    public void unsubscribe(Class<?> eventType, Consumer<?> listener) {
        List<Consumer<?>> list = listeners.get(eventType);
        if (list != null) list.remove(listener);
    }

    public void clear() {
        listeners.clear();
    }
}
