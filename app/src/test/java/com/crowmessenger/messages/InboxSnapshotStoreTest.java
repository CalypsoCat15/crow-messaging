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

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class InboxSnapshotStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("inbox_snapshot", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void saveAndLoad_preservesVisibleInboxFields() {
        InboxSnapshotStore.save(context, List.of(new Conversation(
                "42",
                "+15551234567",
                "Jordan",
                "content://contacts/photo/1",
                "Hello",
                1234L,
                2
        )));

        List<Conversation> loaded = InboxSnapshotStore.load(context);

        assertEquals(1, loaded.size());
        Conversation conversation = loaded.get(0);
        assertEquals("42", conversation.threadId);
        assertEquals("+15551234567", conversation.address);
        assertEquals("Jordan", conversation.name);
        assertEquals("content://contacts/photo/1", conversation.photoUri);
        assertEquals("Hello", conversation.snippet);
        assertEquals(1234L, conversation.dateMillis);
        assertEquals(2, conversation.unreadCount);
    }

    @Test
    public void load_ignoresCorruptSnapshot() {
        context.getSharedPreferences("inbox_snapshot", Context.MODE_PRIVATE)
                .edit()
                .putString("rows", "not-json")
                .commit();

        assertTrue(InboxSnapshotStore.load(context).isEmpty());
        assertTrue(context.getSharedPreferences("inbox_snapshot", Context.MODE_PRIVATE)
                .getString("rows", "")
                .isEmpty());
    }

    @Test
    public void save_limitsSnapshotSize() {
        ArrayList<Conversation> conversations = new ArrayList<>();
        for (int index = 0; index < 150; index++) {
            conversations.add(new Conversation("" + index, "1555000" + index, "Name", "Body", index, 0));
        }

        InboxSnapshotStore.save(context, conversations);

        assertEquals(24, InboxSnapshotStore.load(context).size());
    }

    @Test
    public void remove_hidesOnlyMatchingConversation() {
        InboxSnapshotStore.save(context, List.of(
                new Conversation("1", "+1 (555) 123-4567", "Dave", "Hello", 2L, 0),
                new Conversation("2", "15557654321", "Lisa", "Hi", 1L, 0)
        ));

        InboxSnapshotStore.remove(context, "15551234567");

        List<Conversation> loaded = InboxSnapshotStore.load(context);
        assertEquals(1, loaded.size());
        assertEquals("Lisa", loaded.get(0).name);
    }
}
