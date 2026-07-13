package com.crowmessenger.messages;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.Telephony;

import org.robolectric.shadows.ShadowContentResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TestSmsProvider extends ContentProvider {
    private static final List<Row> ROWS = new ArrayList<>();
    private static boolean queryUnavailable;
    private static RuntimeException insertFailure;
    private static ContentValues lastInsertedValues;
    private static int messageQueryCount;

    static void install() {
        ROWS.clear();
        queryUnavailable = false;
        insertFailure = null;
        lastInsertedValues = null;
        messageQueryCount = 0;
        ShadowContentResolver.registerProviderInternal("sms", new TestSmsProvider());
    }

    static void setQueryUnavailable(boolean unavailable) {
        queryUnavailable = unavailable;
    }

    static ContentValues lastInsertedValues() {
        return lastInsertedValues;
    }

    static void setInsertFailure(RuntimeException failure) {
        insertFailure = failure;
    }

    static void add(String id, String threadId, String address, boolean read, boolean seen) {
        ROWS.add(new Row(
                id,
                threadId,
                address,
                "",
                0L,
                Telephony.Sms.MESSAGE_TYPE_INBOX,
                read,
                seen
        ));
    }

    static void addMessage(String id, String threadId, String address, String body, long date, int type) {
        ROWS.add(new Row(id, threadId, address, body, date, type, true, true));
    }

    static int messageQueryCount() {
        return messageQueryCount;
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
        if (containsColumn(columns, Telephony.Sms.BODY)) {
            messageQueryCount++;
        }
        List<Row> matchingRows = new ArrayList<>();
        for (Row row : ROWS) {
            if (matchesQuery(row, selection, selectionArgs)) {
                matchingRows.add(row);
            }
        }
        String normalizedSort = sortOrder == null ? "" : sortOrder.toUpperCase(Locale.US);
        if (normalizedSort.contains(Telephony.Sms.DATE.toUpperCase(Locale.US))) {
            Comparator<Row> byDate = Comparator.comparingLong(row -> row.date);
            matchingRows.sort(normalizedSort.contains("DESC") ? byDate.reversed() : byDate);
        }
        int limit = queryLimit(sortOrder);
        MatrixCursor cursor = new MatrixCursor(columns);
        for (int rowIndex = 0; rowIndex < matchingRows.size() && rowIndex < limit; rowIndex++) {
            Row row = matchingRows.get(rowIndex);
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
                } else if (Telephony.Sms.BODY.equals(column)) {
                    values[index] = row.body;
                } else if (Telephony.Sms.DATE.equals(column)) {
                    values[index] = row.date;
                } else if (Telephony.Sms.TYPE.equals(column)) {
                    values[index] = row.type;
                } else {
                    values[index] = null;
                }
            }
            cursor.addRow(values);
        }
        return cursor;
    }

    private static boolean containsColumn(String[] columns, String expected) {
        for (String column : columns) {
            if (expected.equals(column)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesQuery(Row row, String selection, String[] args) {
        if (selection != null
                && (selection.contains(Telephony.Sms.READ + "=0")
                || selection.contains(Telephony.Sms.SEEN + "=0"))
                && row.read
                && row.seen) {
            return false;
        }
        if (selection == null || args == null || args.length == 0) {
            return true;
        }
        if (selection.contains(Telephony.Sms.THREAD_ID + "=?")) {
            return row.threadId.equals(args[0]);
        }
        if (selection.contains(Telephony.Sms.ADDRESS + "=?")) {
            return row.address.equals(args[0]);
        }
        return true;
    }

    private static int queryLimit(String sortOrder) {
        if (sortOrder == null) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)").matcher(sortOrder);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(matcher.group(1));
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
        if (insertFailure != null) {
            throw insertFailure;
        }
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
        final String body;
        final long date;
        final int type;
        boolean read;
        boolean seen;

        Row(
                String id,
                String threadId,
                String address,
                String body,
                long date,
                int type,
                boolean read,
                boolean seen
        ) {
            this.id = id;
            this.threadId = threadId;
            this.address = address;
            this.body = body;
            this.date = date;
            this.type = type;
            this.read = read;
            this.seen = seen;
        }
    }
}
