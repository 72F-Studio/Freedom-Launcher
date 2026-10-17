package dev.seventwo.launcher;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notification access serves two things: discovering controllable media sessions, and notification dots.
 * For dots only the posting package, its user and the notification flags are read; titles, text and
 * extras are never read or stored. Ongoing notifications and group summaries don't count.
 */
public final class MediaAccessService extends NotificationListenerService {
    interface Listener { void onDotsChanged(); }
    private static final Map<String, Integer> counts = new ConcurrentHashMap<>();
    private static Listener listener;
    private static final Handler main = new Handler(Looper.getMainLooper());

    static void setListener(Listener l) { listener = l; }
    static String key(String packageName, UserHandle user) { return packageName + "/" + user.hashCode(); }
    static boolean hasDot(String packageName, UserHandle user) { Integer n = counts.get(key(packageName, user)); return n != null && n > 0; }

    private static boolean counts(StatusBarNotification sbn) {
        int flags = sbn.getNotification().flags;
        return (flags & Notification.FLAG_ONGOING_EVENT) == 0 && (flags & Notification.FLAG_GROUP_SUMMARY) == 0
            && (flags & Notification.FLAG_FOREGROUND_SERVICE) == 0;
    }

    private void rebuild() {
        HashMap<String, Integer> next = new HashMap<>();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification sbn : active)
                if (counts(sbn)) next.merge(key(sbn.getPackageName(), sbn.getUser()), 1, Integer::sum);
        } catch (RuntimeException ignored) { }
        counts.clear(); counts.putAll(next); notifyChanged();
    }

    private static void notifyChanged() { main.post(() -> { if (listener != null) listener.onDotsChanged(); }); }

    @Override public void onListenerConnected() { rebuild(); }
    @Override public void onListenerDisconnected() { counts.clear(); notifyChanged(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { rebuild(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { rebuild(); }
}
