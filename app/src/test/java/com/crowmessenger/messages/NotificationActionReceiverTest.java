package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.Intent;

import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NotificationActionReceiverTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void notificationActionIntentTargetsPrivateReceiverAndAddress() {
        Intent intent = MessageNotifier.notificationActionIntent(
                context,
                NotificationActionReceiver.ACTION_MARK_READ,
                "+15551234567"
        );

        assertEquals(NotificationActionReceiver.class.getName(), intent.getComponent().getClassName());
        assertEquals(NotificationActionReceiver.ACTION_MARK_READ, intent.getAction());
        assertEquals("+15551234567", intent.getStringExtra(NotificationActionReceiver.EXTRA_ADDRESS));
    }

    @Test
    public void markReadClearsRememberedNotifications() {
        int stableId = AddressUtil.stableId("15551234567");
        context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE).edit()
                .putStringSet("ids_" + stableId, new HashSet<>(List.of("101")))
                .putString("address_" + stableId, "15551234567")
                .commit();

        NotificationActionReceiver.markRead(context, "+1 (555) 123-4567");

        assertFalse(context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE)
                .contains("ids_" + stableId));
    }
}
