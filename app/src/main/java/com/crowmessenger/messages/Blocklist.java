package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class Blocklist {
    private static final String PREFS = "blocked_numbers";
    private static final String KEY_NUMBERS = "numbers";
    private static final String RAW_PREFIX = "raw:";

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
            String rawKey = rawKey(address);
            if (!TextUtils.isEmpty(rawKey)) {
                return blocked.contains(rawKey);
            }
            String number = AddressUtil.digits(address);
            String legacyNumber = AddressUtil.legacyPlusKey(address);
            return (!TextUtils.isEmpty(number) && blocked.contains(number))
                    || (!TextUtils.isEmpty(legacyNumber) && blocked.contains(legacyNumber))
                    || AddressUtil.containsMatchingPhoneValue(blocked, address, Blocklist::phoneBlockedValue);
        }
    }

    static void block(Context context, String address) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return;
        }
        String value = blockKey(address);
        if (TextUtils.isEmpty(value)) {
            return;
        }
        Set<String> updated = new HashSet<>(numbers(context));
        AddressUtil.removeMatchingPhoneValues(updated, address, Blocklist::phoneBlockedValue);
        updated.add(value);
        save(context, updated);
    }

    static void unblock(Context context, String address) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return;
        }
        Set<String> updated = new HashSet<>(numbers(context));
        updated.remove(AddressUtil.digits(address));
        updated.remove(AddressUtil.legacyPlusKey(address));
        updated.remove(rawKey(address));
        AddressUtil.removeMatchingPhoneValues(updated, address, Blocklist::phoneBlockedValue);
        save(context, updated);
    }

    private static String phoneBlockedValue(String value) {
        return !TextUtils.isEmpty(value) && value.startsWith(RAW_PREFIX) ? "" : value;
    }

    private static String blockKey(String address) {
        String rawKey = rawKey(address);
        return TextUtils.isEmpty(rawKey) ? AddressUtil.digits(address) : rawKey;
    }

    private static String rawKey(String address) {
        if (TextUtils.isEmpty(address) || AddressUtil.isSendableSmsRecipient(address)) {
            return "";
        }
        return RAW_PREFIX + address.trim().toLowerCase(Locale.US);
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
