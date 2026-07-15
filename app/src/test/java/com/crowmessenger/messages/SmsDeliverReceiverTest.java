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
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SmsDeliverReceiverTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("inbox_snapshot", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("trashed_conversations", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void saveNotifyAndBroadcast_usesLocalFallbackWhenAndroidSaveFails() {
        SmsDeliverReceiver.IncomingSms incoming = incoming("15551234567", "hello", 1234L);

        boolean saved = SmsDeliverReceiver.saveNotifyAndBroadcast(context, incoming, (c, a, b, d) -> false);

        assertFalse(saved);
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).body);
        assertEquals(1234L, messages.get(0).dateMillis);
    }

    @Test
    public void saveNotifyAndBroadcast_usesLocalFallbackWhenAndroidSaveThrows() {
        SmsDeliverReceiver.IncomingSms incoming = incoming("15551234567", "hello", 1234L);

        boolean saved = SmsDeliverReceiver.saveNotifyAndBroadcast(context, incoming, (c, a, b, d) -> {
            throw new IllegalStateException("provider unavailable");
        });

        assertFalse(saved);
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).body);
        assertEquals(1234L, messages.get(0).dateMillis);
    }

    @Test
    public void saveNotifyAndBroadcast_doesNotCreateLocalDuplicateWhenAndroidSaveSucceeds() {
        SmsDeliverReceiver.IncomingSms incoming = incoming("15551234567", "hello", 1234L);

        boolean saved = SmsDeliverReceiver.saveNotifyAndBroadcast(context, incoming, (c, a, b, d) -> true);

        assertTrue(saved);
        assertTrue(LocalMmsStore.loadForAddress(context, "15551234567").isEmpty());
    }

    @Test
    public void saveNotifyAndBroadcast_updatesInboxSnapshotBeforeAppIsOpen() {
        SmsDeliverReceiver.IncomingSms incoming = incoming("15551234567", "hello", 1234L);

        SmsDeliverReceiver.saveNotifyAndBroadcast(context, incoming, (c, a, b, d) -> true);

        List<Conversation> snapshot = InboxSnapshotStore.load(context);
        assertEquals(1, snapshot.size());
        assertEquals("hello", snapshot.get(0).snippet);
        assertEquals(1234L, snapshot.get(0).dateMillis);
    }

    @Test
    public void earliestPositiveTimestamp_ignoresInvalidMultipartTimes() {
        assertEquals(2000L, SmsDeliverReceiver.earliestPositiveTimestamp(0L, 2000L));
        assertEquals(2000L, SmsDeliverReceiver.earliestPositiveTimestamp(2000L, 0L));
        assertEquals(1000L, SmsDeliverReceiver.earliestPositiveTimestamp(2000L, 1000L));
    }

    @Test
    public void saveNotifyAndBroadcast_replacesMissingTimestamp() {
        SmsDeliverReceiver.IncomingSms incoming = incoming("15551234567", "hello", 0L);
        long[] savedAt = new long[1];

        boolean saved = SmsDeliverReceiver.saveNotifyAndBroadcast(context, incoming, (c, a, b, date) -> {
            savedAt[0] = date;
            return true;
        });

        assertTrue(saved);
        assertTrue(savedAt[0] > 0L);
    }

    private SmsDeliverReceiver.IncomingSms incoming(String address, String body, long dateMillis) {
        SmsDeliverReceiver.IncomingSms incoming = new SmsDeliverReceiver.IncomingSms(address, dateMillis);
        incoming.body.append(body);
        return incoming;
    }
}
