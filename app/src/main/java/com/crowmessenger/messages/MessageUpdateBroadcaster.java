package com.crowmessenger.messages;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

final class MessageUpdateBroadcaster {
    private MessageUpdateBroadcaster() {
    }

    static void broadcast(Context context, String address) {
        broadcast(context, address, "", 0L);
    }

    static void broadcastIncomingSms(Context context, String address, String body, long dateMillis) {
        broadcast(context, address, body, dateMillis);
    }

    private static void broadcast(Context context, String address, String body, long dateMillis) {
        if (context == null) {
            return;
        }
        Intent update = new Intent(MainActivity.ACTION_MESSAGE_RECEIVED);
        update.setPackage(context.getPackageName());
        if (!TextUtils.isEmpty(address)) {
            update.putExtra(MainActivity.EXTRA_OPEN_ADDRESS, address);
        }
        if (!TextUtils.isEmpty(body)) {
            update.putExtra(MainActivity.EXTRA_MESSAGE_BODY, body);
            update.putExtra(MainActivity.EXTRA_MESSAGE_DATE, dateMillis);
        }
        context.sendBroadcast(update);
    }
}
