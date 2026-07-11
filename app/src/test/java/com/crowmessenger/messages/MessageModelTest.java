package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MessageModelTest {
    @Test
    public void chatMessage_normalizesNullTextFields() {
        ChatMessage message = new ChatMessage(null, null, null, null, null, 1234L, false);

        assertEquals("", message.body);
        assertEquals("", message.imageUri);
        assertEquals("", message.senderAddress);
        assertEquals("", message.status);
        assertEquals("", message.localStatusId);
        assertEquals("", message.sourceType);
        assertEquals("", message.sourceId);
        assertEquals(1234L, message.dateMillis);
        assertFalse(message.outgoing);
    }

    @Test
    public void storedMessages_haveStableDeleteIdentity() {
        ChatMessage sms = ChatMessage.storedSms("Hello", "42", 1234L, false);
        ChatMessage mms = ChatMessage.storedLocalMms("Photo", "file:///photo.jpg", "", "", "mms-1", 1235L, true);

        assertTrue(sms.canDeleteStoredMessage());
        assertEquals(ChatMessage.SOURCE_SMS, sms.sourceType);
        assertEquals("42", sms.sourceId);
        assertTrue(mms.canDeleteStoredMessage());
        assertEquals(ChatMessage.SOURCE_LOCAL_MMS, mms.sourceType);
        assertEquals("mms-1", mms.sourceId);
    }

    @Test
    public void conversation_normalizesNullTextFields() {
        Conversation conversation = new Conversation(null, null, null, null, null, 1234L, 2);

        assertEquals("", conversation.threadId);
        assertEquals("", conversation.address);
        assertEquals("", conversation.name);
        assertEquals("", conversation.photoUri);
        assertEquals("", conversation.snippet);
        assertEquals(1234L, conversation.dateMillis);
        assertEquals(2, conversation.unreadCount);
    }

    @Test
    public void chatMessage_displayStatusDistinguishesSentAndReceivedMessages() {
        ChatMessage sent = new ChatMessage("Hello", 1234L, true);
        ChatMessage received = new ChatMessage("Hello", 1234L, false);
        ChatMessage sending = ChatMessage.sending("Hello", 1234L);

        assertEquals("Sent", sent.displayStatus());
        assertEquals("", received.displayStatus());
        assertEquals(ChatMessage.STATUS_SENDING, sending.displayStatus());
    }
}
