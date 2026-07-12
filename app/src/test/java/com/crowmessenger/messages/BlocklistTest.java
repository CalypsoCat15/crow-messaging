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

import java.util.HashSet;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BlocklistTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void isBlocked_matchesNormalPhoneNumber() {
        Blocklist.block(context, "15551234567");

        assertTrue(Blocklist.isBlocked(context, "+1 (555) 123-4567"));
    }

    @Test
    public void isBlocked_doesNotTreatGroupAddressAsBlockedPhoneNumber() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));
        Blocklist.block(context, "15557654321");

        assertFalse(Blocklist.isBlocked(context, group));
    }

    @Test
    public void block_ignoresGroupAddress() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));

        Blocklist.block(context, group);

        assertFalse(Blocklist.isBlocked(context, "15557654321"));
    }

    @Test
    public void unblock_ignoresGroupAddress() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));
        Blocklist.block(context, "15557654321");

        Blocklist.unblock(context, group);

        assertTrue(Blocklist.isBlocked(context, "15557654321"));
    }

    @Test
    public void blockPhoneNumberRemovesLegacyMatchingPhoneEntries() {
        SharedPreferences prefs = context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE);
        prefs.edit()
                .putStringSet("numbers", new HashSet<>(List.of("+1 (555) 123-4567")))
                .commit();

        Blocklist.block(context, "15551234567");

        assertEquals(
                new HashSet<>(List.of("15551234567")),
                prefs.getStringSet("numbers", new HashSet<>())
        );
    }

    @Test
    public void block_matchesAlphabeticSenderIdsIgnoringCase() {
        Blocklist.block(context, "Crow Alerts");

        assertTrue(Blocklist.isBlocked(context, "CROW ALERTS"));
        assertFalse(Blocklist.isBlocked(context, "Crow Support"));

        Blocklist.unblock(context, "crow alerts");
        assertFalse(Blocklist.isBlocked(context, "Crow Alerts"));
    }

    @Test
    public void alphanumericSenderId_doesNotBlockItsDigitsAsAPhoneNumber() {
        Blocklist.block(context, "ACME2FA");

        assertTrue(Blocklist.isBlocked(context, "acme2fa"));
        assertFalse(Blocklist.isBlocked(context, "2"));
    }
}
