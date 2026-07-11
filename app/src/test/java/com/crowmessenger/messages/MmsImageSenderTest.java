package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsImageSenderTest {
    @Test
    public void recipientsForAddress_usesSingleRecipientForOneToOneThread() throws Exception {
        assertEquals(
                Arrays.asList("5550102000"),
                MmsImageSender.recipientsForAddress("5550102000", new HashSet<>())
        );
    }

    @Test
    public void recipientsForAddress_usesRemoteGroupRecipients() throws Exception {
        String group = LocalMmsStore.conversationAddress("", Arrays.asList(
                "15550101000",
                "5550102000",
                "5550103000"
        ));

        List<String> recipients = MmsImageSender.recipientsForAddress(
                group,
                new HashSet<>(Arrays.asList("15550101000"))
        );

        assertEquals(Arrays.asList("5550102000", "5550103000"), recipients);
    }

    @Test
    public void recipientsForAddress_rejectsGroupWithTooFewRemoteRecipients() {
        String group = LocalMmsStore.conversationAddress("", Arrays.asList(
                "15550101000",
                "5550102000",
                "5550103000"
        ));

        assertThrows(
                SmsSender.SendException.class,
                () -> MmsImageSender.recipientsForAddress(
                        group,
                        new HashSet<>(Arrays.asList("15550101000", "5550102000"))
                )
        );
    }
}
