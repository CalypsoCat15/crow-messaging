package com.crowmessenger.messages;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.Telephony;

import org.robolectric.shadows.ShadowContentResolver;

import java.util.ArrayList;
import java.util.List;

final class TestSmsProvider extends ContentProvider {
    private static final List<Row> ROWS = new ArrayList<>();
    private static boolean queryUnavailable;
    private static ContentValues lastInsertedValues;

    static void install() {
        ROWS.clear();
        queryUnavailable = false;
        lastInsertedValues = null;
        ShadowContentResolver.registerProviderInternal("sms", new TestSmsProvider());
    }

    static void setQueryUnavailable(boolean unavailable) {
        queryUnavailable = unavailable;
    }

    static ContentValues lastInsertedValues() {
        return lastInsertedValues;
    }

    static void add(String id, String threadId, String address, boolean read, boolean seen) {
        ROWS.add(new Row(id, threadId, address, read, seen));
    }

    static boolean isReadAndSeen(String id) {
        for (Row row : ROWS) {
            if (row.id.equals(id)) {
                return row.read && row.seen;
            }
        }
        return false;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (queryUnavailable) {
            return null;
        }
        String[] columns = projection == null
                ? new String[] { Telephony.Sms._ID, Telephony.Sms.ADDRESS }
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        for (Row row : ROWS) {
            if (selection != null && selection.contains(Telephony.Sms.READ) && row.read && row.seen) {
                continue;
            }
            Object[] values = new Object[columns.length];
            for (int index = 0; index < columns.length; index++) {
                String column = columns[index];
                if (Telephony.Sms._ID.equals(column)) {
                    values[index] = row.id;
                } else if (Telephony.Sms.THREAD_ID.equals(column)) {
                    values[index] = row.threadId;
                } else if (Telephony.Sms.ADDRESS.equals(column)) {
                    values[index] = row.address;
                } else if (Telephony.Sms.READ.equals(column)) {
                    values[index] = row.read ? 1 : 0;
                } else if (Telephony.Sms.SEEN.equals(column)) {
                    values[index] = row.seen ? 1 : 0;
                } else {
                    values[index] = null;
                }
            }
            cursor.addRow(values);
        }
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int updated = 0;
        for (Row row : ROWS) {
            if (!matches(row, selection, selectionArgs)) {
                continue;
            }
            Integer read = values.getAsInteger(Telephony.Sms.READ);
            Integer seen = values.getAsInteger(Telephony.Sms.SEEN);
            if (read != null) {
                row.read = read != 0;
            }
            if (seen != null) {
                row.seen = seen != 0;
            }
            updated++;
        }
        return updated;
    }

    private static boolean matches(Row row, String selection, String[] args) {
        if (selection == null || args == null || args.length == 0) {
            return true;
        }
        String value = args[0];
        if (selection.contains(Telephony.Sms.THREAD_ID)) {
            return row.threadId.equals(value);
        }
        if (selection.contains(Telephony.Sms.ADDRESS)) {
            return row.address.equals(value);
        }
        if (selection.contains(Telephony.Sms._ID)) {
            return row.id.equals(value);
        }
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        lastInsertedValues = new ContentValues(values);
        return Uri.withAppendedPath(uri, "inserted");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    private static final class Row {
        final String id;
        final String threadId;
        final String address;
        boolean read;
        boolean seen;

        Row(String id, String threadId, String address, boolean read, boolean seen) {
            this.id = id;
            this.threadId = threadId;
            this.address = address;
            this.read = read;
            this.seen = seen;
        }
    }
}
