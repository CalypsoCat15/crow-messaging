package com.crowmessenger.messages;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

final class MessageUpdateBroadcaster {
    static final String EXTRA_INCOMING_UPDATE = BuildConfig.APPLICATION_ID + ".INCOMING_UPDATE";

    private MessageUpdateBroadcaster() {
    }

    static void broadcast(Context context, String address) {
        broadcast(context, address, "", 0L, false);
    }

    static void broadcastIncoming(Context context, String address) {
        broadcast(context, address, "", 0L, true);
    }

    static void broadcastIncomingSms(Context context, String address, String body, long dateMillis) {
        broadcast(context, address, body, dateMillis, true);
    }

    static boolean isIncomingUpdate(Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_INCOMING_UPDATE, false);
    }

    static Intent updateIntent(Context context, String address, String body, long dateMillis, boolean incoming) {
        Intent update = new Intent(MainActivity.ACTION_MESSAGE_RECEIVED);
        update.setPackage(context.getPackageName());
        update.putExtra(EXTRA_INCOMING_UPDATE, incoming);
        if (!TextUtils.isEmpty(address)) {
            update.putExtra(MainActivity.EXTRA_OPEN_ADDRESS, address);
        }
        if (!TextUtils.isEmpty(body)) {
            update.putExtra(MainActivity.EXTRA_MESSAGE_BODY, body);
            update.putExtra(MainActivity.EXTRA_MESSAGE_DATE, dateMillis);
        }
        return update;
    }

    private static void broadcast(Context context, String address, String body, long dateMillis, boolean incoming) {
        if (context == null) {
            return;
        }
        context.sendBroadcast(updateIntent(context, address, body, dateMillis, incoming));
    }
}
