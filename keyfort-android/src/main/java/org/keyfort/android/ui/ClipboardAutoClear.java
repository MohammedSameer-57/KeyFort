package org.keyfort.android.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

/**
 * Shared clipboard helper that copies sensitive material and auto-wipes it after 30 seconds
 * while reporting 1-second countdown ticks to the active UI.
 */
final class ClipboardAutoClear {

    static final int CLEAR_SECONDS = 30;

    interface TickListener {
        void onTick(int secondsRemaining);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tickRunnable;
    private int secondsLeft = 0;

    void copyWithAutoClear(Context context, String label, String value, TickListener listener) {
        if (context == null || value == null) return;
        Context appContext = context.getApplicationContext();
        ClipboardManager cm = (ClipboardManager) appContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
        }

        cancel();
        secondsLeft = CLEAR_SECONDS;
        if (listener != null) {
            listener.onTick(secondsLeft);
        }

        WeakReference<Context> ctxRef = new WeakReference<>(appContext);
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                secondsLeft--;
                if (secondsLeft > 0) {
                    if (listener != null) {
                        listener.onTick(secondsLeft);
                    }
                    handler.postDelayed(this, 1000L);
                } else {
                    Context ctx = ctxRef.get();
                    if (ctx != null) {
                        ClipboardManager manager = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (manager != null) {
                            manager.setPrimaryClip(ClipData.newPlainText("", ""));
                        }
                    }
                    if (listener != null) {
                        listener.onTick(0);
                    }
                }
            }
        };
        handler.postDelayed(tickRunnable, 1000L);
    }

    void cancel() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }
        secondsLeft = 0;
    }
}
