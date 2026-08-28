package cn.vcampus.client.view;

import javax.swing.Timer;

/** Schedules short-lived UI feedback without disturbing fast responses. */
final class DelayedUiUpdate {
    static final int DEFAULT_DELAY_MILLIS = 180;

    private DelayedUiUpdate() { }

    static Timer once(Runnable update) {
        Timer timer = new Timer(DEFAULT_DELAY_MILLIS, event -> update.run());
        timer.setRepeats(false);
        timer.start();
        return timer;
    }
}
