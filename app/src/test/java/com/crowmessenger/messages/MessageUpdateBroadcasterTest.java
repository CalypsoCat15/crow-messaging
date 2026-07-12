package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MessageUpdateBroadcasterTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void incomingSmsUpdate_isMarkedIncomingAndKeepsMessageDetails() {
        Intent update = MessageUpdateBroadcaster.updateIntent(
                context, "15551234567", "Hello", 1234L, true
        );

        assertTrue(MessageUpdateBroadcaster.isIncomingUpdate(update));
        assertEquals("15551234567", update.getStringExtra(MainActivity.EXTRA_OPEN_ADDRESS));
        assertEquals("Hello", update.getStringExtra(MainActivity.EXTRA_MESSAGE_BODY));
        assertEquals(1234L, update.getLongExtra(MainActivity.EXTRA_MESSAGE_DATE, 0L));
    }

    @Test
    public void ordinaryStatusRefresh_isNotTreatedAsIncoming() {
        Intent update = MessageUpdateBroadcaster.updateIntent(
                context, "15551234567", "", 0L, false
        );

        assertFalse(MessageUpdateBroadcaster.isIncomingUpdate(update));
        assertEquals("15551234567", update.getStringExtra(MainActivity.EXTRA_OPEN_ADDRESS));
        assertFalse(update.hasExtra(MainActivity.EXTRA_MESSAGE_BODY));
    }
}
