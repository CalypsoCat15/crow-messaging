package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ContactNotificationPrefs {
    static final String SILENT = "__silent__";
    private static final String PREFS = "contact_notification_sounds";
    private static final String GROUP_KEY_PREFIX = "group:";

    private ContactNotificationPrefs() {
    }

    static Uri soundUri(Context context, String address) {
        return setting(context, address).soundUri();
    }

    static boolean hasCustomSound(Context context, String address) {
        return setting(context, address).hasCustomSound();
    }

    static boolean hasCustomSetting(Context context, String address) {
        return setting(context, address).hasCustomSetting();
    }

    static boolean isSilent(Context context, String address) {
        return setting(context, address).isSilent();
    }

    static String soundKey(Context context, String address) {
        return setting(context, address).key();
    }

    static NotificationSetting setting(Context context, String address) {
        return new NotificationSetting(rawSound(context, address));
    }

    static void setSound(Context context, String address, Uri uri) {
        String key = key(address);
        String legacyKey = legacyKey(address);
        if (TextUtils.isEmpty(key) && TextUtils.isEmpty(legacyKey)) {
            return;
        }
        String storeKey = TextUtils.isEmpty(key) ? legacyKey : key;
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit()
                .putString(storeKey, uri == null ? SILENT : uri.toString());
        if (!storeKey.equals(legacyKey)) {
            editor.remove(legacyKey);
        }
        removeMatchingKeys(preferences, editor, address, storeKey);
        editor.apply();
    }

    static void useDefault(Context context, String address) {
        String key = key(address);
        String legacyKey = legacyKey(address);
        if (TextUtils.isEmpty(key) && TextUtils.isEmpty(legacyKey)) {
            return;
        }
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit()
                .remove(key)
                .remove(legacyKey);
        removeMatchingKeys(preferences, editor, address, "");
        editor.apply();
    }

    private static String rawSound(Context context, String address) {
        String key = key(address);
        String legacyKey = legacyKey(address);
        if (TextUtils.isEmpty(key) && TextUtils.isEmpty(legacyKey)) {
            return "";
        }
        SharedPreferences prefs = prefs(context);
        String value = prefs.getString(key, "");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }
        value = prefs.getString(legacyKey, "");
        if (!TextUtils.isEmpty(value)) {
            return migrateSound(prefs, address, key, legacyKey, value);
        }
        if (!LocalMmsStore.isGroupAddress(address)) {
            Map<String, ?> storedValues = prefs.getAll();
            for (String storedKey : matchingPhoneKeys(prefs, address)) {
                Object stored = storedValues.get(storedKey);
                if (stored instanceof String
                        && !TextUtils.isEmpty((String) stored)) {
                    return migrateSound(prefs, address, key, storedKey, (String) stored);
                }
            }
        }
        return "";
    }

    private static String migrateSound(
            SharedPreferences prefs,
            String address,
            String key,
            String storedKey,
            String value
    ) {
        if (TextUtils.isEmpty(key) || key.equals(storedKey)) {
            return value;
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString(key, value)
                .remove(storedKey);
        removeMatchingKeys(prefs, editor, address, key);
        editor.apply();
        return value;
    }

    private static void removeMatchingKeys(SharedPreferences prefs, SharedPreferences.Editor editor, String address, String keepKey) {
        if (!AddressUtil.hasSinglePhoneAddress(address)) {
            return;
        }
        for (String storedKey : matchingPhoneKeys(prefs, address)) {
            if (!storedKey.equals(keepKey)) {
                editor.remove(storedKey);
            }
        }
    }

    private static List<String> matchingPhoneKeys(SharedPreferences prefs, String address) {
        List<String> keys = new ArrayList<>();
        if (!AddressUtil.hasSinglePhoneAddress(address)) {
            return keys;
        }
        for (String storedKey : prefs.getAll().keySet()) {
            if (AddressUtil.isMatchingPhoneValue(address, storedKey)) {
                keys.add(storedKey);
            }
        }
        return keys;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String address) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return GROUP_KEY_PREFIX + address;
        }
        return AddressUtil.digits(address);
    }

    private static String legacyKey(String address) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return AddressUtil.digits(address);
        }
        return AddressUtil.legacyPlusKey(address);
    }

    static final class NotificationSetting {
        private final String value;

        private NotificationSetting(String value) {
            this.value = TextUtils.isEmpty(value) ? "" : value;
        }

        boolean hasCustomSetting() {
            return !TextUtils.isEmpty(value);
        }

        boolean hasCustomSound() {
            return hasCustomSetting() && !isSilent();
        }

        boolean isSilent() {
            return SILENT.equals(value);
        }

        Uri soundUri() {
            return hasCustomSound() ? Uri.parse(value) : null;
        }

        String key() {
            return value;
        }
    }
}
