package com.crowmessenger.messages;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

public class MmsDeliverReceiver extends BroadcastReceiver {
    private static final String MMS_NOTICE_RECEIVED = "Picture message notice received.";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(intent.getAction())) {
            return;
        }
        MmsDebugStore.record(context, "MMS receiver fired.");
        String refreshAddress = "";
        byte[] data = intent == null ? null : intent.getByteArrayExtra("data");
        if (data != null) {
            String id = newMmsDownloadId();
            archiveNoticePdu(context, id, data);
            MmsPduUtil.NotificationInfo notice = MmsPduUtil.parseNotification(data);
            String downloadUrl = notice.downloadUrl;
            String address = notice.sender;
            String conversationAddress = noticeConversationAddress(address);
            refreshAddress = conversationAddress;
            int subscriptionId = subscriptionIdFrom(intent);
            MmsDebugStore.record(context, "MMS notice received. parsed=" + notice.parsedHeaders
                    + ", bytes=" + data.length
                    + ", subId=" + subscriptionLabel(subscriptionId)
                    + ", txId=" + describeTransactionId(notice.transactionId)
                    + ", url=" + describeUrl(downloadUrl)
                    + ", senderType=" + describeSender(address));
            if (!TextUtils.isEmpty(downloadUrl)) {
                startMmsDownload(context, id, downloadUrl, conversationAddress, subscriptionId);
            } else {
                saveDownloadFailure(context, conversationAddress);
            }
        } else {
            MmsDebugStore.record(context, "MMS receiver fired with no data extra.");
            LocalMmsStore.saveNotice(context, "MMS", MMS_NOTICE_RECEIVED, System.currentTimeMillis());
            MessageNotifier.showIncoming(context, "MMS", MMS_NOTICE_RECEIVED);
            refreshAddress = "MMS";
            MessageUpdateBroadcaster.broadcastIncoming(context, refreshAddress);
        }

        MessageUpdateBroadcaster.broadcast(context, refreshAddress);
    }

    static String noticeConversationAddress(String address) {
        return TextUtils.isEmpty(address) ? "MMS" : address;
    }

    static String newMmsDownloadId() {
        return UUID.randomUUID().toString();
    }

    private void startMmsDownload(Context context, String id, String downloadUrl, String address, int subscriptionId) {
        File pduFile = null;
        try {
            MmsDebugStore.record(context, "Starting MMS download. senderType=" + describeSender(address)
                    + ", subId=" + subscriptionLabel(subscriptionId));
            File outputDir = MmsFiles.appFileDir(context, MmsFiles.DOWNLOADS_DIR);
            pduFile = new File(outputDir, id + ".pdu");
            if (!LocalMmsStore.savePending(context, id, address, pduFile.getAbsolutePath(), downloadUrl, subscriptionId)) {
                MmsDebugStore.record(context, "MMS download start failed: pending record could not be saved.");
                cleanupFailedDownloadStart(context, id, pduFile);
                saveDownloadFailure(context, address);
                return;
            }
            MmsDownloadedReceiver.requestCarrierDownload(context, id, downloadUrl, pduFile, subscriptionId);
            MmsDebugStore.record(context, "MMS download requested. file=" + pduFile.getName());
        } catch (Exception ignored) {
            MmsDebugStore.record(context, "MMS download start failed: " + ignored.getClass().getSimpleName());
            cleanupFailedDownloadStart(context, id, pduFile);
            saveDownloadFailure(context, address);
        }
    }

    static void cleanupFailedDownloadStart(Context context, String id, File pduFile) {
        LocalMmsStore.clearPending(context, id);
        if (pduFile != null) {
            MmsFiles.deleteAppFile(context, MmsFiles.DOWNLOADS_DIR, pduFile.getAbsolutePath());
        }
    }

    private void saveDownloadFailure(Context context, String address) {
        LocalMmsStore.saveNotice(context, address, LocalMmsStore.DOWNLOAD_FAILED_MESSAGE, System.currentTimeMillis());
        MessageNotifier.showIncoming(context, address, LocalMmsStore.DOWNLOAD_FAILED_MESSAGE);
        MessageUpdateBroadcaster.broadcastIncoming(context, address);
    }

    private void archiveNoticePdu(Context context, String id, byte[] data) {
        if (!MmsDebugStore.shouldArchiveRawPdus(context) || data == null || data.length == 0) {
            return;
        }
        try {
            File outputDir = MmsFiles.appFileDir(context, MmsFiles.NOTICES_DIR);
            File noticeFile = new File(outputDir, id + ".pdu");
            try (FileOutputStream stream = new FileOutputStream(noticeFile)) {
                stream.write(data);
            }
            MmsDebugStore.trimArchivedPduFiles(context);
            MmsDebugStore.record(context, "Archived MMS notice for debugging. file=" + noticeFile.getName());
        } catch (Exception ignored) {
            MmsDebugStore.record(context, "Could not archive MMS notice: " + ignored.getClass().getSimpleName());
        }
    }

    static String describeUrl(String downloadUrl) {
        if (TextUtils.isEmpty(downloadUrl)) {
            return "missing";
        }
        Uri uri = Uri.parse(downloadUrl);
        String query = uri.getEncodedQuery();
        String host = uri.getHost();
        String path = uri.getPath();
        return "present, scheme=" + (TextUtils.isEmpty(uri.getScheme()) ? "missing" : uri.getScheme())
                + ", hostLength=" + (host == null ? 0 : host.length())
                + ", pathLength=" + (path == null ? 0 : path.length())
                + ", queryLength=" + (query == null ? 0 : query.length())
                + ", length=" + downloadUrl.length();
    }

    static String describeSender(String sender) {
        if (TextUtils.isEmpty(sender)) {
            return "missing";
        }
        if (AddressUtil.isSendableSmsRecipient(sender)) {
            return "phone";
        }
        return sender.indexOf('@') > 0 ? "email" : "sender-id";
    }

    private String describeTransactionId(String transactionId) {
        if (TextUtils.isEmpty(transactionId)) {
            return "missing";
        }
        return "present,length=" + transactionId.length();
    }

    private int subscriptionIdFrom(Intent intent) {
        int subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (intent != null) {
            subscriptionId = intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                subscriptionId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            }
            if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                subscriptionId = intent.getIntExtra("sub_id", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            }
        }
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId();
        }
        return subscriptionId;
    }

    private String subscriptionLabel(int subscriptionId) {
        return subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ? "default" : String.valueOf(subscriptionId);
    }

}
