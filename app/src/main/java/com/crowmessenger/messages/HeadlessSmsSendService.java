package com.crowmessenger.messages;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.text.TextUtils;

public class HeadlessSmsSendService extends Service {
    static final String ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE";
    private static final String EXTRA_SMS_BODY = "sms_body";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ReplyRequest request = replyRequest(intent);
        if (request == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        new Thread(() -> {
            try {
                SmsSender.sendAndRecord(this, request.address, request.body);
                MessageUpdateBroadcaster.broadcast(this, request.address);
            } catch (SmsSender.SendException ex) {
                MessageNotifier.showSendFailed(this, request.address, ex.getMessage());
            } finally {
                stopSelf(startId);
            }
        }, "crow-respond-via-message").start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static ReplyRequest replyRequest(Intent intent) {
        if (intent == null || !ACTION_RESPOND_VIA_MESSAGE.equals(intent.getAction())) {
            return null;
        }
        String address = addressFromData(intent.getData());
        String body = bodyFromIntent(intent);
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(body)) {
            return null;
        }
        return new ReplyRequest(address, body);
    }

    static String addressFromData(Uri uri) {
        if (uri == null) {
            return "";
        }
        String scheme = uri.getScheme();
        if (!"sms".equals(scheme) && !"smsto".equals(scheme)
                && !"mms".equals(scheme) && !"mmsto".equals(scheme)) {
            return "";
        }
        String address = uri.getSchemeSpecificPart();
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        int queryStart = address.indexOf('?');
        if (queryStart >= 0) {
            address = address.substring(0, queryStart);
        }
        if (address.startsWith("//")) {
            address = address.substring(2);
        }
        return Uri.decode(address).trim();
    }

    static String bodyFromIntent(Intent intent) {
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (TextUtils.isEmpty(text)) {
            text = intent.getStringExtra(EXTRA_SMS_BODY);
        }
        return text == null ? "" : text.toString().trim();
    }

    static final class ReplyRequest {
        final String address;
        final String body;

        ReplyRequest(String address, String body) {
            this.address = address;
            this.body = body;
        }
    }
}
