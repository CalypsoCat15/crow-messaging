package com.crowmessenger.messages;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;

final class MessageLinkUtil {
    private MessageLinkUtil() {
    }

    static SpannableString linkifyWebUrls(CharSequence text) {
        SpannableString linked = new SpannableString(text == null ? "" : text);
        Linkify.addLinks(linked, Linkify.WEB_URLS);
        trimTrailingSentencePunctuation(linked);
        return linked;
    }

    static boolean hasWebLinks(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return false;
        }
        Spanned spanned = (Spanned) text;
        return spanned.getSpans(0, spanned.length(), URLSpan.class).length > 0;
    }

    static String firstWebUrl(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return "";
        }
        Spanned spanned = (Spanned) text;
        URLSpan[] links = spanned.getSpans(0, spanned.length(), URLSpan.class);
        URLSpan first = null;
        int firstStart = Integer.MAX_VALUE;
        for (URLSpan link : links) {
            int start = spanned.getSpanStart(link);
            if (start >= 0 && start < firstStart) {
                first = link;
                firstStart = start;
            }
        }
        return first == null || first.getURL() == null ? "" : first.getURL();
    }

    private static void trimTrailingSentencePunctuation(SpannableString text) {
        URLSpan[] links = text.getSpans(0, text.length(), URLSpan.class);
        for (URLSpan link : links) {
            int start = text.getSpanStart(link);
            int originalEnd = text.getSpanEnd(link);
            int end = originalEnd;
            while (end > start && isTrailingSentencePunctuation(text, start, end)) {
                end--;
            }
            if (end == originalEnd) {
                continue;
            }
            int flags = text.getSpanFlags(link);
            String url = link.getURL();
            int removedCharacters = originalEnd - end;
            if (url.length() >= removedCharacters) {
                url = url.substring(0, url.length() - removedCharacters);
            }
            text.removeSpan(link);
            text.setSpan(new URLSpan(url), start, end, flags == 0
                    ? Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    : flags);
        }
    }

    private static boolean isTrailingSentencePunctuation(CharSequence text, int start, int end) {
        char last = text.charAt(end - 1);
        if (last == '.' || last == ',' || last == '!' || last == '?'
                || last == ';' || last == ':' || last == '\u2026') {
            return true;
        }
        if (last == ')') {
            return count(text, start, end, ')') > count(text, start, end, '(');
        }
        if (last == ']') {
            return count(text, start, end, ']') > count(text, start, end, '[');
        }
        return last == '}' && count(text, start, end, '}') > count(text, start, end, '{');
    }

    private static int count(CharSequence text, int start, int end, char target) {
        int matches = 0;
        for (int index = start; index < end; index++) {
            if (text.charAt(index) == target) {
                matches++;
            }
        }
        return matches;
    }
}
