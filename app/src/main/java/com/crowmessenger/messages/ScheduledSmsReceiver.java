package com.crowmessenger.messages;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

public class ScheduledSmsReceiver extends BroadcastReceiver {
    static final String ACTION_SEND_SCHEDULED = BuildConfig.APPLICATION_ID + ".SEND_SCHEDULED";
    private static final String EXTRA_ID = "scheduled_message_id";
    private static final String SCHEDULED_URI_PREFIX = "scheduled-message://";
    private static final Object SEND_LOCK = new Object();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !ACTION_SEND_SCHEDULED.equals(action)) {
            return;
        }
        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                handleIntent(appContext, intent);
            } finally {
                pendingResult.finish();
            }
        }, "crow-scheduled-message").start();
    }

    static void handleIntent(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            scheduleAll(context);
        } else if (ACTION_SEND_SCHEDULED.equals(action)) {
            synchronized (SEND_LOCK) {
                sendScheduledMessage(context, intent);
            }
        }
    }

    private static void sendScheduledMessage(Context context, Intent intent) {
        SmsSender.cleanupStalePendingSends(context);
        String id = intent.getStringExtra(EXTRA_ID);
        ScheduledMessageStore.ScheduledMessage message = ScheduledMessageStore.find(context, id);
        if (message == null || TextUtils.isEmpty(message.address) || TextUtils.isEmpty(message.body)) {
            ScheduledMessageStore.delete(context, id);
            return;
        }
        if (message.failed() || SmsSender.hasPendingScheduled(context, message.id)) {
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            markFailed(context, message, "Crow Messenger does not have permission to send texts.");
            return;
        }
        try {
            SmsSender.sendScheduled(context, message);
            MessageUpdateBroadcaster.broadcast(context, message.address);
        } catch (SmsSender.SendException ex) {
            markFailed(context, message, ex.getMessage());
        }
    }

    static boolean schedule(Context context, ScheduledMessageStore.ScheduledMessage message) {
        if (message == null) {
            return false;
        }
        if (message.failed()) {
            return false;
        }
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            markFailed(context, message, "Scheduled texts are not available on this phone right now.");
            return false;
        }
        PendingIntent pendingIntent = pendingIntent(context, message.id);
        long triggerAt = Math.max(System.currentTimeMillis() + 1000L, message.sendAtMillis);
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            return true;
        } catch (RuntimeException ex) {
            markFailed(context, message, "Scheduled text could not be scheduled.");
            return false;
        }
    }

    static void cancel(Context context, String id) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(context, id));
        }
    }

    static void deleteAndCancel(Context context, String id) {
        // Delete durably first. A stale alarm is safe because its receiver will find no saved message.
        ScheduledMessageStore.delete(context, id);
        cancel(context, id);
    }

    static void scheduleAll(Context context) {
        SmsSender.cleanupStalePendingSends(context);
        long now = System.currentTimeMillis();
        for (ScheduledMessageStore.ScheduledMessage message : ScheduledMessageStore.all(context)) {
            if (message.failed()) {
                continue;
            }
            if (SmsSender.hasPendingScheduled(context, message.id)) {
                continue;
            }
            if (message.sendAtMillis <= now) {
                sendNow(context, message.id);
            } else {
                schedule(context, message);
            }
        }
    }

    private static void markFailed(Context context, ScheduledMessageStore.ScheduledMessage message, String reason) {
        ScheduledMessageStore.markFailed(context, message, reason);
        MessageNotifier.showScheduledFailed(context, message.address, reason);
        MessageUpdateBroadcaster.broadcast(context, message.address);
    }

    private static void sendNow(Context context, String id) {
        Intent intent = new Intent(context, ScheduledSmsReceiver.class);
        intent.setAction(ACTION_SEND_SCHEDULED);
        intent.putExtra(EXTRA_ID, id);
        synchronized (SEND_LOCK) {
            sendScheduledMessage(context, intent);
        }
    }

    private static PendingIntent pendingIntent(Context context, String id) {
        Intent intent = new Intent(context, ScheduledSmsReceiver.class);
        intent.setAction(ACTION_SEND_SCHEDULED);
        intent.putExtra(EXTRA_ID, id);
        intent.setData(Uri.parse(SCHEDULED_URI_PREFIX + Uri.encode(id)));
        return PendingIntent.getBroadcast(
                context,
                AddressUtil.stableId(id),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
