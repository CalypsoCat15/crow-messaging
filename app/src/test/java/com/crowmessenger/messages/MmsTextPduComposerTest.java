package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsTextPduComposerTest {
    @Test
    public void composedGroupMmsKeepsRecipientsAndBodyReadableByCurrentParser() {
        byte[] pdu = MmsTextPduComposer.compose(
                "tx-1",
                Arrays.asList("+1 (555) 010-1000", "555-010-2000"),
                "Group reply"
        );

        List<String> participants = MmsPduUtil.extractParticipants(pdu, "");

        assertTrue(participants.contains("15550101000"));
        assertTrue(participants.contains("5550102000"));
        assertEquals("Group reply", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void composedMmsSupportsEmailRecipient() {
        byte[] pdu = MmsTextPduComposer.compose(
                "tx-email",
                Arrays.asList("person@example.com"),
                "Email route"
        );

        List<String> participants = MmsPduUtil.extractParticipants(pdu, "");

        assertTrue(participants.contains("person@example.com"));
        assertEquals("Email route", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void composedMmsDedupesPhoneNumberFormats() {
        byte[] pdu = MmsTextPduComposer.compose(
                "tx-dedupe",
                Arrays.asList("+1 (555) 010-2000", "5550102000", "555-010-3000"),
                "No duplicate recipients"
        );

        List<String> participants = MmsPduUtil.extractParticipants(pdu, "");

        assertEquals(2, participants.size());
        assertTrue(participants.contains("15550102000"));
        assertTrue(participants.contains("5550103000"));
        assertEquals("No duplicate recipients", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void composedMmsDropsInvalidRecipientsButKeepsBody() {
        byte[] pdu = MmsTextPduComposer.compose(
                "tx-empty",
                Arrays.asList("", "MMS", "Family", "thread:1234567", "person @example.com", "555-010-2000"),
                "Still sends to valid people"
        );

        List<String> participants = MmsPduUtil.extractParticipants(pdu, "");

        assertEquals(1, participants.size());
        assertEquals("5550102000", participants.get(0));
        assertEquals("Still sends to valid people", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void composedImageMmsKeepsCaptionAndImageReadableByCurrentParser() {
        byte[] image = new byte[] { (byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9 };

        byte[] pdu = MmsTextPduComposer.composeImage(
                "tx-image",
                Arrays.asList("555-010-2000"),
                "Picture caption",
                image
        );

        assertEquals("Picture caption", MmsPduUtil.extractText(pdu));
        assertArrayEquals(image, MmsPduUtil.extractFirstImage(pdu));
    }

    @Test
    public void composedImageMmsWithoutCaptionKeepsTextEmptyAndImageReadable() {
        byte[] image = new byte[] { (byte) 0xFF, (byte) 0xD8, 7, 8, 9, (byte) 0xFF, (byte) 0xD9 };

        byte[] pdu = MmsTextPduComposer.composeImage(
                "tx-image-only",
                Arrays.asList("555-010-2000"),
                "",
                image
        );

        assertEquals("", MmsPduUtil.extractText(pdu));
        assertArrayEquals(image, MmsPduUtil.extractFirstImage(pdu));
    }
}
