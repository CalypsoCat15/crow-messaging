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

    private SmsDeliverReceiver.IncomingSms incoming(String address, String body, long dateMillis) {
        SmsDeliverReceiver.IncomingSms incoming = new SmsDeliverReceiver.IncomingSms(address, dateMillis);
        incoming.body.append(body);
        return incoming;
    }
}
