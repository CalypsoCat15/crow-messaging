package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MessageGroupingTest {
    @Test
    public void groupsNearbyMessagesFromSameSide() {
        ChatMessage first = new ChatMessage("one", 1_000L, true);
        ChatMessage second = new ChatMessage("two", 60_000L, true);

        assertTrue(MessageGrouping.canGroup(first, second));
    }

    @Test
    public void separatesMessagesFromDifferentSendersOrFarApart() {
        ChatMessage first = new ChatMessage("one", "", "15550000001", 1_000L, false);
        ChatMessage otherSender = new ChatMessage("two", "", "15550000002", 2_000L, false);
        ChatMessage muchLater = new ChatMessage("three", "", "15550000001", 700_000L, false);

        assertFalse(MessageGrouping.canGroup(first, otherSender));
        assertFalse(MessageGrouping.canGroup(first, muchLater));
    }

    @Test
    public void separatesIncomingAndOutgoingMessages() {
        ChatMessage incoming = new ChatMessage("one", 1_000L, false);
        ChatMessage outgoing = new ChatMessage("two", 2_000L, true);

        assertFalse(MessageGrouping.canGroup(incoming, outgoing));
    }
}
