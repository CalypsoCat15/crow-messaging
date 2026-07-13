package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsTextSenderTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void normalizedRecipients_dedupesFormatsAndDropsPlaceholders() {
        assertEquals(
                List.of("15551234567", "15557654321"),
                MmsTextSender.normalizedRecipients(Arrays.asList(
                        "+1 (555) 123-4567",
                        "5551234567",
                        "MMS",
                        "15557654321"
                ))
        );
    }

    @Test
    public void sendAndRecord_rejectsNonGroupConversationBeforeCarrierSend() {
        Context context = RuntimeEnvironment.getApplication();

        SmsSender.SendException exception = assertThrows(
                SmsSender.SendException.class,
                () -> MmsTextSender.sendAndRecord(
                        context,
                        "15551234567",
                        List.of("15551234567", "15557654321"),
                        "hello"
                )
        );

        assertEquals("Group text needs a group conversation.", exception.getMessage());
    }

    @Test
    public void sendAndRecord_rejectsTooFewRecipientsBeforeCarrierSend() {
        Context context = RuntimeEnvironment.getApplication();
        String group = LocalMmsStore.outgoingGroupAddress(List.of("15551234567", "15557654321"));

        SmsSender.SendException exception = assertThrows(
                SmsSender.SendException.class,
                () -> MmsTextSender.sendAndRecord(
                        context,
                        group,
                        List.of("15551234567"),
                        "hello"
                )
        );

        assertEquals("Crow Messenger could not find enough people in this group.", exception.getMessage());
    }

    @Test
    public void sendAndRecord_durablySavesExactRowBeforeCarrierCall() throws Exception {
        String group = LocalMmsStore.outgoingGroupAddress(List.of("15551234567", "15557654321"));
        boolean[] carrierCalled = { false };
        String[] sentId = { "" };
        String[] pduName = { "" };

        long sentAt = MmsTextSender.sendAndRecord(
                context,
                group,
                List.of("15551234567", "15557654321"),
                "hello group",
                (sendContext, pduUri, sentIntent) -> {
                    carrierCalled[0] = true;
                    android.content.Intent callbackIntent = Shadows.shadowOf(sentIntent).getSavedIntent();
                    sentId[0] = callbackIntent.getStringExtra(MmsSentReceiver.EXTRA_LOCAL_MESSAGE_ID);
                    pduName[0] = pduUri.getLastPathSegment();

                    List<ChatMessage> messages = LocalMmsStore.loadForAddress(sendContext, group);
                    assertEquals(1, messages.size());
                    assertEquals(sentId[0], messages.get(0).localStatusId);
                    assertEquals("hello group", messages.get(0).body);
                    assertTrue(sendContext.getSharedPreferences("local_mms", Context.MODE_PRIVATE)
                            .getStringSet("ids", Set.of())
                            .contains(sentId[0]));
                    assertTrue(new File(
                            MmsFiles.appFileDirPath(sendContext, MmsFiles.OUTGOING_DIR),
                            pduName[0]
                    ).exists());
                }
        );

        assertTrue(carrierCalled[0]);
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, group);
        assertEquals(1, messages.size());
        assertEquals(sentAt, messages.get(0).dateMillis);
        assertTrue(LocalMmsStore.rollbackSentMessage(context, sentId[0], group));
        MmsSentReceiver.deleteOutgoingPdu(context, pduName[0]);
    }

    @Test
    public void sendAndRecord_synchronousCarrierExceptionRollsBackRowAndPdu() throws Exception {
        String group = "group:15557654321|15559876543|15551234567";
        String canonicalGroup = LocalMmsStore.outgoingGroupAddress(
                List.of("15551234567", "15557654321", "15559876543")
        );
        Set<String> pduNamesBefore = fileNames(MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR));
        boolean[] sawSavedRow = { false };

        SmsSender.SendException exception = assertThrows(
                SmsSender.SendException.class,
                () -> MmsTextSender.sendAndRecord(
                        context,
                        group,
                        List.of("15551234567", "15557654321", "15559876543"),
                        "carrier failure",
                        (sendContext, pduUri, sentIntent) -> {
                            String id = Shadows.shadowOf(sentIntent)
                                    .getSavedIntent()
                                    .getStringExtra(MmsSentReceiver.EXTRA_LOCAL_MESSAGE_ID);
                            List<ChatMessage> messages = LocalMmsStore.loadForAddress(sendContext, canonicalGroup);
                            sawSavedRow[0] = messages.size() == 1
                                    && id.equals(messages.get(0).localStatusId);
                            throw new SecurityException("carrier rejected");
                        }
                )
        );

        assertEquals("carrier rejected", exception.getMessage());
        assertTrue(sawSavedRow[0]);
        assertTrue(LocalMmsStore.loadForAddress(context, group).isEmpty());
        assertTrue(LocalMmsStore.loadForAddress(context, canonicalGroup).isEmpty());
        assertEquals(pduNamesBefore, fileNames(MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR)));
    }

    private static Set<String> fileNames(File directory) {
        Set<String> names = new HashSet<>();
        File[] files = directory.listFiles(File::isFile);
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        return names;
    }
}
