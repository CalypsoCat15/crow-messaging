package com.crowmessenger.messages;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.text.TextUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class SmsDeliverReceiver extends BroadcastReceiver {
    interface IncomingSmsSaver {
        boolean save(Context context, String address, String body, long dateMillis);
    }

    private static final IncomingSmsSaver ANDROID_SMS_SAVER = SmsStore::saveIncomingSms;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) {
            return;
        }
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
            incoming.dateMillis = Math.min(incoming.dateMillis, message.getTimestampMillis());
        }

        for (IncomingSms incoming : incomingByAddress.values()) {
            saveNotifyAndBroadcast(context, incoming, ANDROID_SMS_SAVER);
        }
    }

    static boolean saveNotifyAndBroadcast(Context context, IncomingSms incoming, IncomingSmsSaver saver) {
        String body = incoming.body.toString();
        boolean saved;
        try {
            saved = saver.save(context, incoming.address, body, incoming.dateMillis);
        } catch (RuntimeException ex) {
            saved = false;
        }
        if (!saved) {
            LocalMmsStore.saveNotice(context, incoming.address, body, incoming.dateMillis);
        }
        MessageNotifier.showIncoming(context, incoming.address, body);
        MessageUpdateBroadcaster.broadcastIncomingSms(context, incoming.address, body, incoming.dateMillis);
        return saved;
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
