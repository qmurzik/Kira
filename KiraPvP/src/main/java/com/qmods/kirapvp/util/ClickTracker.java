package com.qmods.kirapvp.util;

/**
 * Fixed-capacity ring buffer of click timestamps for one mouse button.
 * Never allocates after construction and never grows unbounded: stale
 * entries are dropped by advancing the head pointer, not by removing
 * elements from a list.
 */
public final class ClickTracker {

    private static final int CAPACITY = 64;
    private static final long WINDOW_MS = 1000L;

    private final long[] timestamps = new long[CAPACITY];
    private int head;
    private int count;

    public void recordClick(long nowMillis) {
        expireStale(nowMillis);
        int writeIndex = (head + count) % CAPACITY;
        timestamps[writeIndex] = nowMillis;
        if (count < CAPACITY) {
            count++;
        } else {
            head = (head + 1) % CAPACITY;
        }
    }

    /** Drops entries older than the trailing 1s window and returns how many remain. */
    public int getCps(long nowMillis) {
        expireStale(nowMillis);
        return count;
    }

    private void expireStale(long nowMillis) {
        while (count > 0 && nowMillis - timestamps[head] > WINDOW_MS) {
            head = (head + 1) % CAPACITY;
            count--;
        }
    }
}
