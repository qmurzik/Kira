package com.qmods.kirapvp.hud;

import com.qmods.kirapvp.config.GuiState;
import com.qmods.kirapvp.core.EventBus;
import com.qmods.kirapvp.core.events.ModuleToggleEvent;
import com.qmods.kirapvp.util.MathUtil;
import com.qmods.kirapvp.util.RenderUtils;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Small "module enabled/disabled" toast stack. Slots are pooled up front
 * (see {@link Notification}) and reused, so pushing a notification never
 * allocates anything beyond the short message string itself - and that only
 * happens on the rare user-driven toggle, never per frame.
 */
public final class NotificationManager {

    private static final int CAPACITY = 5;
    private static final long VISIBLE_MS = 2000L;
    private static final long FADE_MS = 250L;

    private final Notification[] slots = new Notification[CAPACITY];
    private int count;
    private final GuiState guiState;

    public NotificationManager(GuiState guiState) {
        this.guiState = guiState;
        for (int i = 0; i < CAPACITY; i++) {
            slots[i] = new Notification();
        }
    }

    public void subscribe(EventBus eventBus) {
        eventBus.subscribe(ModuleToggleEvent.class, new EventBus.Listener<ModuleToggleEvent>() {
            @Override
            public void onEvent(ModuleToggleEvent event) {
                if (guiState.notificationsEnabled) {
                    push(event.getModule().getName() + (event.isEnabled() ? " enabled" : " disabled"));
                }
            }
        });
    }

    public void push(String text) {
        expireOldest(System.currentTimeMillis());
        int index;
        if (count < CAPACITY) {
            index = count++;
        } else {
            // Overflow only happens with 5 simultaneously visible toasts; shifting 4 refs is negligible.
            Notification oldest = slots[0];
            System.arraycopy(slots, 1, slots, 0, CAPACITY - 1);
            slots[CAPACITY - 1] = oldest;
            index = CAPACITY - 1;
        }
        slots[index].text = text;
        slots[index].startTime = System.currentTimeMillis();
    }

    private void expireOldest(long now) {
        while (count > 0 && now - slots[0].startTime > VISIBLE_MS + FADE_MS) {
            Notification expired = slots[0];
            System.arraycopy(slots, 1, slots, 0, count - 1);
            slots[count - 1] = expired;
            count--;
        }
    }

    public void render() {
        if (count == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        expireOldest(now);

        ScaledResolution res = RenderUtils.scaledResolution();
        if (res == null) {
            return;
        }
        int centerX = res.getScaledWidth() / 2;
        int y = 10;

        for (int i = 0; i < count; i++) {
            Notification n = slots[i];
            long age = now - n.startTime;
            float alpha;
            if (age < 150) {
                alpha = MathUtil.easeOutQuad(age / 150f);
            } else if (age > VISIBLE_MS) {
                alpha = 1f - MathUtil.easeOutQuad((age - VISIBLE_MS) / (float) FADE_MS);
            } else {
                alpha = 1f;
            }

            int width = RenderUtils.stringWidth(n.text);
            int boxX = centerX - width / 2 - 4;
            int boxAlpha = (int) (alpha * 160);
            int textAlpha = (int) (alpha * 255);

            RenderUtils.rect(boxX, y, width + 8, RenderUtils.fontHeight() + 4, RenderUtils.argb(boxAlpha, 20, 20, 24));
            RenderUtils.drawStringShadow(n.text, centerX - width / 2f, y + 2, RenderUtils.withAlpha(0xFFFFFF, textAlpha));

            y += RenderUtils.fontHeight() + 8;
        }
    }
}
