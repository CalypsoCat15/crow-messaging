package com.crowmessenger.messages;

import android.text.TextUtils;

import java.util.Calendar;

final class MessageGrouping {
    private static final long GROUP_WINDOW_MILLIS = 5 * 60 * 1000L;

    private MessageGrouping() {
    }

    static boolean canGroup(ChatMessage previous, ChatMessage current) {
        if (previous == null || current == null
                || previous.outgoing != current.outgoing
                || !sameDay(previous.dateMillis, current.dateMillis)
                || Math.abs(current.dateMillis - previous.dateMillis) > GROUP_WINDOW_MILLIS) {
            return false;
        }
        return previous.outgoing || TextUtils.equals(previous.senderAddress, current.senderAddress);
    }

    static boolean sameDay(long firstMillis, long secondMillis) {
        Calendar first = Calendar.getInstance();
        first.setTimeInMillis(firstMillis);
        Calendar second = Calendar.getInstance();
        second.setTimeInMillis(secondMillis);
        return first.get(Calendar.ERA) == second.get(Calendar.ERA)
                && first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }
}
