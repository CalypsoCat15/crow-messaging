package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class TrashStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("trashed_conversations", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void moveRestoreAndFilter_preserveConversationUntilPermanentDelete() {
        Conversation conversation = new Conversation("4", "+1 (555) 123-4567", "Dave", "photo", "Hello", 100L, 0);
        TrashStore.moveToTrash(context, conversation);

        assertTrue(TrashStore.isTrashed(context, "15551234567"));
        assertEquals(1, TrashStore.all(context).size());
        assertEquals("Dave", TrashStore.all(context).get(0).conversation().name);

        List<Conversation> rows = new ArrayList<>();
        rows.add(conversation);
        TrashStore.removeHiddenOrRestoreNew(context, rows);
        assertTrue(rows.isEmpty());

        assertTrue(TrashStore.restore(context, "+15551234567"));
        assertFalse(TrashStore.isTrashed(context, "15551234567"));
    }

    @Test
    public void newerMessage_automaticallyRestoresTrashedConversation() {
        Conversation oldConversation = new Conversation("4", "15551234567", "Dave", "Old", 100L, 0);
        TrashStore.moveToTrash(context, oldConversation);

        List<Conversation> rows = new ArrayList<>();
        rows.add(new Conversation("4", "15551234567", "Dave", "New", Long.MAX_VALUE, 1));
        TrashStore.removeHiddenOrRestoreNew(context, rows);

        assertEquals(1, rows.size());
        assertFalse(TrashStore.isTrashed(context, "15551234567"));
    }
}
