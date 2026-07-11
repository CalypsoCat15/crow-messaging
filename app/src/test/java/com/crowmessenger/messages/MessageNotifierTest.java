package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MessageNotifierTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE).edit().clear().commit();
        TestContactsProvider.install();
        TestContactsProvider.clear();
    }

    @Test
    public void shouldSuppressIncoming_blocksGroupNotificationFromBlockedSender() {
        Blocklist.block(context, "15551234567");

        assertTrue(MessageNotifier.shouldSuppressIncoming(
                context,
                "group:15551234567|15557654321",
                "15551234567",
                "hello"
        ));
    }

    @Test
    public void shouldSuppressIncoming_blocksGroupNotificationFromSpamMarkedSender() {
        SpamFilter.markSpam(context, "15551234567");

        assertTrue(MessageNotifier.shouldSuppressIncoming(
                context,
                "group:15551234567|15557654321",
                "15551234567",
                "hello"
        ));
    }

    @Test
    public void shouldSuppressIncoming_allowsGroupNotificationFromNormalSender() {
        assertFalse(MessageNotifier.shouldSuppressIncoming(
                context,
                "group:15551234567|15557654321",
                "15551234567",
                "hello"
        ));
    }

    @Test
    public void shouldSuppressIncoming_allowsGroupNotificationWhenBlockedParticipantIsNotSender() {
        Blocklist.block(context, "15557654321");

        assertFalse(MessageNotifier.shouldSuppressIncoming(
                context,
                "group:15551234567|15557654321",
                "15551234567",
                "hello"
        ));
    }

    @Test
    public void shouldSuppressIncoming_allowsKeywordFromSavedContact() {
        SpamFilter.addCustomKeywords(context, "donate");
        TestContactsProvider.add("15551234567", "Dad");

        assertFalse(MessageNotifier.shouldSuppressIncoming(
                context,
                "15551234567",
                "",
                "Did you donate anything this year?"
        ));
    }

    @Test
    public void shouldSuppressIncoming_blocksKeywordFromUnknownSender() {
        SpamFilter.addCustomKeywords(context, "donate");

        assertTrue(MessageNotifier.shouldSuppressIncoming(
                context,
                "15557654321",
                "",
                "Donate to our charity today"
        ));
    }

    @Test
    public void shouldSuppressIncoming_usesActualGroupSenderForContactBypass() {
        SpamFilter.addCustomKeywords(context, "donate");
        TestContactsProvider.add("15551234567", "Dad");

        assertFalse(MessageNotifier.shouldSuppressIncoming(
                context,
                "group:15551234567|15557654321",
                "15551234567",
                "Did you donate anything this year?"
        ));
    }

    @Test
    public void clearIncomingForAddress_matchesEquivalentPhoneFormats() {
        String storedAddress = "15551234567";
        int stableId = AddressUtil.stableId(storedAddress);
        SharedPreferences prefs = context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE);
        prefs.edit()
                .putStringSet("ids_" + stableId, new HashSet<>(List.of("101", "102")))
                .putString("address_" + stableId, storedAddress)
                .commit();

        MessageNotifier.clearIncomingForAddress(context, "+1 (555) 123-4567");

        assertFalse(prefs.contains("ids_" + stableId));
        assertFalse(prefs.contains("address_" + stableId));
        assertEquals(0, prefs.getAll().size());
    }
}
