package com.studio72f.freedom;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notification access serves three things:
 * 1. Discovering controllable media sessions.
 * 2. Notification dots on app icons.
 * 3. Ratio-inspired "Tree" conversation timeline with direct inline replies.
 */
public final class MediaAccessService extends NotificationListenerService {
    public interface Listener { void onDotsChanged(); }
    public interface ConversationListener { void onConversationsChanged(List<ConversationItem> items); }

    public static final class ConversationItem {
        public final String key;
        public final String packageName;
        public final String appName;
        public final String sender;
        public final String text;
        public final String conversationTitle;
        public final long postTime;
        public final boolean isGroup;
        public final PendingIntent replyIntent;
        public final RemoteInput remoteInput;
        public final PendingIntent contentIntent;
        public final Icon icon;

        public ConversationItem(String key, String packageName, String appName, String sender,
                                String text, String conversationTitle, long postTime, boolean isGroup,
                                PendingIntent replyIntent, RemoteInput remoteInput,
                                PendingIntent contentIntent, Icon icon) {
            this.key = key;
            this.packageName = packageName;
            this.appName = appName;
            this.sender = sender;
            this.text = text;
            this.conversationTitle = conversationTitle;
            this.postTime = postTime;
            this.isGroup = isGroup;
            this.replyIntent = replyIntent;
            this.remoteInput = remoteInput;
            this.contentIntent = contentIntent;
            this.icon = icon;
        }

        public boolean canReply() {
            return replyIntent != null && remoteInput != null;
        }
    }

    private static volatile MediaAccessService activeInstance;
    private static final Map<String, Integer> counts = new ConcurrentHashMap<>();
    private static final List<ConversationItem> conversations = new CopyOnWriteArrayList<>();
    private static Listener listener;
    private static ConversationListener conversationListener;
    private static final Handler main = new Handler(Looper.getMainLooper());

    public static void setListener(Listener l) { listener = l; }
    public static void setConversationListener(ConversationListener l) {
        conversationListener = l;
        if (l != null) {
            main.post(() -> l.onConversationsChanged(new ArrayList<>(conversations)));
        }
    }

    public static boolean isServiceConnected() {
        return activeInstance != null;
    }

    public static List<ConversationItem> getConversations() {
        return new ArrayList<>(conversations);
    }

    static String key(String packageName, UserHandle user) { return packageName + "/" + user.hashCode(); }
    static boolean hasDot(String packageName, UserHandle user) { Integer n = counts.get(key(packageName, user)); return n != null && n > 0; }

    private static boolean counts(StatusBarNotification sbn) {
        int flags = sbn.getNotification().flags;
        return (flags & Notification.FLAG_ONGOING_EVENT) == 0 && (flags & Notification.FLAG_GROUP_SUMMARY) == 0
            && (flags & Notification.FLAG_FOREGROUND_SERVICE) == 0;
    }

    private void rebuild() {
        HashMap<String, Integer> nextCounts = new HashMap<>();
        ArrayList<ConversationItem> nextConversations = new ArrayList<>();
        PackageManager pm = getPackageManager();

        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    if (counts(sbn)) {
                        nextCounts.merge(key(sbn.getPackageName(), sbn.getUser()), 1, Integer::sum);
                    }

                    // Parse conversation / messaging notification
                    Notification n = sbn.getNotification();
                    if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;

                    Bundle extras = n.extras;
                    if (extras == null) continue;

                    CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
                    CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
                    CharSequence bigTextCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                    CharSequence subTextCs = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
                    CharSequence convTitleCs = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);

                    // Check for RemoteInput reply action
                    PendingIntent replyIntent = null;
                    RemoteInput replyRemoteInput = null;
                    if (n.actions != null) {
                        for (Notification.Action action : n.actions) {
                            if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                                replyIntent = action.actionIntent;
                                replyRemoteInput = action.getRemoteInputs()[0];
                                break;
                            }
                        }
                    }

                    String appLabel = sbn.getPackageName();
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(sbn.getPackageName(), 0);
                        CharSequence al = pm.getApplicationLabel(ai);
                        if (al != null) appLabel = al.toString();
                    } catch (PackageManager.NameNotFoundException ignored) {}

                    String text = bigTextCs != null ? bigTextCs.toString() : (textCs != null ? textCs.toString() : "");
                    String sender = titleCs != null ? titleCs.toString() : appLabel;
                    String convTitle = convTitleCs != null ? convTitleCs.toString() : (subTextCs != null ? subTextCs.toString() : "");
                    boolean isGroup = convTitleCs != null && !convTitleCs.toString().isEmpty();

                    if (!text.isEmpty() || replyIntent != null) {
                        ConversationItem item = new ConversationItem(
                                sbn.getKey(),
                                sbn.getPackageName(),
                                appLabel,
                                sender,
                                text,
                                convTitle,
                                sbn.getPostTime(),
                                isGroup,
                                replyIntent,
                                replyRemoteInput,
                                n.contentIntent,
                                n.getSmallIcon()
                        );
                        nextConversations.add(item);
                    }
                }
            }
        } catch (RuntimeException ignored) { }

        // Sort latest messages first
        Collections.sort(nextConversations, (a, b) -> Long.compare(b.postTime, a.postTime));

        counts.clear();
        counts.putAll(nextCounts);
        conversations.clear();
        conversations.addAll(nextConversations);

        notifyChanged();
    }

    private static void notifyChanged() {
        main.post(() -> {
            if (listener != null) listener.onDotsChanged();
            if (conversationListener != null) conversationListener.onConversationsChanged(new ArrayList<>(conversations));
        });
    }

    public static boolean sendReply(Context context, ConversationItem item, String replyText) {
        if (item == null || item.replyIntent == null || item.remoteInput == null || replyText == null || replyText.trim().isEmpty()) {
            return false;
        }
        try {
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putCharSequence(item.remoteInput.getResultKey(), replyText.trim());
            RemoteInput.addResultsToIntent(new RemoteInput[]{item.remoteInput}, intent, bundle);
            item.replyIntent.send(context, 0, intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void dismissNotification(String key) {
        MediaAccessService instance = activeInstance;
        if (instance != null && key != null) {
            try {
                instance.cancelNotification(key);
                for (int i = 0; i < conversations.size(); i++) {
                    if (key.equals(conversations.get(i).key)) {
                        conversations.remove(i);
                        break;
                    }
                }
                notifyChanged();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onListenerConnected() {
        activeInstance = this;
        rebuild();
    }

    @Override
    public void onListenerDisconnected() {
        if (activeInstance == this) activeInstance = null;
        counts.clear();
        conversations.clear();
        notifyChanged();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { rebuild(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { rebuild(); }
}
