package com.crowmessenger.messages;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;

import org.robolectric.shadows.ShadowContentResolver;

import java.util.HashMap;
import java.util.Map;

final class TestContactsProvider extends ContentProvider {
    private static final Map<String, String> NAMES_BY_NUMBER = new HashMap<>();
    private static int queryCount;

    static void install() {
        ShadowContentResolver.registerProviderInternal("com.android.contacts", new TestContactsProvider());
    }

    static void clear() {
        NAMES_BY_NUMBER.clear();
        queryCount = 0;
        ContactLookup.clearCache();
    }

    static void add(String number, String name) {
        NAMES_BY_NUMBER.put(number, name);
        ContactLookup.clearCache();
    }

    static void addWithoutClearingCache(String number, String name) {
        NAMES_BY_NUMBER.put(number, name);
    }

    static int queryCount() {
        return queryCount;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        queryCount++;
        String number = uri == null ? "" : Uri.decode(uri.getLastPathSegment());
        String name = NAMES_BY_NUMBER.get(number);
        String[] columns = projection == null
                ? new String[] { ContactsContract.PhoneLookup._ID }
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        if (name == null) {
            return cursor;
        }
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (ContactsContract.PhoneLookup._ID.equals(columns[i])) {
                values[i] = 1L;
            } else if (ContactsContract.PhoneLookup.DISPLAY_NAME.equals(columns[i])) {
                values[i] = name;
            } else {
                values[i] = "";
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
