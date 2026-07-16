package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsDownloadedReceiverTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void recoverPendingDownloads_treatsEmptyFileAsDownloadFailure() throws Exception {
        File downloadDir = MmsFiles.appFileDirPath(context, MmsFiles.DOWNLOADS_DIR);
        assertTrue(downloadDir.exists() || downloadDir.mkdirs());
        File pduFile = new File(downloadDir, "empty-download.pdu");
        assertTrue(pduFile.createNewFile());
        LocalMmsStore.savePending(context, "empty-download", "15551234567", pduFile.getAbsolutePath());
        makePendingOld("empty-download");

        MmsDownloadedReceiver.recoverPendingDownloads(context);

        assertEquals("", LocalMmsStore.pending(context, "empty-download").pduPath);
        assertFalse(pduFile.exists());
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals(1, messages.size());
        assertEquals(LocalMmsStore.DOWNLOAD_FAILED_MESSAGE, messages.get(0).body);
    }

    @Test
    public void recoverPendingDownloads_treatsOldMissingFileAsDownloadFailure() {
        File pduFile = new File(MmsFiles.appFileDirPath(context, MmsFiles.DOWNLOADS_DIR), "missing-download.pdu");
        LocalMmsStore.savePending(context, "missing-download", "15551234567", pduFile.getAbsolutePath());
        makePendingOld("missing-download");

        MmsDownloadedReceiver.recoverPendingDownloads(context);

        assertEquals("", LocalMmsStore.pending(context, "missing-download").pduPath);
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals(1, messages.size());
        assertEquals(LocalMmsStore.DOWNLOAD_FAILED_MESSAGE, messages.get(0).body);
    }

    @Test
    public void recoverPendingDownloads_keepsFreshEmptyFilePending() throws Exception {
        File downloadDir = MmsFiles.appFileDirPath(context, MmsFiles.DOWNLOADS_DIR);
        assertTrue(downloadDir.exists() || downloadDir.mkdirs());
        File pduFile = new File(downloadDir, "fresh-empty-download.pdu");
        assertTrue(pduFile.createNewFile());
        LocalMmsStore.savePending(context, "fresh-empty-download", "15551234567", pduFile.getAbsolutePath());

        MmsDownloadedReceiver.recoverPendingDownloads(context);

        assertEquals(pduFile.getAbsolutePath(), LocalMmsStore.pending(context, "fresh-empty-download").pduPath);
        assertTrue(pduFile.exists());
        assertTrue(LocalMmsStore.loadForAddress(context, "15551234567").isEmpty());
    }

    @Test
    public void recoverPendingDownloads_keepsFreshMissingFilePending() {
        File pduFile = new File(MmsFiles.appFileDirPath(context, MmsFiles.DOWNLOADS_DIR), "fresh-missing-download.pdu");
        LocalMmsStore.savePending(context, "fresh-missing-download", "15551234567", pduFile.getAbsolutePath());

        MmsDownloadedReceiver.recoverPendingDownloads(context);

        assertEquals(pduFile.getAbsolutePath(), LocalMmsStore.pending(context, "fresh-missing-download").pduPath);
        assertTrue(LocalMmsStore.loadForAddress(context, "15551234567").isEmpty());
    }

    @Test
    public void recoverPendingDownloads_keepsFreshPartiallyWrittenFilePending() throws Exception {
        File downloadDir = MmsFiles.appFileDirPath(context, MmsFiles.DOWNLOADS_DIR);
        assertTrue(downloadDir.exists() || downloadDir.mkdirs());
        File pduFile = new File(downloadDir, "fresh-partial-download.pdu");
        try (FileOutputStream output = new FileOutputStream(pduFile)) {
            output.write(new byte[] { 1, 2, 3 });
        }
        LocalMmsStore.savePending(context, "fresh-partial-download", "15551234567", pduFile.getAbsolutePath());

        MmsDownloadedReceiver.recoverPendingDownloads(context);

        assertEquals(pduFile.getAbsolutePath(), LocalMmsStore.pending(context, "fresh-partial-download").pduPath);
        assertTrue(pduFile.exists());
        assertTrue(LocalMmsStore.loadForAddress(context, "15551234567").isEmpty());
    }

    @Test
    public void recoverUnreadableArchives_replacesExistingPlaceholderWithoutDuplicate() throws Exception {
        String address = "15551234567";
        String recoveredText = "\u4f60\u597d\uff01";
        long receivedAt = System.currentTimeMillis();
        File unreadableDir = MmsFiles.appFileDir(context, MmsFiles.UNREADABLE_DIR);
        File[] oldFiles = unreadableDir.listFiles();
        if (oldFiles != null) {
            for (File oldFile : oldFiles) {
                assertTrue(oldFile.delete());
            }
        }
        File archive = new File(unreadableDir, "recover-text.pdu");
        try (FileOutputStream output = new FileOutputStream(archive)) {
            output.write(MmsTextPduComposer.compose(
                    "tx-recover",
                    List.of(address),
                    recoveredText
            ));
        }
        assertTrue(archive.setLastModified(receivedAt));
        LocalMmsStore.saveNotice(
                context,
                address,
                address,
                LocalMmsStore.UNREADABLE_MESSAGE,
                receivedAt
        );

        assertEquals(1, MmsDownloadedReceiver.recoverUnreadableArchives(context));

        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, address);
        assertEquals(1, messages.size());
        assertEquals(recoveredText, messages.get(0).body);
        assertFalse(archive.exists());
    }

    @Test
    public void handleDownloadResult_cleansFailedCallbackOnlyOnce() throws Exception {
        File downloadDir = MmsFiles.appFileDirPath(context, MmsFiles.DOWNLOADS_DIR);
        assertTrue(downloadDir.exists() || downloadDir.mkdirs());
        File pduFile = new File(downloadDir, "callback-failure.pdu");
        assertTrue(pduFile.createNewFile());
        String id = "callback-failure";
        String address = "15551234567";
        LocalMmsStore.savePending(context, id, address, pduFile.getAbsolutePath());
        Intent callback = new Intent(MmsDownloadedReceiver.ACTION_MMS_DOWNLOADED)
                .putExtra(MmsDownloadedReceiver.EXTRA_DOWNLOAD_ID, id);

        MmsDownloadedReceiver.handleDownloadResult(
                context,
                callback,
                SmsManager.MMS_ERROR_HTTP_FAILURE
        );
        MmsDownloadedReceiver.handleDownloadResult(
                context,
                callback,
                SmsManager.MMS_ERROR_HTTP_FAILURE
        );

        assertEquals("", LocalMmsStore.pending(context, id).pduPath);
        assertFalse(pduFile.exists());
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, address);
        assertEquals(1, messages.size());
        assertEquals(LocalMmsStore.DOWNLOAD_FAILED_MESSAGE, messages.get(0).body);
    }

    @Test
    public void shouldRetryDownload_retriesTemporaryCarrierFailures() {
        LocalMmsStore.Pending pending = new LocalMmsStore.Pending(
                "15551234567",
                "/tmp/message.pdu",
                System.currentTimeMillis(),
                "http://mms.example/message",
                1,
                0
        );

        assertTrue(MmsDownloadedReceiver.shouldRetryDownload(SmsManager.MMS_ERROR_HTTP_FAILURE, pending));
        assertTrue(MmsDownloadedReceiver.shouldRetryDownload(SmsManager.MMS_ERROR_NO_DATA_NETWORK, pending));
        assertTrue(MmsDownloadedReceiver.shouldRetryDownload(SmsManager.MMS_ERROR_RETRY, pending));
    }

    @Test
    public void shouldRetryDownload_rejectsPermanentOrExhaustedFailures() {
        LocalMmsStore.Pending retryable = new LocalMmsStore.Pending(
                "15551234567",
                "/tmp/message.pdu",
                System.currentTimeMillis(),
                "http://mms.example/message",
                1,
                0
        );
        LocalMmsStore.Pending exhausted = new LocalMmsStore.Pending(
                "15551234567",
                "/tmp/message.pdu",
                System.currentTimeMillis(),
                "http://mms.example/message",
                1,
                2
        );
        LocalMmsStore.Pending missingUrl = new LocalMmsStore.Pending(
                "15551234567",
                "/tmp/message.pdu",
                System.currentTimeMillis(),
                "",
                1,
                0
        );

        assertFalse(MmsDownloadedReceiver.shouldRetryDownload(SmsManager.MMS_ERROR_INVALID_APN, retryable));
        assertFalse(MmsDownloadedReceiver.shouldRetryDownload(SmsManager.MMS_ERROR_CONFIGURATION_ERROR, retryable));
        assertFalse(MmsDownloadedReceiver.shouldRetryDownload(SmsManager.MMS_ERROR_HTTP_FAILURE, exhausted));
        assertFalse(MmsDownloadedReceiver.shouldRetryDownload(SmsManager.MMS_ERROR_HTTP_FAILURE, missingUrl));
    }

    @Test
    public void conversationAddressForDownloadedMms_usesSenderWhenNoticeHadGenericFallback() {
        String address = MmsDownloadedReceiver.conversationAddressForDownloadedMms(
                context,
                "MMS",
                "15551234567",
                List.of("15551234567")
        );

        assertEquals("15551234567", address);
    }

    @Test
    public void conversationAddressForDownloadedMms_keepsKnownNoticeSender() {
        String address = MmsDownloadedReceiver.conversationAddressForDownloadedMms(
                context,
                "15557654321",
                "15551234567",
                List.of("15551234567")
        );

        assertEquals("15557654321", address);
    }

    @Test
    public void downloadCallbackPendingIntent_isMutableForCarrierResultDetails() {
        int flags = MmsDownloadedReceiver.downloadCallbackPendingIntentFlags();

        assertTrue((flags & PendingIntent.FLAG_UPDATE_CURRENT) != 0);
        assertTrue((flags & PendingIntent.FLAG_MUTABLE) != 0);
        assertFalse((flags & PendingIntent.FLAG_IMMUTABLE) != 0);
    }

    @Test
    public void cleanDownloadedText_dropsLayoutCaptionBeforeNotification() {
        assertEquals("", MmsDownloadedReceiver.cleanDownloadedText("240\" height"));
        assertEquals("", MmsDownloadedReceiver.cleanDownloadedText("text\" width"));
        assertEquals("zzz", MmsDownloadedReceiver.cleanDownloadedText("zzz"));
        assertEquals("Width is 240.", MmsDownloadedReceiver.cleanDownloadedText("Width is 240."));
    }

    private void makePendingOld(String id) {
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE)
                .edit()
                .putLong("pending_created_" + id, System.currentTimeMillis() - 20L * 60L * 1000L)
                .commit();
    }
}
