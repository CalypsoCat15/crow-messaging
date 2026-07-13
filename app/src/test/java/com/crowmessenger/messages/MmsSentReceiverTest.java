package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsSentReceiverTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void deleteOutgoingPdu_removesOnlySafePduNames() throws Exception {
        File pdu = new File(MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR), "send-test.pdu");
        try (FileOutputStream output = new FileOutputStream(pdu)) {
            output.write(new byte[] { 1, 2, 3 });
        }

        MmsSentReceiver.deleteOutgoingPdu(context, pdu.getName());

        assertFalse(pdu.exists());
        assertTrue(MmsSentReceiver.isSafePduName("message.pdu"));
        assertFalse(MmsSentReceiver.isSafePduName("../message.pdu"));
        assertFalse(MmsSentReceiver.isSafePduName("message.jpg"));
    }

    @Test
    public void handleSentResult_marksMatchingGroupTextFailedAndDeletesPdu() throws Exception {
        String group = LocalMmsStore.outgoingGroupAddress(java.util.List.of("15551234567", "15557654321"));
        String messageId = "group-send-result";
        LocalMmsStore.saveSentText(context, messageId, group, "hello", 1000L);
        File pdu = createOutgoingPdu(messageId + ".pdu");
        Intent intent = resultIntent(pdu.getName(), group)
                .putExtra(MmsSentReceiver.EXTRA_LOCAL_MESSAGE_ID, messageId);

        assertTrue(MmsSentReceiver.handleSentResult(context, intent, Activity.RESULT_CANCELED));

        assertFalse(pdu.exists());
        assertEquals(ChatMessage.STATUS_FAILED, LocalMmsStore.loadForAddress(context, group).get(0).status);
    }

    @Test
    public void handleSentResult_marksPictureByExactIdWhenImageUrisMatch() throws Exception {
        String address = "15551234567";
        String imageUri = "file:///tmp/shared-callback-image.jpg";
        assertTrue(LocalMmsStore.saveSentImage(
                context,
                "first-picture-callback",
                address,
                "first",
                imageUri,
                1000L
        ));
        assertTrue(LocalMmsStore.saveSentImage(
                context,
                "second-picture-callback",
                address,
                "second",
                imageUri,
                2000L
        ));
        File pdu = createOutgoingPdu("second-picture-callback.pdu");
        Intent intent = resultIntent(pdu.getName(), address)
                .putExtra(MmsSentReceiver.EXTRA_IMAGE_URI, imageUri)
                .putExtra(MmsSentReceiver.EXTRA_LOCAL_MESSAGE_ID, "second-picture-callback");

        assertTrue(MmsSentReceiver.handleSentResult(context, intent, Activity.RESULT_CANCELED));

        assertFalse(pdu.exists());
        java.util.List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, address);
        assertEquals(2, messages.size());
        assertEquals("first-picture-callback", messages.get(0).localStatusId);
        assertEquals("", messages.get(0).status);
        assertEquals("second-picture-callback", messages.get(1).localStatusId);
        assertEquals(ChatMessage.STATUS_FAILED, messages.get(1).status);
    }

    @Test
    public void handleSentResult_usesImageUriForLegacyPictureCallbackWithoutId() {
        String address = "15551234567";
        String imageUri = "file:///tmp/legacy-callback-image.jpg";
        LocalMmsStore.saveSentImage(context, address, "legacy", imageUri, 1000L);
        Intent intent = resultIntent("legacy-picture-callback.pdu", address)
                .putExtra(MmsSentReceiver.EXTRA_IMAGE_URI, imageUri);

        assertTrue(MmsSentReceiver.handleSentResult(context, intent, Activity.RESULT_CANCELED));

        java.util.List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, address);
        assertEquals(1, messages.size());
        assertEquals(ChatMessage.STATUS_FAILED, messages.get(0).status);
    }

    @Test
    public void handleSentResult_ignoresFailureWithoutMatchingLocalMessage() {
        Intent intent = resultIntent("missing.pdu", "15551234567")
                .putExtra(MmsSentReceiver.EXTRA_IMAGE_URI, "file:///missing.jpg");

        assertFalse(MmsSentReceiver.handleSentResult(context, intent, Activity.RESULT_CANCELED));
        assertTrue(LocalMmsStore.loadForAddress(context, "15551234567").isEmpty());
    }

    @Test
    public void handleSentResult_successDeletesPduWithoutChangingMessageStatus() throws Exception {
        String group = LocalMmsStore.outgoingGroupAddress(java.util.List.of("15551234567", "15557654321"));
        String messageId = "successful-group-send";
        LocalMmsStore.saveSentText(context, messageId, group, "hello", 1000L);
        File pdu = createOutgoingPdu(messageId + ".pdu");
        Intent intent = resultIntent(pdu.getName(), group)
                .putExtra(MmsSentReceiver.EXTRA_LOCAL_MESSAGE_ID, messageId);

        assertFalse(MmsSentReceiver.handleSentResult(context, intent, Activity.RESULT_OK));

        assertFalse(pdu.exists());
        assertEquals("", LocalMmsStore.loadForAddress(context, group).get(0).status);
    }

    private Intent resultIntent(String pduName, String address) {
        return new Intent(context, MmsSentReceiver.class)
                .setAction(MmsSentReceiver.ACTION_MMS_SENT)
                .putExtra(MmsSentReceiver.EXTRA_PDU_NAME, pduName)
                .putExtra(MmsSentReceiver.EXTRA_ADDRESS, address);
    }

    private File createOutgoingPdu(String name) throws Exception {
        File pdu = new File(MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR), name);
        try (FileOutputStream output = new FileOutputStream(pdu)) {
            output.write(new byte[] { 1, 2, 3 });
        }
        return pdu;
    }
}
