package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PinnedStore {
    private static final String PREFS = "pinned_conversations";
    private static final String KEY_ADDRESSES = "addresses";

    private PinnedStore() {
    }

    static boolean isPinned(Context context, String address) {
        return new Matcher(pins(context)).isPinned(address);
    }

    static void sortConversations(Context context, List<Conversation> conversations) {
        if (conversations == null || conversations.size() < 2) {
            return;
        }
        Matcher matcher = new Matcher(pins(context));
        conversations.sort((left, right) -> {
            boolean leftPinned = matcher.isPinned(left.address);
            boolean rightPinned = matcher.isPinned(right.address);
            if (leftPinned != rightPinned) {
                return leftPinned ? -1 : 1;
            }
            return Long.compare(right.dateMillis, left.dateMillis);
        });
    }

    private static final class Matcher {
        private final Set<String> pins;

        private Matcher(Set<String> pins) {
            this.pins = pins;
        }

        private boolean isPinned(String address) {
            String key = key(address);
            return !TextUtils.isEmpty(key)
                    && (pins.contains(key) || AddressUtil.containsMatchingConversationAddress(pins, address));
        }
    }

    static void pin(Context context, String address) {
        String key = key(address);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        Set<String> updated = pins(context);
        AddressUtil.removeMatchingConversationAddresses(updated, address);
        updated.add(key);
        save(context, updated);
    }

    static void unpin(Context context, String address) {
        String key = key(address);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        Set<String> updated = pins(context);
        AddressUtil.removeMatchingConversationAddresses(updated, address);
        save(context, updated);
    }

    private static String key(String address) {
        return TextUtils.isEmpty(address) ? "" : AddressUtil.stableKey(address);
    }

    private static Set<String> pins(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_ADDRESSES, new HashSet<>()));
    }

    private static void save(Context context, Set<String> pins) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_ADDRESSES, pins)
                .apply();
    }
}
