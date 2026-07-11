package com.crowmessenger.messages;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.io.File;

public final class MmsSentReceiver extends BroadcastReceiver {
    static final String ACTION_MMS_SENT = BuildConfig.APPLICATION_ID + ".MMS_SENT";
    static final String EXTRA_PDU_NAME = "pdu_name";
    static final String EXTRA_ADDRESS = "address";
    static final String EXTRA_IMAGE_URI = "image_uri";
    static final String EXTRA_LOCAL_MESSAGE_ID = "local_message_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !ACTION_MMS_SENT.equals(intent.getAction())) {
            return;
        }
        deleteOutgoingPdu(context, intent.getStringExtra(EXTRA_PDU_NAME));
        if (getResultCode() == Activity.RESULT_OK) {
            return;
        }
        String address = intent.getStringExtra(EXTRA_ADDRESS);
        String localMessageId = intent.getStringExtra(EXTRA_LOCAL_MESSAGE_ID);
        if (!TextUtils.isEmpty(localMessageId)) {
            LocalMmsStore.markSentMessageFailed(context, localMessageId, address);
            MessageNotifier.showSendFailed(context, address, "Group text could not be sent by the carrier.");
        } else {
            LocalMmsStore.markSentImageFailed(context, address, intent.getStringExtra(EXTRA_IMAGE_URI));
            MessageNotifier.showPictureSendFailed(context, address, "Picture message could not be sent by the carrier.");
        }
        MessageUpdateBroadcaster.broadcast(context, address);
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
