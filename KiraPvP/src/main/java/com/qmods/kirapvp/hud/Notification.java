package com.qmods.kirapvp.hud;

/** Mutable, reusable slot - {@link NotificationManager} pools these instead of allocating per toggle. */
final class Notification {
    String text = "";
    long startTime;
}
