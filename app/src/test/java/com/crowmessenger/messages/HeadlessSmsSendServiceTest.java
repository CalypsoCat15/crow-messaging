package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class HeadlessSmsSendServiceTest {
    @Test
    public void replyRequest_readsRecipientAndTextFromRespondViaMessageIntent() {
        Intent intent = new Intent(HeadlessSmsSendService.ACTION_RESPOND_VIA_MESSAGE, Uri.parse("smsto:+15551234567"));
        intent.putExtra(Intent.EXTRA_TEXT, " On my way ");

        HeadlessSmsSendService.ReplyRequest request = HeadlessSmsSendService.replyRequest(intent);

        assertNotNull(request);
        assertEquals("+15551234567", request.address);
        assertEquals("On my way", request.body);
    }

    @Test
    public void replyRequest_decodesEncodedRecipientAddress() {
        Intent intent = new Intent(HeadlessSmsSendService.ACTION_RESPOND_VIA_MESSAGE, Uri.parse("smsto:%2B15551234567"));
        intent.putExtra(Intent.EXTRA_TEXT, "On my way");

        HeadlessSmsSendService.ReplyRequest request = HeadlessSmsSendService.replyRequest(intent);

        assertNotNull(request);
        assertEquals("+15551234567", request.address);
        assertEquals("On my way", request.body);
    }

    @Test
    public void replyRequest_readsLegacySmsBodyExtra() {
        Intent intent = new Intent(HeadlessSmsSendService.ACTION_RESPOND_VIA_MESSAGE, Uri.parse("sms:+15551234567?body=ignored"));
        intent.putExtra("sms_body", "Yes");

        HeadlessSmsSendService.ReplyRequest request = HeadlessSmsSendService.replyRequest(intent);

        assertNotNull(request);
        assertEquals("+15551234567", request.address);
        assertEquals("Yes", request.body);
    }

    @Test
    public void replyRequest_rejectsMissingBodyOrRecipient() {
        assertNull(HeadlessSmsSendService.replyRequest(new Intent(
                HeadlessSmsSendService.ACTION_RESPOND_VIA_MESSAGE,
                Uri.parse("smsto:+15551234567")
        )));

        Intent noRecipient = new Intent(HeadlessSmsSendService.ACTION_RESPOND_VIA_MESSAGE);
        noRecipient.putExtra(Intent.EXTRA_TEXT, "hello");
        assertNull(HeadlessSmsSendService.replyRequest(noRecipient));
    }

    @Test
    public void replyRequest_rejectsOtherActions() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+15551234567"));
        intent.putExtra(Intent.EXTRA_TEXT, "hello");

        assertNull(HeadlessSmsSendService.replyRequest(intent));
    }
}
