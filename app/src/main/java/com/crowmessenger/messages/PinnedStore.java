package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;

final class PinnedStore {
    private static final String PREFS = "pinned_conversations";
    private static final String KEY_ADDRESSES = "addresses";

    private PinnedStore() {
    }

    static boolean isPinned(Context context, String address) {
        String key = key(address);
        Set<String> pins = pins(context);
        return !TextUtils.isEmpty(key) && (pins.contains(key) || AddressUtil.containsMatchingPhoneValue(pins, address));
    }

    static void pin(Context context, String address) {
        String key = key(address);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        Set<String> updated = pins(context);
        AddressUtil.removeMatchingPhoneValues(updated, address);
        updated.add(key);
        save(context, updated);
    }

    static void unpin(Context context, String address) {
        String key = key(address);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        Set<String> updated = pins(context);
        updated.remove(key);
        AddressUtil.removeMatchingPhoneValues(updated, address);
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
