package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SpamInboxSnapshotStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("spam_inbox_snapshot", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("inbox_snapshot", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("trashed_conversations", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void upsert_keepsMultipleMarkedSpamConversations() {
        SpamFilter.markSpam(context, "15551234567");
        SpamFilter.markSpam(context, "15557654321");

        SpamInboxSnapshotStore.upsert(context, conversation("1", "15551234567", "First", 1000L));
        SpamInboxSnapshotStore.upsert(context, conversation("2", "15557654321", "Second", 2000L));

        List<Conversation> rows = SpamInboxSnapshotStore.loadVisible(context);
        assertEquals(2, rows.size());
        assertEquals("15557654321", rows.get(0).address);
        assertEquals("15551234567", rows.get(1).address);
    }

    @Test
    public void save_reconcilesFullSpamInboxSnapshot() {
        SpamFilter.markSpam(context, "15551234567");
        SpamFilter.markSpam(context, "15557654321");
        SpamInboxSnapshotStore.upsert(context, conversation("old", "15550001111", "Old", 500L));

        SpamInboxSnapshotStore.save(context, List.of(
                conversation("1", "15551234567", "First", 1000L),
                conversation("2", "15557654321", "Second", 2000L)
        ));

        assertEquals(2, SpamInboxSnapshotStore.loadVisible(context).size());
    }

    @Test
    public void remove_hidesOnlyRestoredConversation() {
        SpamFilter.markSpam(context, "15551234567");
        SpamFilter.markSpam(context, "15557654321");
        SpamInboxSnapshotStore.upsert(context, conversation("1", "15551234567", "First", 1000L));
        SpamInboxSnapshotStore.upsert(context, conversation("2", "15557654321", "Second", 2000L));

        SpamFilter.unmarkSpam(context, "15551234567");
        SpamInboxSnapshotStore.remove(context, "+1 (555) 123-4567");

        List<Conversation> rows = SpamInboxSnapshotStore.loadVisible(context);
        assertEquals(1, rows.size());
        assertEquals("15557654321", rows.get(0).address);
    }

    @Test
    public void suppressedIncoming_updatesSpamSnapshotInsteadOfNormalInbox() {
        SpamFilter.markSpam(context, "15551234567");

        List<Conversation> normalRows = InboxSnapshotStore.upsertIncomingDurably(
                context,
                "15551234567",
                "Filtered message",
                3000L
        );

        assertTrue(normalRows.isEmpty());
        List<Conversation> spamRows = SpamInboxSnapshotStore.loadVisible(context);
        assertEquals(1, spamRows.size());
        assertEquals("Filtered message", spamRows.get(0).snippet);
    }

    @Test
    public void suppressedIncoming_sameDeliveryDoesNotDoubleUnreadCount() {
        SpamFilter.markSpam(context, "15551234567");
        InboxSnapshotStore.upsertIncomingDurably(context, "15551234567", "Filtered message", 3000L);

        InboxSnapshotStore.upsertIncomingDurably(context, "15551234567", "Filtered message", 3000L);

        List<Conversation> spamRows = SpamInboxSnapshotStore.loadVisible(context);
        assertEquals(1, spamRows.size());
        assertEquals(1, spamRows.get(0).unreadCount);
    }

    private static Conversation conversation(String threadId, String address, String body, long dateMillis) {
        return new Conversation(threadId, address, "Sender", body, dateMillis, 0);
    }
}
