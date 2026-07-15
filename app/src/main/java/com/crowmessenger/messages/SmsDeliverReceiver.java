package com.crowmessenger.messages;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsDeliverReceiver extends BroadcastReceiver {
    private static final String TAG = "CrowSmsDelivery";

    interface IncomingSmsSaver {
        boolean save(Context context, String address, String body, long dateMillis);
    }

    private static final IncomingSmsSaver ANDROID_SMS_SAVER = SmsStore::saveIncomingSms;
    private static final ExecutorService DELIVERY_EXECUTOR = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "crow-sms-delivery")
    );

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) {
            return;
        }
        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        DELIVERY_EXECUTOR.execute(() -> {
            try {
                handleDelivery(appContext, intent);
            } catch (RuntimeException ex) {
                Log.e(TAG, "Incoming SMS delivery failed", ex);
            } finally {
                pendingResult.finish();
            }
        });
    }

    static void handleDelivery(Context context, Intent intent) {
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }
        Map<String, IncomingSms> incomingByAddress = new LinkedHashMap<>();
        for (SmsMessage message : messages) {
            if (message == null) {
                continue;
            }
            String address = message.getDisplayOriginatingAddress();
            String body = message.getDisplayMessageBody();
            if (TextUtils.isEmpty(body)) {
                continue;
            }
            String key = TextUtils.isEmpty(address) ? "Unknown" : address;
            IncomingSms incoming = incomingByAddress.get(key);
            if (incoming == null) {
                incoming = new IncomingSms(key, message.getTimestampMillis());
                incomingByAddress.put(key, incoming);
            }
            incoming.body.append(body);
            incoming.dateMillis = earliestPositiveTimestamp(incoming.dateMillis, message.getTimestampMillis());
        }

        for (IncomingSms incoming : incomingByAddress.values()) {
            saveNotifyAndBroadcast(context, incoming, ANDROID_SMS_SAVER);
        }
    }

    static boolean saveNotifyAndBroadcast(Context context, IncomingSms incoming, IncomingSmsSaver saver) {
        String body = incoming.body.toString();
        long receivedAt = incoming.dateMillis > 0 ? incoming.dateMillis : System.currentTimeMillis();
        boolean saved;
        try {
            saved = saver.save(context, incoming.address, body, receivedAt);
        } catch (RuntimeException ex) {
            saved = false;
        }
        if (!saved) {
            LocalMmsStore.saveNotice(context, incoming.address, body, receivedAt);
        }
        InboxSnapshotStore.upsertIncomingDurably(context, incoming.address, body, receivedAt);
        // Post first so an already-open conversation can clear it when the UI receives the update.
        MessageNotifier.showIncoming(context, incoming.address, body, receivedAt);
        MessageUpdateBroadcaster.broadcastIncomingSms(context, incoming.address, body, receivedAt);
        return saved;
    }

    static long earliestPositiveTimestamp(long first, long second) {
        if (first <= 0) {
            return Math.max(0, second);
        }
        if (second <= 0) {
            return first;
        }
        return Math.min(first, second);
    }

    static final class IncomingSms {
        final String address;
        final StringBuilder body = new StringBuilder();
        long dateMillis;

        IncomingSms(String address, long dateMillis) {
            this.address = address;
            this.dateMillis = dateMillis;
        }
    }
}
