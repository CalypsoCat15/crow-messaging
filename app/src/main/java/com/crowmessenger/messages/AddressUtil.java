package com.crowmessenger.messages;

import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;

final class AddressUtil {
    private static final int MIN_SUFFIX_MATCH_DIGITS = 7;
    private static final String SMS_RECIPIENT_CHARACTERS = "+0123456789().- ";

    private AddressUtil() {
    }

    static String digits(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        return address.replaceAll("[^0-9]", "");
    }

    static String legacyPlusKey(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        return address.replaceAll("[^0-9+]", "");
    }

    static boolean sameDigits(String first, String second) {
        String a = digits(first);
        String b = digits(second);
        if (TextUtils.isEmpty(a) || TextUtils.isEmpty(b)) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return a.length() >= MIN_SUFFIX_MATCH_DIGITS
                && b.length() >= MIN_SUFFIX_MATCH_DIGITS
                && (a.endsWith(b) || b.endsWith(a));
    }

    static boolean sameConversationAddress(String first, String second) {
        if (LocalMmsStore.isGroupAddress(first) || LocalMmsStore.isGroupAddress(second)) {
            return TextUtils.equals(first, second);
        }
        if (TextUtils.equals(first, second)) {
            return true;
        }
        return sameDigits(first, second);
    }

    static boolean hasSinglePhoneAddress(String address) {
        return !LocalMmsStore.isGroupAddress(address) && !TextUtils.isEmpty(digits(address));
    }

    static boolean isSendableSmsRecipient(String address) {
        if (!hasSinglePhoneAddress(address)) {
            return false;
        }
        String value = address.trim();
        for (int index = 0; index < value.length(); index++) {
            if (SMS_RECIPIENT_CHARACTERS.indexOf(value.charAt(index)) < 0) {
                return false;
            }
        }
        return true;
    }

    static boolean isMatchingPhoneValue(String address, String storedValue) {
        return hasSinglePhoneAddress(address)
                && hasSinglePhoneAddress(storedValue)
                && sameDigits(address, storedValue);
    }

    static boolean containsMatchingPhoneValue(Set<String> storedValues, String address) {
        return containsMatchingPhoneValue(storedValues, address, value -> value);
    }

    static boolean containsMatchingPhoneValue(
            Set<String> storedValues,
            String address,
            PhoneValueExtractor extractor
    ) {
        if (!hasSinglePhoneAddress(address) || storedValues == null) {
            return false;
        }
        for (String storedValue : storedValues) {
            String phoneValue = extractor.phoneValue(storedValue);
            if (isMatchingPhoneValue(address, phoneValue)) {
                return true;
            }
        }
        return false;
    }

    static void removeMatchingPhoneValues(Set<String> storedValues, String address) {
        removeMatchingPhoneValues(storedValues, address, value -> value);
    }

    static void removeMatchingPhoneValues(
            Set<String> storedValues,
            String address,
            PhoneValueExtractor extractor
    ) {
        if (!hasSinglePhoneAddress(address) || storedValues == null) {
            return;
        }
        Set<String> removals = new HashSet<>();
        for (String storedValue : storedValues) {
            String phoneValue = extractor.phoneValue(storedValue);
            if (isMatchingPhoneValue(address, phoneValue)) {
                removals.add(storedValue);
            }
        }
        storedValues.removeAll(removals);
    }

    static int stableId(String value) {
        return stableKey(value).hashCode() & 0x7fffffff;
    }

    static int stableId(String first, String second) {
        return stableKey(first).concat("|").concat(stableKey(second)).hashCode() & 0x7fffffff;
    }

    static String stableKey(String value) {
        if (LocalMmsStore.isGroupAddress(value)) {
            return value;
        }
        String digits = digits(value);
        if (!TextUtils.isEmpty(digits)) {
            return digits;
        }
        return TextUtils.isEmpty(value) ? "unknown" : value;
    }

    interface PhoneValueExtractor {
        String phoneValue(String value);
    }
}
