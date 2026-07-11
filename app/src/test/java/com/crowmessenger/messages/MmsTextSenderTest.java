package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsTextSenderTest {
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
}
