package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsDeliverReceiverTest {
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
    public void noticeConversationAddress_usesMmsFallbackWhenSenderMissing() {
        assertEquals("MMS", MmsDeliverReceiver.noticeConversationAddress(""));
        assertEquals("MMS", MmsDeliverReceiver.noticeConversationAddress(null));
    }

    @Test
    public void noticeConversationAddress_keepsParsedSender() {
        assertEquals("15551234567", MmsDeliverReceiver.noticeConversationAddress("15551234567"));
    }

    @Test
    public void newMmsDownloadId_isRandomUuidSafeForFileName() {
        String first = MmsDeliverReceiver.newMmsDownloadId();
        String second = MmsDeliverReceiver.newMmsDownloadId();

        assertNotEquals(first, second);
        assertTrue(first.matches("[0-9a-f\\-]{36}"));
        assertTrue(second.matches("[0-9a-f\\-]{36}"));
    }

    @Test
    public void diagnosticDescriptions_doNotExposeSenderOrPrivateUrlParts() {
        String privateUrl = "https://carrier.example/private/account-token?message=secret";

        String description = MmsDeliverReceiver.describeUrl(privateUrl);

        assertFalse(description.contains("carrier.example"));
        assertFalse(description.contains("account-token"));
        assertFalse(description.contains("secret"));
        assertEquals("phone", MmsDeliverReceiver.describeSender("15551234567"));
        assertEquals("email", MmsDeliverReceiver.describeSender("private@example.com"));
        assertEquals("sender-id", MmsDeliverReceiver.describeSender("CROW ALERTS"));
    }

    @Test
    public void cleanupFailedDownloadStart_clearsPendingDownloadAndFile() throws Exception {
        File downloadDir = MmsFiles.appFileDir(context, MmsFiles.DOWNLOADS_DIR);
        File pduFile = new File(downloadDir, "failed-start.pdu");
        assertTrue(pduFile.createNewFile());
        LocalMmsStore.savePending(context, "failed-start", "15551234567", pduFile.getAbsolutePath());

        MmsDeliverReceiver.cleanupFailedDownloadStart(context, "failed-start", pduFile);

        assertEquals("", LocalMmsStore.pending(context, "failed-start").pduPath);
        assertFalse(pduFile.exists());
    }

    @Test
    public void cleanupFailedDownloadStart_doesNotDeleteOutsideFile() throws Exception {
        File outside = new File(context.getFilesDir(), "outside-failed-start.pdu");
        assertTrue(outside.createNewFile());
        LocalMmsStore.savePending(context, "outside-failed-start", "15551234567", outside.getAbsolutePath());

        MmsDeliverReceiver.cleanupFailedDownloadStart(context, "outside-failed-start", outside);

        assertEquals("", LocalMmsStore.pending(context, "outside-failed-start").pduPath);
        assertTrue(outside.exists());
    }
}
