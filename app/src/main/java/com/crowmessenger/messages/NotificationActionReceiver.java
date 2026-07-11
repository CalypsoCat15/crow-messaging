package com.crowmessenger.messages;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

public class NotificationActionReceiver extends BroadcastReceiver {
    static final String ACTION_MARK_READ = BuildConfig.APPLICATION_ID + ".NOTIFICATION_MARK_READ";
    static final String ACTION_REPLY = BuildConfig.APPLICATION_ID + ".NOTIFICATION_REPLY";
    static final String EXTRA_ADDRESS = "address";
    static final String KEY_REPLY = "reply_text";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        String address = intent.getStringExtra(EXTRA_ADDRESS);
        if (TextUtils.isEmpty(address)) {
            return;
        }
        if (ACTION_MARK_READ.equals(action)) {
            markRead(context, address);
            return;
        }
        if (!ACTION_REPLY.equals(action)) {
            return;
        }
        CharSequence reply = replyText(intent);
        if (TextUtils.isEmpty(reply)) {
            return;
        }
        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                if (sendReply(appContext, address, reply.toString().trim())) {
                    markRead(appContext, address);
                }
            } finally {
                pendingResult.finish();
            }
        }, "crow-notification-reply").start();
    }

    static CharSequence replyText(Intent intent) {
        Bundle results = intent == null ? null : RemoteInput.getResultsFromIntent(intent);
        CharSequence reply = results == null ? null : results.getCharSequence(KEY_REPLY);
        return reply == null ? "" : reply.toString().trim();
    }

    static void markRead(Context context, String address) {
        SmsStore.markAddressRead(context, address);
        MessageNotifier.clearIncomingForAddress(context, address);
        MessageUpdateBroadcaster.broadcast(context, address);
    }

    private static boolean sendReply(Context context, String address, String body) {
        try {
            if (LocalMmsStore.isGroupAddress(address)) {
                MmsTextSender.sendAndRecord(context, address, body);
            } else {
                SmsSender.sendAndRecord(context, address, body);
            }
            return true;
        } catch (SmsSender.SendException ex) {
            MessageNotifier.showSendFailed(context, address, ex.getMessage());
            return false;
        }
    }
}
