package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;

final class Blocklist {
    private static final String PREFS = "blocked_numbers";
    private static final String KEY_NUMBERS = "numbers";

    private Blocklist() {
    }

    static boolean isBlocked(Context context, String address) {
        return matcher(context).isBlocked(address);
    }

    static Matcher matcher(Context context) {
        return new Matcher(numbers(context));
    }

    static final class Matcher {
        private final Set<String> blocked;

        private Matcher(Set<String> blocked) {
            this.blocked = blocked;
        }

        boolean isBlocked(String address) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return false;
        }
        String number = AddressUtil.digits(address);
        String legacyNumber = AddressUtil.legacyPlusKey(address);
        return (!TextUtils.isEmpty(number) && blocked.contains(number))
                || (!TextUtils.isEmpty(legacyNumber) && blocked.contains(legacyNumber))
                || AddressUtil.containsMatchingPhoneValue(blocked, address);
        }
    }

    static void block(Context context, String address) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return;
        }
        String number = AddressUtil.digits(address);
        if (TextUtils.isEmpty(number)) {
            return;
        }
        Set<String> updated = new HashSet<>(numbers(context));
        AddressUtil.removeMatchingPhoneValues(updated, address);
        updated.add(number);
        save(context, updated);
    }

    static void unblock(Context context, String address) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return;
        }
        Set<String> updated = new HashSet<>(numbers(context));
        updated.remove(AddressUtil.digits(address));
        updated.remove(AddressUtil.legacyPlusKey(address));
        AddressUtil.removeMatchingPhoneValues(updated, address);
        save(context, updated);
    }

    private static Set<String> numbers(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_NUMBERS, new HashSet<>()));
    }

    private static void save(Context context, Set<String> numbers) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_NUMBERS, numbers)
                .apply();
    }
}
