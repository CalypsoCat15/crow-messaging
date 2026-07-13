package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class GroupMmsRecipientsTest {
    @Test
    public void remoteRecipientsDropsOwnNumberBeforeGroupSend() {
        String group = LocalMmsStore.conversationAddress("", Arrays.asList(
                "+1 (555) 010-1000",
                "555-010-2000",
                "+1 555 010 3000"
        ));

        List<String> recipients = GroupMmsRecipients.remoteRecipients(
                group,
                new HashSet<>(Arrays.asList("15550101000"))
        );

        assertEquals(Arrays.asList("15550103000", "5550102000"), recipients);
    }

    @Test
    public void remoteRecipientsKeepsStableUniqueOrder() {
        String group = LocalMmsStore.conversationAddress("", Arrays.asList(
                "555-010-2000",
                "5550102000",
                "person@example.com",
                "PERSON@example.com",
                "555-010-3000"
        ));

        List<String> recipients = GroupMmsRecipients.remoteRecipients(group, new HashSet<>());

        assertEquals(Arrays.asList("5550102000", "5550103000", "person@example.com"), recipients);
    }

    @Test
    public void remoteRecipientsDedupesCountryCodeVariants() {
        String group = LocalMmsStore.conversationAddress("", Arrays.asList(
                "+1 (555) 010-2000",
                "5550102000",
                "555-010-3000"
        ));

        List<String> recipients = GroupMmsRecipients.remoteRecipients(group, new HashSet<>());

        assertEquals(Arrays.asList("15550102000", "5550103000"), recipients);
    }

    @Test
    public void normalizedRecipientRejectsThreadKeysBeforeDigits() {
        assertEquals("", GroupMmsRecipients.normalizedRecipient("thread:1234567"));
        assertEquals("", GroupMmsRecipients.normalizedRecipient("ALERT5551234"));
        assertEquals("person5551234@example.com",
                GroupMmsRecipients.normalizedRecipient("PERSON5551234@example.com"));
    }

    @Test
    public void remoteRecipients_doNotMergeEmailDigitsWithPhoneNumber() {
        String group = LocalMmsStore.conversationAddress("", Arrays.asList(
                "5551234",
                "person5551234@example.com",
                "5559876"
        ));

        List<String> recipients = GroupMmsRecipients.remoteRecipients(group, new HashSet<>());

        assertTrue(recipients.contains("5551234"));
        assertTrue(recipients.contains("person5551234@example.com"));
        assertTrue(recipients.contains("5559876"));
        assertEquals(3, recipients.size());
    }

    @Test
    public void hasEnoughRecipientsRequiresAtLeastTwoRemotePeople() {
        assertFalse(GroupMmsRecipients.hasEnoughRecipientsForGroupMms(Arrays.asList("5550102000")));
        assertTrue(GroupMmsRecipients.hasEnoughRecipientsForGroupMms(Arrays.asList("5550102000", "5550103000")));
    }

    @Test
    public void totalPeopleCount_includesCurrentUserForRemoteOnlyIdentity() {
        Context context = RuntimeEnvironment.getApplication();
        String group = LocalMmsStore.outgoingGroupAddress(Arrays.asList("5550102000", "5550103000"));

        assertEquals(3, GroupMmsRecipients.totalPeopleCount(context, group));
    }
}
