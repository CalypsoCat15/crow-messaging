package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.net.InetAddress;

import org.junit.Test;

public class LinkPreviewUrlPolicyTest {
    @Test
    public void normalizedFetchUrl_upgradesHttpAndRemovesFragment() {
        assertEquals(
                "https://example.com/news?item=42",
                LinkPreviewUrlPolicy.normalizedFetchUrl("http://example.com/news?item=42#comments")
        );
    }

    @Test
    public void normalizedFetchUrl_rejectsUnsafeDestinations() {
        assertEquals("", LinkPreviewUrlPolicy.normalizedFetchUrl("file:///sdcard/message.txt"));
        assertEquals("", LinkPreviewUrlPolicy.normalizedFetchUrl("https://localhost/page"));
        assertEquals("", LinkPreviewUrlPolicy.normalizedFetchUrl("https://router.local/page"));
        assertEquals("", LinkPreviewUrlPolicy.normalizedFetchUrl("https://example.com:8080/page"));
        assertEquals("", LinkPreviewUrlPolicy.normalizedFetchUrl("https://user@example.com/page"));
    }

    @Test
    public void isPublicAddress_rejectsPrivateAndSpecialAddresses() throws Exception {
        assertFalse(LinkPreviewUrlPolicy.isPublicAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(LinkPreviewUrlPolicy.isPublicAddress(InetAddress.getByName("10.0.0.2")));
        assertFalse(LinkPreviewUrlPolicy.isPublicAddress(InetAddress.getByName("192.168.1.10")));
        assertFalse(LinkPreviewUrlPolicy.isPublicAddress(InetAddress.getByName("100.64.0.1")));
        assertFalse(LinkPreviewUrlPolicy.isPublicAddress(InetAddress.getByName("fc00::1")));
    }
}
