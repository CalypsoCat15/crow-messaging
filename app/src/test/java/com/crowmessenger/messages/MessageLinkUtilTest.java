package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.URLSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MessageLinkUtilTest {
    @Test
    public void linkifyWebUrls_linksFullUrlWithoutTrailingPunctuation() {
        SpannableString linked = MessageLinkUtil.linkifyWebUrls(
                "Open https://example.com/news?item=42."
        );

        URLSpan[] spans = linked.getSpans(0, linked.length(), URLSpan.class);
        assertEquals(1, spans.length);
        assertEquals("https://example.com/news?item=42", spans[0].getURL());
        assertTrue(MessageLinkUtil.hasWebLinks(linked));
    }

    @Test
    public void linkifyWebUrls_linksWwwAndBareDomains() {
        SpannableString linked = MessageLinkUtil.linkifyWebUrls(
                "Try www.example.com or example.org/help"
        );

        URLSpan[] spans = linked.getSpans(0, linked.length(), URLSpan.class);
        assertEquals(2, spans.length);
        assertEquals("http://www.example.com", spans[0].getURL());
        assertEquals("http://example.org/help", spans[1].getURL());
        assertEquals("http://www.example.com", MessageLinkUtil.firstWebUrl(linked));
    }

    @Test
    public void linkifyWebUrls_preservesBalancedClosingParenthesis() {
        SpannableString linked = MessageLinkUtil.linkifyWebUrls(
                "Read https://example.com/wiki/Test_(film)."
        );

        URLSpan[] spans = linked.getSpans(0, linked.length(), URLSpan.class);
        assertEquals(1, spans.length);
        assertEquals("https://example.com/wiki/Test_(film)", spans[0].getURL());
        assertEquals(')', linked.charAt(linked.getSpanEnd(spans[0]) - 1));
    }

    @Test
    public void linkifyWebUrls_leavesOrdinaryTextUnlinked() {
        SpannableString linked = MessageLinkUtil.linkifyWebUrls(
                "Dinner is at 6 and the confirmation code is 482193."
        );

        assertFalse(MessageLinkUtil.hasWebLinks(linked));
        assertEquals(0, linked.getSpans(0, linked.length(), URLSpan.class).length);
    }

    @Test
    public void linkifyWebUrls_preservesSearchHighlightSpans() {
        SpannableString highlighted = new SpannableString("Visit example.com today");
        BackgroundColorSpan highlight = new BackgroundColorSpan(Color.YELLOW);
        highlighted.setSpan(highlight, 6, 17, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        SpannableString linked = MessageLinkUtil.linkifyWebUrls(highlighted);

        BackgroundColorSpan[] highlights = linked.getSpans(
                0, linked.length(), BackgroundColorSpan.class
        );
        assertEquals(1, highlights.length);
        assertSame(highlight, highlights[0]);
        assertTrue(MessageLinkUtil.hasWebLinks(linked));
    }
}
