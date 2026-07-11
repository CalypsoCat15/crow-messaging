package com.crowmessenger.messages;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SmsSentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SmsSender.ACTION_SMS_SENT.equals(intent.getAction())) {
            return;
        }
        SmsSender.handleSentResult(context, intent, getResultCode());
    }
}
