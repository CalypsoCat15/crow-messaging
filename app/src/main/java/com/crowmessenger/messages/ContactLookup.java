package com.crowmessenger.messages;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ContactLookup {
    private static final Map<String, Boolean> SAVED_CONTACT_CACHE = new ConcurrentHashMap<>();

    private ContactLookup() {
    }

    static boolean isSavedContact(Context context, String address) {
        String key = cacheKey(address);
        if (TextUtils.isEmpty(key) || LocalMmsStore.isGroupAddress(address)) {
            return false;
        }
        Boolean cached = SAVED_CONTACT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        boolean saved = querySavedContact(context, address);
        if (saved) {
            SAVED_CONTACT_CACHE.put(key, true);
        } else {
            SAVED_CONTACT_CACHE.remove(key);
        }
        return saved;
    }

    static void clearCache() {
        SAVED_CONTACT_CACHE.clear();
    }

    private static boolean querySavedContact(Context context, String address) {
        Uri lookup = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
        );
        try (Cursor cursor = context.getContentResolver().query(
                lookup,
                new String[] { ContactsContract.PhoneLookup._ID },
                null,
                null,
                null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (SecurityException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String cacheKey(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        String digits = AddressUtil.digits(address);
        return TextUtils.isEmpty(digits)
                ? address.trim().toLowerCase(Locale.US)
                : digits;
    }
}
