package com.crowmessenger.messages;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SubscriptionManager;
import android.telephony.SmsManager;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class MmsDownloadedReceiver extends BroadcastReceiver {
    static final String ACTION_MMS_DOWNLOADED = BuildConfig.APPLICATION_ID + ".MMS_DOWNLOADED";
    static final String EXTRA_DOWNLOAD_ID = "download_id";
    private static final long PENDING_DOWNLOAD_GRACE_MILLIS = 15L * 60L * 1000L;
    private static final int MAX_DOWNLOAD_RETRIES = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !ACTION_MMS_DOWNLOADED.equals(intent.getAction())) {
            return;
        }
        int result = getResultCode();
        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                handleDownloadResult(appContext, intent, result);
            } catch (RuntimeException ex) {
                MmsDebugStore.record(appContext, "MMS callback worker failed: " + ex.getClass().getSimpleName());
            } finally {
                pendingResult.finish();
            }
        }, "crow-mms-download-result").start();
    }

    static synchronized void handleDownloadResult(Context context, Intent intent, int result) {
        String resultDescription = explainResult(result, intent);
        MmsDebugStore.record(context, "MMS download callback: " + resultDescription);
        String id = intent == null ? "" : intent.getStringExtra(EXTRA_DOWNLOAD_ID);
        LocalMmsStore.Pending pending = LocalMmsStore.pending(context, id);
        if (TextUtils.isEmpty(pending.pduPath)) {
            if (!TextUtils.isEmpty(id)) {
                MmsDebugStore.record(context, "MMS callback had no pending file path.");
                LocalMmsStore.clearPending(context, id);
            }
            return;
        }
        String conversationAddress = pending.address;
        boolean clearPending = true;
        boolean deletePendingFile = true;

        try {
            File pduFile = new File(pending.pduPath);
            boolean hasDownloadedFile = pduFile.exists() && pduFile.length() > 0;
            if (result != Activity.RESULT_OK && !hasDownloadedFile) {
                if (retryPendingDownload(context, id, pending, result, resultDescription)) {
                    clearPending = false;
                    deletePendingFile = false;
                    return;
                }
                saveDownloadFailure(context, conversationAddress, "MMS download failed before file write. " + resultDescription);
                return;
            }
            if (!hasDownloadedFile) {
                saveDownloadFailure(context, conversationAddress, "MMS callback file missing. " + resultDescription);
                return;
            }
            if (result != Activity.RESULT_OK) {
                MmsDebugStore.record(context, "MMS callback was not OK, but downloaded file exists. Trying to save picture.");
            }
            conversationAddress = saveDownloadedPdu(context, id, pending);
        } catch (Exception ignored) {
            MmsDebugStore.record(context, "MMS callback failed: " + ignored.getClass().getSimpleName());
            LocalMmsStore.saveNotice(context, conversationAddress, LocalMmsStore.DISPLAY_FAILED_MESSAGE, System.currentTimeMillis());
            MessageNotifier.showIncoming(context, conversationAddress, LocalMmsStore.DISPLAY_FAILED_MESSAGE);
        } finally {
            if (clearPending) {
                LocalMmsStore.clearPending(context, id);
            }
            if (deletePendingFile) {
                deletePendingDownload(context, pending.pduPath);
            }
        }

        MessageUpdateBroadcaster.broadcast(context, conversationAddress);
    }

    private static boolean retryPendingDownload(Context context, String id, LocalMmsStore.Pending pending, int result, String resultDescription) {
        if (!shouldRetryDownload(result, pending)) {
            return false;
        }
        int retryCount = LocalMmsStore.incrementPendingRetry(context, id);
        if (retryCount <= 0 || retryCount > MAX_DOWNLOAD_RETRIES) {
            return false;
        }
        try {
            requestCarrierDownload(context, id, pending.downloadUrl, new File(pending.pduPath), pending.subscriptionId);
            MmsDebugStore.record(context, "Retrying MMS download. attempt=" + retryCount + ", previous=" + resultDescription);
            return true;
        } catch (Exception ex) {
            MmsDebugStore.record(context, "MMS retry start failed: " + ex.getClass().getSimpleName());
            return false;
        }
    }

    static boolean shouldRetryDownload(int result, LocalMmsStore.Pending pending) {
        if (pending == null
                || TextUtils.isEmpty(pending.downloadUrl)
                || TextUtils.isEmpty(pending.pduPath)
                || pending.retryCount >= MAX_DOWNLOAD_RETRIES) {
            return false;
        }
        switch (result) {
            case SmsManager.MMS_ERROR_RETRY:
            case SmsManager.MMS_ERROR_HTTP_FAILURE:
            case SmsManager.MMS_ERROR_IO_ERROR:
            case SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS:
            case SmsManager.MMS_ERROR_NO_DATA_NETWORK:
            case SmsManager.MMS_ERROR_UNSPECIFIED:
                return true;
            default:
                return false;
        }
    }

    static void requestCarrierDownload(Context context, String id, String downloadUrl, File pduFile, int subscriptionId) {
        Uri pduUri = Uri.parse("content://" + MmsFileProvider.AUTHORITY + "/" + pduFile.getName());
        Intent complete = new Intent(context, MmsDownloadedReceiver.class);
        complete.setAction(ACTION_MMS_DOWNLOADED);
        complete.putExtra(EXTRA_DOWNLOAD_ID, id);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                AddressUtil.stableId(id),
                complete,
                downloadCallbackPendingIntentFlags()
        );

        smsManager(subscriptionId).downloadMultimediaMessage(
                context,
                downloadUrl,
                pduUri,
                null,
                pendingIntent
        );
    }

    private static void saveDownloadFailure(Context context, String address, String debugMessage) {
        MmsDebugStore.record(context, debugMessage);
        LocalMmsStore.saveNotice(context, address, LocalMmsStore.DOWNLOAD_FAILED_MESSAGE, System.currentTimeMillis());
        MessageNotifier.showIncoming(context, address, LocalMmsStore.DOWNLOAD_FAILED_MESSAGE);
        MessageUpdateBroadcaster.broadcast(context, address);
    }

    static synchronized void recoverPendingDownloads(Context context) {
        for (String id : LocalMmsStore.pendingIds(context)) {
            LocalMmsStore.Pending pending = LocalMmsStore.pending(context, id);
            if (TextUtils.isEmpty(pending.pduPath)) {
                MmsDebugStore.record(context, "Clearing stale pending MMS with no file path.");
                LocalMmsStore.clearPending(context, id);
                continue;
            }
            File pendingFile = new File(pending.pduPath);
            if (!pendingFile.exists()) {
                if (!isStalePendingDownload(pending)) {
                    continue;
                }
                saveDownloadFailure(context, pending.address, "Clearing stale pending MMS with missing file.");
                LocalMmsStore.clearPending(context, id);
                continue;
            }
            if (pendingFile.length() == 0) {
                if (!isStalePendingDownload(pending)) {
                    continue;
                }
                saveDownloadFailure(context, pending.address, "Clearing stale pending MMS with empty file.");
                LocalMmsStore.clearPending(context, id);
                deletePendingDownload(context, pending.pduPath);
                continue;
            }
            if (!isStalePendingDownload(pending)) {
                MmsDebugStore.record(context, "Pending MMS file is still within the carrier write grace period.");
                continue;
            }
            try {
                MmsDebugStore.record(context, "Recovering pending MMS download.");
                String address = saveDownloadedPdu(context, id, pending);
                LocalMmsStore.clearPending(context, id);
                deletePendingDownload(context, pending.pduPath);
                MessageUpdateBroadcaster.broadcast(context, address);
            } catch (Exception ignored) {
                MmsDebugStore.record(context, "Pending MMS recovery failed: " + ignored.getClass().getSimpleName());
                archiveUnreadablePdu(context, id, pending.pduPath);
                LocalMmsStore.saveNotice(context, pending.address, LocalMmsStore.DISPLAY_FAILED_MESSAGE, System.currentTimeMillis());
                MessageNotifier.showIncoming(context, pending.address, LocalMmsStore.DISPLAY_FAILED_MESSAGE);
                LocalMmsStore.clearPending(context, id);
                deletePendingDownload(context, pending.pduPath);
                MessageUpdateBroadcaster.broadcast(context, pending.address);
            }
        }
    }

    private static boolean isStalePendingDownload(LocalMmsStore.Pending pending) {
        return pending.createdAtMillis <= 0
                || pending.createdAtMillis <= System.currentTimeMillis() - PENDING_DOWNLOAD_GRACE_MILLIS;
    }

    private static String saveDownloadedPdu(Context context, String id, LocalMmsStore.Pending pending) throws Exception {
        String conversationAddress = pending.address;
        byte[] pdu = Files.readAllBytes(new File(pending.pduPath).toPath());
        archiveDownloadedPdu(context, id, pdu);
        String senderAddress = downloadedSenderAddress(pdu, pending.address);
        List<String> participants = safeParticipants(context, pdu, senderAddress);
        addParticipant(participants, pending.address);
        addParticipant(participants, senderAddress);
        conversationAddress = conversationAddressForDownloadedMms(context, pending.address, senderAddress, participants);
        int savedParticipantCount = LocalMmsStore.participantsForAddress(conversationAddress).size();
        MmsDebugStore.record(context, "MMS participants raw=" + participants.size()
                + ", saved=" + savedParticipantCount
                + ", group=" + LocalMmsStore.isGroupAddress(conversationAddress)
                + ", senderKnown=" + !TextUtils.isEmpty(senderAddress));
        byte[] image = safeImage(context, pdu);
        String text = cleanDownloadedText(safeText(context, pdu));
        MmsDebugStore.record(context, "MMS extraction imageBytes=" + image.length
                + ", textLength=" + (TextUtils.isEmpty(text) ? 0 : text.length()));
        if (image.length > 0) {
            File outputDir = MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR);
            File imageFile = new File(outputDir, id + imageExtension(image));
            try (FileOutputStream stream = new FileOutputStream(imageFile)) {
                stream.write(image);
            }
            LocalMmsStore.saveImage(context, conversationAddress, senderAddress, text, Uri.fromFile(imageFile).toString(), System.currentTimeMillis());
            MessageNotifier.showIncoming(context, conversationAddress, senderAddress, TextUtils.isEmpty(text) ? LocalMmsStore.PICTURE_MESSAGE : text);
            MmsDebugStore.record(context, "MMS image extracted and saved. text=" + !TextUtils.isEmpty(text));
        } else if (!TextUtils.isEmpty(text)) {
            LocalMmsStore.saveNotice(context, conversationAddress, senderAddress, text, System.currentTimeMillis());
            MessageNotifier.showIncoming(context, conversationAddress, senderAddress, text);
            MmsDebugStore.record(context, "MMS text extracted and saved.");
        } else {
            MmsDebugStore.record(context, "MMS callback had no extractable image. bytes=" + pdu.length);
            archiveUnreadablePdu(context, id, pending.pduPath);
            LocalMmsStore.saveNotice(context, conversationAddress, senderAddress, LocalMmsStore.UNREADABLE_MESSAGE, System.currentTimeMillis());
            MessageNotifier.showIncoming(context, conversationAddress, senderAddress, LocalMmsStore.UNREADABLE_MESSAGE);
        }
        return conversationAddress;
    }

    private static List<String> safeParticipants(Context context, byte[] pdu, String senderAddress) {
        try {
            return new ArrayList<>(MmsPduUtil.extractParticipants(pdu, senderAddress));
        } catch (RuntimeException ex) {
            MmsDebugStore.record(context, "MMS participant parsing skipped: " + ex.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    private static byte[] safeImage(Context context, byte[] pdu) {
        try {
            return MmsPduUtil.extractFirstImage(pdu);
        } catch (RuntimeException ex) {
            MmsDebugStore.record(context, "MMS image parsing failed: " + ex.getClass().getSimpleName());
            return new byte[0];
        }
    }

    private static String safeText(Context context, byte[] pdu) {
        try {
            return MmsPduUtil.extractText(pdu);
        } catch (RuntimeException ex) {
            MmsDebugStore.record(context, "MMS caption parsing skipped: " + ex.getClass().getSimpleName());
            return "";
        }
    }

    static String cleanDownloadedText(String text) {
        return MmsPduUtil.cleanDisplayText(text);
    }

    private static void addParticipant(List<String> participants, String address) {
        String normalized = LocalMmsStore.normalizedParticipantAddress(address);
        if (TextUtils.isEmpty(normalized)) {
            return;
        }
        for (String participant : participants) {
            if (TextUtils.equals(LocalMmsStore.normalizedParticipantAddress(participant), normalized)) {
                return;
            }
        }
        participants.add(normalized);
    }

    static String conversationAddressForDownloadedMms(
            Context context,
            String pendingAddress,
            String senderAddress,
            List<String> participants
    ) {
        String fallbackAddress = pendingAddress;
        if (!AddressUtil.hasSinglePhoneAddress(fallbackAddress)
                && !TextUtils.isEmpty(LocalMmsStore.normalizedParticipantAddress(senderAddress))) {
            fallbackAddress = senderAddress;
        }
        return LocalMmsStore.bestConversationAddress(context, fallbackAddress, participants);
    }

    static int downloadCallbackPendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
    }

    private static String downloadedSenderAddress(byte[] pdu, String fallbackAddress) {
        String sender = MmsPduUtil.findSender(pdu);
        String normalizedSender = LocalMmsStore.normalizedParticipantAddress(sender);
        if (!TextUtils.isEmpty(normalizedSender)) {
            return normalizedSender;
        }
        return LocalMmsStore.normalizedParticipantAddress(fallbackAddress);
    }

    private static String imageExtension(byte[] image) {
        if (image != null && image.length >= 12
                && image[0] == 0x52 && image[1] == 0x49 && image[2] == 0x46 && image[3] == 0x46
                && image[8] == 0x57 && image[9] == 0x45 && image[10] == 0x42 && image[11] == 0x50) {
            return ".webp";
        }
        if (image != null && image.length >= 4
                && (image[0] & 0xFF) == 0x89 && image[1] == 0x50 && image[2] == 0x4E && image[3] == 0x47) {
            return ".png";
        }
        return ".jpg";
    }

    private static void archiveUnreadablePdu(Context context, String id, String pduPath) {
        if (!MmsDebugStore.shouldArchiveRawPdus(context)
                || TextUtils.isEmpty(id)
                || TextUtils.isEmpty(pduPath)) {
            return;
        }
        try {
            File source = new File(pduPath);
            if (!source.exists()) {
                return;
            }
            File outputDir = MmsFiles.appFileDir(context, MmsFiles.UNREADABLE_DIR);
            Files.copy(source.toPath(), new File(outputDir, id + ".pdu").toPath(), StandardCopyOption.REPLACE_EXISTING);
            MmsDebugStore.trimArchivedPduFiles(context);
            MmsDebugStore.record(context, "Archived unreadable MMS for debugging. file=" + id + ".pdu");
        } catch (Exception ignored) {
            MmsDebugStore.record(context, "Could not archive unreadable MMS: " + ignored.getClass().getSimpleName());
        }
    }

    private static void archiveDownloadedPdu(Context context, String id, byte[] pdu) {
        if (!MmsDebugStore.shouldArchiveRawPdus(context)
                || TextUtils.isEmpty(id)
                || pdu == null
                || pdu.length == 0) {
            return;
        }
        try {
            File outputDir = MmsFiles.appFileDir(context, MmsFiles.RAW_DOWNLOADS_DIR);
            File outputFile = new File(outputDir, id + ".pdu");
            try (FileOutputStream stream = new FileOutputStream(outputFile)) {
                stream.write(pdu);
            }
            MmsDebugStore.trimArchivedPduFiles(context);
            MmsDebugStore.record(context, "Archived downloaded MMS for debugging. file=" + outputFile.getName());
        } catch (Exception ignored) {
            MmsDebugStore.record(context, "Could not archive downloaded MMS: " + ignored.getClass().getSimpleName());
        }
    }

    private static void deletePendingDownload(Context context, String pduPath) {
        MmsFiles.deleteAppFile(context, MmsFiles.DOWNLOADS_DIR, pduPath);
    }

    private static String explainResult(int result, Intent intent) {
        String label;
        switch (result) {
            case Activity.RESULT_OK:
                label = "OK";
                break;
            case SmsManager.MMS_ERROR_UNSPECIFIED:
                label = "MMS_ERROR_UNSPECIFIED";
                break;
            case SmsManager.MMS_ERROR_INVALID_APN:
                label = "MMS_ERROR_INVALID_APN";
                break;
            case SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS:
                label = "MMS_ERROR_UNABLE_CONNECT_MMS";
                break;
            case SmsManager.MMS_ERROR_HTTP_FAILURE:
                label = "MMS_ERROR_HTTP_FAILURE";
                break;
            case SmsManager.MMS_ERROR_IO_ERROR:
                label = "MMS_ERROR_IO_ERROR";
                break;
            case SmsManager.MMS_ERROR_RETRY:
                label = "MMS_ERROR_RETRY";
                break;
            case SmsManager.MMS_ERROR_CONFIGURATION_ERROR:
                label = "MMS_ERROR_CONFIGURATION_ERROR";
                break;
            case SmsManager.MMS_ERROR_NO_DATA_NETWORK:
                label = "MMS_ERROR_NO_DATA_NETWORK";
                break;
            default:
                label = "result=" + result;
                break;
        }

        int httpStatus = intent == null ? 0 : intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0);
        byte[] response = intent == null ? null : intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA);
        return label + ", http=" + httpStatus + ", responseBytes=" + (response == null ? 0 : response.length);
    }

    private static SmsManager smsManager(int subscriptionId) {
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
        }
        return SmsManager.getDefault();
    }
}
