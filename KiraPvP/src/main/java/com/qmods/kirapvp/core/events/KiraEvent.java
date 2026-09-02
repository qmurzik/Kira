package com.qmods.kirapvp.core.events;

/**
 * Base type for internal mod events, dispatched through {@link com.qmods.kirapvp.core.EventBus}.
 * Deliberately separate from Forge's own event bus: these events describe mod-internal
 * state changes (module toggled, config saved) rather than game state.
 */
public abstract class KiraEvent {
}
