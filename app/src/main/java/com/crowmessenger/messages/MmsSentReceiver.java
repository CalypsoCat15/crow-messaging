package com.crowmessenger.messages;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MmsSentReceiver extends BroadcastReceiver {
    static final String ACTION_MMS_SENT = BuildConfig.APPLICATION_ID + ".MMS_SENT";
    static final String EXTRA_PDU_NAME = "pdu_name";
    static final String EXTRA_ADDRESS = "address";
    static final String EXTRA_IMAGE_URI = "image_uri";
    static final String EXTRA_LOCAL_MESSAGE_ID = "local_message_id";
    private static final ExecutorService RESULT_EXECUTOR = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "crow-mms-send-result")
    );

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !ACTION_MMS_SENT.equals(intent.getAction())) {
            return;
        }
        int resultCode = getResultCode();
        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        RESULT_EXECUTOR.execute(() -> {
            try {
                handleSentResult(appContext, intent, resultCode);
            } catch (RuntimeException ex) {
                MmsDebugStore.record(appContext, "MMS send callback failed: " + ex.getClass().getSimpleName());
            } finally {
                pendingResult.finish();
            }
        });
    }

    static boolean handleSentResult(Context context, Intent intent, int resultCode) {
        if (context == null || intent == null) {
            return false;
        }
        deleteOutgoingPdu(context, intent.getStringExtra(EXTRA_PDU_NAME));
        if (resultCode == Activity.RESULT_OK) {
            return false;
        }
        String address = intent.getStringExtra(EXTRA_ADDRESS);
        String localMessageId = intent.getStringExtra(EXTRA_LOCAL_MESSAGE_ID);
        boolean markedFailed;
        if (!TextUtils.isEmpty(localMessageId)) {
            markedFailed = LocalMmsStore.markSentMessageFailed(context, localMessageId, address);
            if (markedFailed) {
                MessageNotifier.showSendFailed(context, address, "Group text could not be sent by the carrier.");
            }
        } else {
            markedFailed = LocalMmsStore.markSentImageFailed(context, address, intent.getStringExtra(EXTRA_IMAGE_URI));
            if (markedFailed) {
                MessageNotifier.showPictureSendFailed(context, address, "Picture message could not be sent by the carrier.");
            }
        }
        if (!markedFailed) {
            MmsDebugStore.record(context, "MMS send failure had no matching local message.");
            return false;
        }
        MessageUpdateBroadcaster.broadcast(context, address);
        return true;
    }

    static void deleteOutgoingPdu(Context context, String fileName) {
        if (!isSafePduName(fileName)) {
            return;
        }
        File file = new File(MmsFiles.appFileDirPath(context, MmsFiles.OUTGOING_DIR), fileName);
        MmsFiles.deleteAppFile(context, MmsFiles.OUTGOING_DIR, file.getAbsolutePath());
    }

    static boolean isSafePduName(String fileName) {
        return !TextUtils.isEmpty(fileName)
                && fileName.endsWith(".pdu")
                && fileName.indexOf('/') < 0
                && fileName.indexOf('\\') < 0;
    }
}
