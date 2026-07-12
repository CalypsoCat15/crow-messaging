package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SmsStoreTest {
    @Test
    public void searchMatchesOlderMessageTextAndMatchingContactWithoutPerMessageLookup() {
        assertTrue(SmsStore.matchesQuery("15551234567", "This is another Test message", "test", new HashSet<>()));

        HashSet<String> matchingContacts = new HashSet<>();
        matchingContacts.add("+1 (555) 765-4321");
        assertTrue(SmsStore.matchesQuery("15557654321", "Hello", "dave", matchingContacts));
        assertFalse(SmsStore.matchesQuery("15550000000", "Hello", "test", matchingContacts));
    }

    @Test
    public void searchSelection_filtersInProviderBeforeReadingMessageHistory() {
        HashSet<String> matchingContacts = new HashSet<>();
        matchingContacts.add("15557654321");

        SmsStore.SearchSelection selection = SmsStore.searchSelection("test", matchingContacts);

        assertTrue(selection.selection.contains("body LIKE ?"));
        assertTrue(selection.selection.contains("address=?"));
        assertEquals("%test%", selection.args[0]);
        assertEquals("%test%", selection.args[1]);
        assertEquals("15557654321", selection.args[2]);
    }

    @Test
    public void searchIndex_matchesHistoricalTextNameAndNumberInMemory() {
        Conversation conversation = new Conversation("7", "15551234567", "Dave", "Newest message", 2000L, 0);
        SmsStore.SearchThread indexed = new SmsStore.SearchThread(
                conversation,
                "Older testing message\nAnother old message"
        );

        assertTrue(indexed.matches("testing"));
        assertTrue(indexed.matches("dave"));
        assertTrue(indexed.matches("1234"));
        assertFalse(indexed.matches("not present"));
    }

    @Test
    public void searchIndex_returnsNewestMatchingMessageInsteadOfUnrelatedThreadSnippet() {
        Conversation conversation = new Conversation("7", "15551234567", "Dave", "Newest unrelated message", 3000L, 0);
        SmsStore.SearchThread indexed = new SmsStore.SearchThread(
                conversation,
                List.of(
                        new SmsStore.SearchMessage("Newest unrelated message", 3000L),
                        new SmsStore.SearchMessage("Second test result", 2000L),
                        new SmsStore.SearchMessage("Old test result", 1000L)
                )
        );

        SmsStore.SearchThread.Match match = indexed.match("test");

        assertEquals("Second test result", match.snippet);
        assertEquals(2000L, match.dateMillis);
    }

    @Test
    public void searchIndex_explainsPhoneNumberMatchesWithTheNumber() {
        Conversation conversation = new Conversation("7", "15551234567", "Dave", "Unrelated message", 3000L, 0);
        SmsStore.SearchThread indexed = new SmsStore.SearchThread(conversation, "Unrelated message");

        SmsStore.SearchThread.Match match = indexed.match("1234");

        assertEquals("15551234567", match.snippet);
    }

    @Test
    public void deleteMessage_removesOnlySelectedLocalMmsRecord() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE).edit().clear().commit();
        LocalMmsStore.saveNotice(context, "15551234567", "First", 1000L);
        LocalMmsStore.saveNotice(context, "15551234567", "Second", 2000L);
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");

        assertEquals(2, messages.size());
        assertTrue(SmsStore.deleteMessage(context, messages.get(0)));

        List<ChatMessage> remaining = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals(1, remaining.size());
        assertEquals("Second", remaining.get(0).body);
    }

    @Test
    public void deleteMessage_rejectsUntrustedSmsIdentity() {
        Context context = RuntimeEnvironment.getApplication();
        ChatMessage message = ChatMessage.storedSms("Hello", "1 OR 1=1", 1000L, false);

        assertFalse(SmsStore.deleteMessage(context, message));
    }

    @Test
    public void loadConversations_includesLocalMmsWhenSmsProviderUnavailable() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        LocalMmsStore.saveNotice(context, "15551234567", "Picture message", 1000L);

        List<Conversation> conversations = SmsStore.loadConversations(context, false, "");

        assertEquals(1, conversations.size());
        assertEquals("15551234567", conversations.get(0).address);
        assertEquals("Picture message", conversations.get(0).snippet);
    }

    @Test
    public void markConversationRead_alwaysClearsLocalMmsUnreadState() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        String address = "15551234567";
        LocalMmsStore.saveNotice(context, address, "Picture message", 1000L);

        SmsStore.markConversationRead(context, "12", address);

        List<Conversation> conversations = LocalMmsStore.loadConversations(context, false, "");
        assertEquals(1, conversations.size());
        assertEquals(0, conversations.get(0).unreadCount);
    }

    @Test
    public void readValues_markMessagesBothReadAndSeen() {
        android.content.ContentValues values = SmsStore.smsReadValues();

        assertEquals(Integer.valueOf(1), values.getAsInteger(android.provider.Telephony.Sms.READ));
        assertEquals(Integer.valueOf(1), values.getAsInteger(android.provider.Telephony.Sms.SEEN));
    }

    @Test
    public void savedIncomingSms_isExplicitlyUnreadAndUnseen() {
        Context context = RuntimeEnvironment.getApplication();
        TestSmsProvider.install();

        assertTrue(SmsStore.saveIncomingSms(context, "31354", "hello", 1000L));

        android.content.ContentValues values = TestSmsProvider.lastInsertedValues();
        assertEquals(Integer.valueOf(0), values.getAsInteger(android.provider.Telephony.Sms.READ));
        assertEquals(Integer.valueOf(0), values.getAsInteger(android.provider.Telephony.Sms.SEEN));
    }

    @Test
    public void savedSentSms_isExplicitlyReadAndSeen() {
        Context context = RuntimeEnvironment.getApplication();
        TestSmsProvider.install();

        assertTrue(SmsStore.saveSentSms(context, "31354", "hello", 1000L));

        android.content.ContentValues values = TestSmsProvider.lastInsertedValues();
        assertEquals(Integer.valueOf(1), values.getAsInteger(android.provider.Telephony.Sms.READ));
        assertEquals(Integer.valueOf(1), values.getAsInteger(android.provider.Telephony.Sms.SEEN));
    }

    @Test
    public void verifiedRead_fallsBackToShortCodeWhenThreadIdIsStale() {
        Context context = RuntimeEnvironment.getApplication();
        TestSmsProvider.install();
        TestSmsProvider.add("7", "real-thread", "31354", false, false);

        assertTrue(SmsStore.markConversationReadVerified(context, "stale-thread", "31354"));
        assertTrue(TestSmsProvider.isReadAndSeen("7"));
    }

    @Test
    public void verifiedRead_doesNotReportSuccessWhenProviderCannotBeChecked() {
        Context context = RuntimeEnvironment.getApplication();
        TestSmsProvider.install();
        TestSmsProvider.setQueryUnavailable(true);

        assertFalse(SmsStore.markConversationReadVerified(context, "thread", "31354"));
    }

    @Test
    public void mergeLocalConversation_addsUnreadCountToExistingSmsThread() {
        List<Conversation> conversations = new ArrayList<>();
        conversations.add(new Conversation(
                "12",
                "15551234567",
                "Alex",
                "",
                "Text message",
                1000L,
                2
        ));

        SmsStore.mergeLocalConversation(conversations, new Conversation(
                "",
                "+1 (555) 123-4567",
                "Alex",
                "",
                "Picture message",
                2000L,
                1
        ));

        assertEquals(1, conversations.size());
        assertEquals("Picture message", conversations.get(0).snippet);
        assertEquals(2000L, conversations.get(0).dateMillis);
        assertEquals(3, conversations.get(0).unreadCount);
    }

    @Test
    public void mergeLocalConversation_keepsOlderLocalSnippetButStillAddsUnreadCount() {
        List<Conversation> conversations = new ArrayList<>();
        conversations.add(new Conversation(
                "12",
                "15551234567",
                "Alex",
                "",
                "Text message",
                2000L,
                2
        ));

        SmsStore.mergeLocalConversation(conversations, new Conversation(
                "",
                "+1 (555) 123-4567",
                "Alex",
                "",
                "Older picture",
                1000L,
                1
        ));

        assertEquals(1, conversations.size());
        assertEquals("Text message", conversations.get(0).snippet);
        assertEquals(2000L, conversations.get(0).dateMillis);
        assertEquals(3, conversations.get(0).unreadCount);
    }

    @Test
    public void normalInboxSkipsKeywordSpamWithoutHidingWholeThread() {
        assertFalse(SmsStore.shouldHideWholeThread(false, false));
        assertFalse(SmsStore.shouldShowMessage(false, false, true));
        assertTrue(SmsStore.shouldShowMessage(false, false, false));
    }

    @Test
    public void normalInboxHidesWholeThreadForBlockedSender() {
        assertTrue(SmsStore.shouldHideWholeThread(false, true));
        assertFalse(SmsStore.shouldShowMessage(false, true, false));
    }

    @Test
    public void blockedInboxShowsBlockedAndKeywordSpamMessages() {
        assertFalse(SmsStore.shouldHideWholeThread(true, true));
        assertTrue(SmsStore.shouldShowMessage(true, true, false));
        assertTrue(SmsStore.shouldShowMessage(true, false, true));
        assertFalse(SmsStore.shouldShowMessage(true, false, false));
    }

    @Test
    public void shouldShowThreadMessage_usesSameKeywordRulesAsInbox() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        SpamFilter.addCustomKeywords(context, "free gift");

        assertFalse(SmsStore.shouldShowThreadMessage(context, "15551234567", "claim your free gift", false));
        assertTrue(SmsStore.shouldShowThreadMessage(context, "15551234567", "claim your free gift", true));
        assertTrue(SmsStore.shouldShowThreadMessage(context, "15551234567", "see you soon", false));
        assertFalse(SmsStore.shouldShowThreadMessage(context, "15551234567", "see you soon", true));
    }

    @Test
    public void shouldShowThreadMessage_hidesBlockedSenderFromNormalThread() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        Blocklist.block(context, "15551234567");

        assertFalse(SmsStore.shouldShowThreadMessage(context, "15551234567", "hello", false));
        assertTrue(SmsStore.shouldShowThreadMessage(context, "15551234567", "hello", true));
    }

    @Test
    public void shouldShowThreadMessage_neverFiltersOutgoingKeywordMatch() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        ContactLookup.clearCache();
        SpamFilter.addCustomKeywords(context, "donate");

        assertTrue(SmsStore.shouldShowThreadMessage(
                context,
                "15557654321",
                "I already donate there",
                false,
                true
        ));
        assertFalse(SmsStore.shouldShowThreadMessage(
                context,
                "15557654321",
                "I already donate there",
                true,
                true
        ));
    }

    @Test
    public void deleteConversation_clearsDraftForAddress() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("message_drafts", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        DraftStore.save(context, "15551234567", "unfinished");

        int deleted = SmsStore.deleteConversation(context, "", "+1 (555) 123-4567");

        assertTrue(deleted > 0);
        assertEquals("", DraftStore.draft(context, "15551234567"));
    }

    @Test
    public void deleteConversation_clearsPendingMmsForAddress() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        File downloadDir = MmsFiles.appFileDir(context, MmsFiles.DOWNLOADS_DIR);
        File pduFile = new File(downloadDir, "pending-delete.pdu");
        assertTrue(pduFile.createNewFile());
        LocalMmsStore.savePending(context, "pending-delete", "15551234567", pduFile.getAbsolutePath());

        int deleted = SmsStore.deleteConversation(context, "", "+1 (555) 123-4567");

        assertTrue(deleted > 0);
        assertEquals("", LocalMmsStore.pending(context, "pending-delete").pduPath);
        assertFalse(pduFile.exists());
    }

    @Test
    public void deleteConversation_clearsPinAndTrackedNotifications() {
        Context context = RuntimeEnvironment.getApplication();
        String address = "15551234567";
        PinnedStore.pin(context, address);
        String notificationKey = "ids_" + AddressUtil.stableId(address);
        context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE)
                .edit()
                .putStringSet(notificationKey, new java.util.HashSet<>(java.util.List.of("123")))
                .putString("address_" + AddressUtil.stableId(address), address)
                .commit();

        int deleted = SmsStore.deleteConversation(context, "", "+1 (555) 123-4567");

        assertTrue(deleted > 0);
        assertFalse(PinnedStore.isPinned(context, address));
        assertFalse(context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE)
                .contains(notificationKey));
        assertFalse(context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE)
                .contains("address_" + AddressUtil.stableId(address)));
    }
}
