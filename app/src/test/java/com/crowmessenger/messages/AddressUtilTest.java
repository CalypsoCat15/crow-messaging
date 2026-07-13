package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AddressUtilTest {
    @Test
    public void containsMatchingPhoneValue_matchesFormattedSameNumber() {
        Set<String> values = new HashSet<>();
        values.add("+1 (555) 123-4567");

        assertTrue(AddressUtil.containsMatchingPhoneValue(values, "5551234567"));
    }

    @Test
    public void containsMatchingPhoneValue_doesNotMatchParticipantInsideGroupAddress() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));
        Set<String> values = new HashSet<>();
        values.add(group);

        assertFalse(AddressUtil.containsMatchingPhoneValue(values, "15557654321"));
    }

    @Test
    public void removeMatchingPhoneValues_removesOnlySamePhoneNumber() {
        Set<String> values = new HashSet<>();
        values.add("+1 (555) 123-4567");
        values.add("15557654321");

        AddressUtil.removeMatchingPhoneValues(values, "5551234567");

        assertFalse(values.contains("+1 (555) 123-4567"));
        assertTrue(values.contains("15557654321"));
    }

    @Test
    public void sendableRecipient_acceptsFormattedNumbersAndShortCodes() {
        assertTrue(AddressUtil.isSendableSmsRecipient("+1 (555) 123-4567"));
        assertTrue(AddressUtil.isSendableSmsRecipient("31354"));
    }

    @Test
    public void sendableRecipient_rejectsNamesGroupsAndRecipientLists() {
        assertFalse(AddressUtil.isSendableSmsRecipient("call-me-at-5551234567"));
        assertFalse(AddressUtil.isSendableSmsRecipient("15551234567,15557654321"));
        assertFalse(AddressUtil.isSendableSmsRecipient("group:15551234567|15557654321"));
        assertFalse(AddressUtil.isSendableSmsRecipient("no-number"));
    }

    @Test
    public void sameConversationAddress_matchesSenderIdsIgnoringCaseAndWhitespace() {
        assertTrue(AddressUtil.sameConversationAddress(" Crow Alerts ", "CROW ALERTS"));
        assertFalse(AddressUtil.sameConversationAddress("Crow Alerts", "Crow Support"));
        assertFalse(AddressUtil.sameConversationAddress("ACME2FA", "2"));
        assertEquals("acme2fa", AddressUtil.stableKey(" ACME2FA "));
        assertEquals("2", AddressUtil.stableKey("2"));
    }

    @Test
    public void matchingConversationValues_handlePhonesSenderIdsAndExactGroups() {
        Set<String> values = new HashSet<>(List.of(
                "+1 (555) 123-4567",
                "Crow Alerts",
                "group:one|two"
        ));

        assertTrue(AddressUtil.containsMatchingConversationAddress(values, "5551234567"));
        assertTrue(AddressUtil.containsMatchingConversationAddress(values, "CROW ALERTS"));
        assertTrue(AddressUtil.containsMatchingConversationAddress(values, "group:one|two"));
        assertFalse(AddressUtil.containsMatchingConversationAddress(values, "group:two|one"));
    }
}
