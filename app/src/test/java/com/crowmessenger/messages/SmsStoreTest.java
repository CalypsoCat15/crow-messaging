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
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SmsStoreTest {
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
