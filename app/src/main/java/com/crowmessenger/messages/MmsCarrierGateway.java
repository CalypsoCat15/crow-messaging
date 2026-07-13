package com.crowmessenger.messages;

import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;

@FunctionalInterface
interface MmsCarrierGateway {
    void send(Context context, Uri pduUri, PendingIntent sentIntent);
}
