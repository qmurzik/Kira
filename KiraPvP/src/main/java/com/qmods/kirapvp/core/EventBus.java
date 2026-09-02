package com.qmods.kirapvp.core;

import com.qmods.kirapvp.core.events.KiraEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, reflection-free publish/subscribe bus for internal mod events
 * (module toggles, config saves, notifications). Separate from Forge's own
 * {@code MinecraftForge.EVENT_BUS}, which is used directly for all
 * game-tick/render/world hooks - this bus only carries mod-internal signals
 * that fire rarely (never per-frame), so the lookup cost here is irrelevant.
 *
 * Not thread-safe by design: every caller runs on the client thread.
 */
public final class EventBus {

    public interface Listener<T extends KiraEvent> {
        void onEvent(T event);
    }

    private final Map<Class<?>, List<Listener<?>>> listeners = new HashMap<Class<?>, List<Listener<?>>>();

    public <T extends KiraEvent> void subscribe(Class<T> type, Listener<T> listener) {
        List<Listener<?>> list = listeners.get(type);
        if (list == null) {
            list = new ArrayList<Listener<?>>(2);
            listeners.put(type, list);
        }
        list.add(listener);
    }

    @SuppressWarnings("unchecked")
    public void post(KiraEvent event) {
        List<Listener<?>> list = listeners.get(event.getClass());
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            ((Listener<KiraEvent>) list.get(i)).onEvent(event);
        }
    }
}
