package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ScheduledMessageStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void save_rejectsEmptyRecords() {
        long future = System.currentTimeMillis() + 60000L;

        assertNull(ScheduledMessageStore.save(context, "", "hello", future));
        assertNull(ScheduledMessageStore.save(context, "15551234567", "", future));
        assertNull(ScheduledMessageStore.save(context, "   ", "hello", future));
        assertNull(ScheduledMessageStore.save(context, "15551234567", "   ", future));
        assertNull(ScheduledMessageStore.save(context, "15551234567", "hello", 0L));
        assertTrue(ScheduledMessageStore.all(context).isEmpty());
    }

    @Test
    public void save_trimsStoredAddressAndBody() {
        ScheduledMessageStore.ScheduledMessage message = ScheduledMessageStore.save(
                context,
                " 15551234567 ",
                " hello ",
                System.currentTimeMillis() + 60000L
        );

        assertEquals("15551234567", message.address);
        assertEquals("hello", message.body);
        assertEquals("hello", ScheduledMessageStore.find(context, message.id).body);
    }

    @Test
    public void find_trimsLegacyStoredAddressAndBody() {
        String id = "spaced-message";
        context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("ids", new HashSet<>(java.util.Collections.singletonList(id)))
                .putString("message_" + id, "{"
                        + "\"id\":\"" + id + "\","
                        + "\"address\":\" 15551234567 \","
                        + "\"body\":\" hello \","
                        + "\"sendAtMillis\":1234"
                        + "}")
                .commit();

        ScheduledMessageStore.ScheduledMessage message = ScheduledMessageStore.find(context, id);

        assertEquals("15551234567", message.address);
        assertEquals("hello", message.body);
    }

    @Test
    public void markFailed_restoresMissingIdListEntry() {
        ScheduledMessageStore.ScheduledMessage message = ScheduledMessageStore.save(
                context,
                "15551234567",
                "hello",
                System.currentTimeMillis() + 60000L
        );
        context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("ids", new HashSet<>())
                .commit();

        ScheduledMessageStore.markFailed(context, message, "Network unavailable.");

        Set<String> ids = context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .getStringSet("ids", new HashSet<>());
        assertTrue(ids.contains(message.id));
        assertEquals(1, ScheduledMessageStore.all(context).size());
        assertTrue(ScheduledMessageStore.find(context, message.id).failed());
    }

    @Test
    public void all_removesCorruptIdListEntries() {
        context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("ids", new HashSet<>(java.util.Collections.singletonList("missing")))
                .commit();

        assertTrue(ScheduledMessageStore.all(context).isEmpty());
        assertFalse(context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .getStringSet("ids", new HashSet<>())
                .contains("missing"));
    }

    @Test
    public void all_removesIncompleteStoredMessages() {
        String id = "missing-body";
        context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("ids", new HashSet<>(java.util.Collections.singletonList(id)))
                .putString("message_" + id, "{"
                        + "\"id\":\"" + id + "\","
                        + "\"address\":\"15551234567\","
                        + "\"sendAtMillis\":1234"
                        + "}")
                .commit();

        assertNull(ScheduledMessageStore.find(context, id));
        assertTrue(ScheduledMessageStore.all(context).isEmpty());
        assertFalse(context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .getStringSet("ids", new HashSet<>())
                .contains(id));
    }

    @Test
    public void all_removesWhitespaceOnlyStoredMessages() {
        String id = "blank-body";
        context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("ids", new HashSet<>(java.util.Collections.singletonList(id)))
                .putString("message_" + id, "{"
                        + "\"id\":\"" + id + "\","
                        + "\"address\":\"15551234567\","
                        + "\"body\":\"   \","
                        + "\"sendAtMillis\":1234"
                        + "}")
                .commit();

        assertNull(ScheduledMessageStore.find(context, id));
        assertTrue(ScheduledMessageStore.all(context).isEmpty());
        assertFalse(context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE)
                .getStringSet("ids", new HashSet<>())
                .contains(id));
    }

    @Test
    public void deleteForAddress_removesOnlyMatchingScheduledMessages() {
        long future = System.currentTimeMillis() + 60000L;
        ScheduledMessageStore.ScheduledMessage matching = ScheduledMessageStore.save(
                context,
                "15551234567",
                "hello",
                future
        );
        ScheduledMessageStore.ScheduledMessage kept = ScheduledMessageStore.save(
                context,
                "15557654321",
                "keep",
                future
        );

        List<ScheduledMessageStore.ScheduledMessage> deleted = ScheduledMessageStore.deleteForAddress(
                context,
                "+1 (555) 123-4567"
        );

        assertEquals(1, deleted.size());
        assertEquals(matching.id, deleted.get(0).id);
        assertNull(ScheduledMessageStore.find(context, matching.id));
        assertEquals(kept.id, ScheduledMessageStore.find(context, kept.id).id);
    }

    @Test
    public void deleteForAddress_removesEveryEquivalentAddressInOneBatch() {
        long future = System.currentTimeMillis() + 60000L;
        ScheduledMessageStore.ScheduledMessage first = ScheduledMessageStore.save(
                context, "5551234567", "first", future
        );
        ScheduledMessageStore.ScheduledMessage second = ScheduledMessageStore.save(
                context, "+1 (555) 123-4567", "second", future + 1L
        );

        List<ScheduledMessageStore.ScheduledMessage> deleted = ScheduledMessageStore.deleteForAddress(
                context, "15551234567"
        );

        assertEquals(2, deleted.size());
        assertNull(ScheduledMessageStore.find(context, first.id));
        assertNull(ScheduledMessageStore.find(context, second.id));
        assertTrue(ScheduledMessageStore.all(context).isEmpty());
    }

    @Test
    public void addressLookupsIgnoreEmptyAddress() {
        ScheduledMessageStore.ScheduledMessage saved = ScheduledMessageStore.save(
                context,
                "15551234567",
                "hello",
                System.currentTimeMillis() + 60000L
        );

        assertTrue(ScheduledMessageStore.forAddress(context, "").isEmpty());
        assertFalse(ScheduledMessageStore.hasForAddress(context, ""));
        assertTrue(ScheduledMessageStore.deleteForAddress(context, "").isEmpty());
        assertEquals(saved.id, ScheduledMessageStore.find(context, saved.id).id);
    }

    @Test
    public void concurrentSaves_keepEveryScheduledMessageIndexed() throws Exception {
        int messageCount = 30;
        long future = System.currentTimeMillis() + 60000L;
        ExecutorService executor = Executors.newFixedThreadPool(6);
        java.util.ArrayList<Future<?>> saves = new java.util.ArrayList<>();
        for (int index = 0; index < messageCount; index++) {
            int messageIndex = index;
            saves.add(executor.submit(() -> ScheduledMessageStore.save(
                    context,
                    "15551234567",
                    "scheduled-" + messageIndex,
                    future + messageIndex
            )));
        }
        for (Future<?> save : saves) {
            save.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertEquals(messageCount, ScheduledMessageStore.all(context).size());
    }
}
