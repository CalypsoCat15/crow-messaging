package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SpamFilterTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        TestContactsProvider.install();
        TestContactsProvider.clear();
    }

    @Test
    public void isMarkedSpam_matchesSamePhoneNumberWithDifferentFormatting() {
        SpamFilter.markSpam(context, "+1 (555) 123-4567");

        assertTrue(SpamFilter.isMarkedSpam(context, "5551234567"));
    }

    @Test
    public void markSpamPhoneNumberRemovesLegacyMatchingSenderEntries() {
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("senders", new HashSet<>(List.of("+1 (555) 123-4567")))
                .commit();

        SpamFilter.markSpam(context, "15551234567");

        assertEquals(
                new HashSet<>(List.of("tel:15551234567")),
                context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE)
                        .getStringSet("senders", new HashSet<>())
        );
    }

    @Test
    public void isMarkedSpam_doesNotMatchParticipantInsideGroupAddress() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));

        SpamFilter.markSpam(context, group);

        assertTrue(SpamFilter.isMarkedSpam(context, group));
        assertFalse(SpamFilter.isMarkedSpam(context, "15557654321"));
    }

    @Test
    public void alphanumericSenderId_doesNotMarkItsDigitsAsSpam() {
        SpamFilter.markSpam(context, "ACME2FA");

        assertTrue(SpamFilter.isMarkedSpam(context, "acme2fa"));
        assertFalse(SpamFilter.isMarkedSpam(context, "2"));
    }

    @Test
    public void addCustomKeywords_ignoresNullOrEmptyInput() {
        assertTrue(SpamFilter.customKeywords(context).isEmpty());

        assertEquals(0, SpamFilter.addCustomKeywords(context, null));
        assertEquals(0, SpamFilter.addCustomKeywords(context, ""));

        assertTrue(SpamFilter.customKeywords(context).isEmpty());
    }

    @Test
    public void removeCustomKeywords_ignoresNullOrEmptyInput() {
        SpamFilter.addCustomKeywords(context, "free gift");

        assertEquals(0, SpamFilter.removeCustomKeywords(context, null));
        assertEquals(0, SpamFilter.removeCustomKeywords(context, Collections.emptyList()));

        assertEquals(List.of("free gift"), SpamFilter.customKeywords(context));
    }

    @Test
    public void customKeywords_cleansLegacyStoredRules() {
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("custom_keywords", new HashSet<>(List.of(" Free Gift ", "", "URGENT")))
                .commit();

        assertEquals(List.of("free gift", "urgent"), SpamFilter.customKeywords(context));
        assertTrue(SpamFilter.matchesCustomKeyword(context, "This is an urgent message"));
        assertEquals(
                new HashSet<>(List.of("free gift", "urgent")),
                context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE)
                        .getStringSet("custom_keywords", new HashSet<>())
        );
    }

    @Test
    public void keywordRulesApplyOnlyToNumbersOutsideContacts() {
        SpamFilter.addCustomKeywords(context, "donate");
        TestContactsProvider.add("15551234567", "Dad");

        assertFalse(SpamFilter.matchesKeywordForUnknownSender(
                context,
                "15551234567",
                "Did you donate anything this year?"
        ));
        assertTrue(SpamFilter.matchesKeywordForUnknownSender(
                context,
                "15557654321",
                "Donate to our charity today"
        ));
    }

    @Test
    public void markedThreadStillMatchesWhenProviderAddressChanges() {
        SpamFilter.markSpam(context, "sender-id-original", "42");

        SpamFilter.Matcher matcher = SpamFilter.matcher(context);
        assertTrue(matcher.isMarkedSpam("sender-id-rewritten", "42"));
        assertFalse(matcher.isMarkedSpam("sender-id-rewritten", "43"));
    }

    @Test
    public void unmarkSpamRemovesAddressAndThreadMarkers() {
        SpamFilter.markSpam(context, "+1 (555) 123-4567", "42");

        SpamFilter.unmarkSpam(context, "15551234567", "42");

        SpamFilter.Matcher matcher = SpamFilter.matcher(context);
        assertFalse(matcher.isMarkedSpam("+1 555 123 4567", "42"));
    }

    @Test
    public void newlySavedContactIsRecognizedAfterEarlierUnknownLookup() {
        SpamFilter.addCustomKeywords(context, "donate");
        String address = "15551234567";

        assertTrue(SpamFilter.matchesKeywordForUnknownSender(context, address, "Please donate"));
        TestContactsProvider.addWithoutClearingCache(address, "Dad");

        assertFalse(SpamFilter.matchesKeywordForUnknownSender(context, address, "Did you donate?"));
    }

    @Test
    public void matcherQueriesEachUnknownKeywordSenderOnlyOncePerScan() {
        SpamFilter.addCustomKeywords(context, "donate");
        SpamFilter.Matcher matcher = SpamFilter.matcher(context);

        assertTrue(matcher.matchesKeywordForUnknownSender("15557654321", "Please donate"));
        assertTrue(matcher.matchesKeywordForUnknownSender("15557654321", "Donate today"));

        assertEquals(1, TestContactsProvider.queryCount());
    }

    @Test
    public void manuallyMarkedSpamStillOverridesSavedContact() {
        SpamFilter.addCustomKeywords(context, "donate");
        TestContactsProvider.add("15551234567", "Dad");
        SpamFilter.markSpam(context, "15551234567");

        assertTrue(SpamFilter.isSpam(context, "15551234567", "Did you donate anything?"));
    }

    @Test
    public void savedNumericShortCodeDoesNotWhitelistAlphanumericSenderWithSameDigits() {
        SpamFilter.addCustomKeywords(context, "donate");
        TestContactsProvider.add("2", "Short Code Two");

        assertTrue(ContactLookup.isSavedContact(context, "2"));
        assertTrue(SpamFilter.matchesKeywordForUnknownSender(
                context,
                "ACME2FA",
                "Please donate today"
        ));
        assertEquals(2, TestContactsProvider.queryCount());
    }
}
