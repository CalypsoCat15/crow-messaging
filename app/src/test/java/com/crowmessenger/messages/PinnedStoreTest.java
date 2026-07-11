package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.HashSet;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PinnedStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("pinned_conversations", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void isPinned_doesNotMatchPhoneNumberInsidePinnedGroupAddress() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));

        PinnedStore.pin(context, group);

        assertTrue(PinnedStore.isPinned(context, group));
        assertFalse(PinnedStore.isPinned(context, "15557654321"));
    }

    @Test
    public void unpinPhoneNumberDoesNotRemovePinnedGroupAddress() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));
        PinnedStore.pin(context, group);

        PinnedStore.unpin(context, "15557654321");

        assertTrue(PinnedStore.isPinned(context, group));
    }

    @Test
    public void pinPhoneNumberRemovesLegacyMatchingPhonePins() {
        SharedPreferences prefs = context.getSharedPreferences("pinned_conversations", Context.MODE_PRIVATE);
        prefs.edit()
                .putStringSet("addresses", new HashSet<>(List.of("+1 (555) 123-4567")))
                .commit();

        PinnedStore.pin(context, "15551234567");

        assertEquals(
                new HashSet<>(List.of("15551234567")),
                prefs.getStringSet("addresses", new HashSet<>())
        );
    }
}
