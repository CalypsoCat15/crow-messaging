package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ConversationSuppressionTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("trashed_conversations", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("inbox_snapshot", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void block_clearsExistingConversationNotifications() {
        String address = "15551234567";
        seedNotification(address);
        seedSnapshot(address);

        ConversationSuppression.block(context, "+1 (555) 123-4567");

        assertTrue(Blocklist.isBlocked(context, address));
        assertNotificationCleared(address);
        assertTrue(InboxSnapshotStore.load(context).isEmpty());
    }

    @Test
    public void markSpam_clearsExistingConversationNotifications() {
        String address = "15551234567";
        seedNotification(address);
        seedSnapshot(address);

        ConversationSuppression.markSpam(context, "+1 (555) 123-4567", "42");

        assertTrue(SpamFilter.isMarkedSpam(context, address));
        assertNotificationCleared(address);
        assertTrue(InboxSnapshotStore.load(context).isEmpty());
    }

    @Test
    public void moveToTrash_clearsExistingConversationNotifications() {
        String address = "15551234567";
        seedNotification(address);
        seedSnapshot(address);
        Conversation conversation = new Conversation("42", address, "Dave", "", "Hello", 100L, 1);

        ConversationSuppression.moveToTrash(context, conversation);

        assertTrue(TrashStore.isTrashed(context, address));
        assertNotificationCleared(address);
        assertTrue(InboxSnapshotStore.load(context).isEmpty());
    }

    @Test
    public void loadVisible_defensivelyFiltersSuppressedSnapshotRows() {
        String address = "15551234567";
        seedSnapshot(address);
        Blocklist.block(context, address);

        assertTrue(InboxSnapshotStore.loadVisible(context).isEmpty());
    }

    private void seedNotification(String address) {
        int stableId = AddressUtil.stableId(address);
        context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("ids_" + stableId, new HashSet<>(List.of("101")))
                .putString("address_" + stableId, address)
                .commit();
    }

    private void seedSnapshot(String address) {
        InboxSnapshotStore.save(
                context,
                List.of(new Conversation("42", address, "Dave", "", "Hello", 100L, 1))
        );
    }

    private void assertNotificationCleared(String address) {
        int stableId = AddressUtil.stableId(address);
        SharedPreferences prefs = context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE);
        assertFalse(prefs.contains("ids_" + stableId));
        assertFalse(prefs.contains("address_" + stableId));
    }
}
