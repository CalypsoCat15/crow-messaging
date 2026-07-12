package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class MmsDebugStore {
    private static final String PREFS = "mms_debug";
    private static final String KEY_LAST = "last";
    private static final String KEY_ARCHIVE_RAW_PDUS = "archive_raw_pdus";
    private static final int MAX_LINES = 8;
    private static final int MAX_ARCHIVED_PDU_FILES = 5;
    private static final long MAX_ARCHIVED_PDU_AGE_MILLIS = 3L * 24L * 60L * 60L * 1000L;

    private MmsDebugStore() {
    }

    static void record(Context context, String message) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String timestamp = DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.getDefault()).format(new Date());
        String line = timestamp + ": " + redactPhoneNumbers(message);
        String previous = prefs.getString(KEY_LAST, "");
        String combined = TextUtilsCompat.joinLines(line, previous, MAX_LINES);
        prefs.edit()
                .putString(KEY_LAST, combined)
                .apply();
    }

    static String last(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST, "No MMS notice received yet.");
    }

    static boolean shouldArchiveRawPdus(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ARCHIVE_RAW_PDUS, false);
    }

    static void setArchiveRawPdus(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ARCHIVE_RAW_PDUS, enabled)
                .apply();
    }

    static void trimArchivedPduFiles(Context context) {
        trimArchivedPduFiles(context, MmsFiles.NOTICES_DIR);
        trimArchivedPduFiles(context, MmsFiles.RAW_DOWNLOADS_DIR);
        trimArchivedPduFiles(context, MmsFiles.UNREADABLE_DIR);
    }

    private static void trimArchivedPduFiles(Context context, String dirName) {
        File directory = MmsFiles.appFileDirPath(context, dirName);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".pdu"));
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - MAX_ARCHIVED_PDU_AGE_MILLIS;
        List<File> recentFiles = new ArrayList<>();
        for (File file : files) {
            if (file.lastModified() > 0 && file.lastModified() < cutoff) {
                MmsFiles.deleteAppFile(context, dirName, file.getAbsolutePath());
            } else {
                recentFiles.add(file);
            }
        }
        if (recentFiles.size() <= MAX_ARCHIVED_PDU_FILES) {
            return;
        }
        File[] recent = recentFiles.toArray(new File[0]);
        Arrays.sort(recent, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < recent.length - MAX_ARCHIVED_PDU_FILES; i++) {
            MmsFiles.deleteAppFile(context, dirName, recent[i].getAbsolutePath());
        }
    }

    static String redactPhoneNumbers(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < message.length()) {
            char c = message.charAt(i);
            if (!Character.isDigit(c) && c != '+') {
                result.append(c);
                i++;
                continue;
            }

            int start = i;
            if (c == '+') {
                i++;
            }
            while (i < message.length()
                    && (Character.isDigit(message.charAt(i)) || isPhoneSeparator(message.charAt(i)))) {
                i++;
            }
            int end = trimTrailingPhoneSeparators(message, start, i);
            String value = message.substring(start, end);
            String digits = value.replaceAll("[^0-9]", "");
            if (digits.length() >= 7 && !isDiagnosticNumber(message, start, end)) {
                result.append("***").append(digits.substring(Math.max(0, digits.length() - 4)));
            } else {
                result.append(value);
            }
            if (end < i) {
                result.append(message, end, i);
            }
        }
        return result.toString();
    }

    private static boolean isPhoneSeparator(char value) {
        return value == ' ' || value == '-' || value == '(' || value == ')' || value == '.';
    }

    private static int trimTrailingPhoneSeparators(String message, int start, int end) {
        int trimmed = end;
        while (trimmed > start && isPhoneSeparator(message.charAt(trimmed - 1))) {
            trimmed--;
        }
        return trimmed;
    }

    private static boolean isDiagnosticNumber(String message, int start, int end) {
        String prefix = message.substring(Math.max(0, start - 18), start);
        String suffix = message.substring(end, Math.min(message.length(), end + 5));
        return prefix.endsWith("bytes=")
                || prefix.endsWith("imageBytes=")
                || prefix.endsWith("responseBytes=")
                || prefix.endsWith("textLength=")
                || prefix.endsWith("http=")
                || prefix.endsWith("subId=")
                || prefix.endsWith("raw=")
                || prefix.endsWith("saved=")
                || prefix.endsWith("file=")
                || suffix.startsWith(".pdu");
    }

    private static final class TextUtilsCompat {
        private TextUtilsCompat() {
        }

        static String joinLines(String first, String rest, int maxLines) {
            StringBuilder builder = new StringBuilder(first);
            int count = 1;
            if (rest != null && !rest.isEmpty()) {
                for (String line : rest.split("\\n")) {
                    if (count >= maxLines) {
                        break;
                    }
                    if (!line.trim().isEmpty()) {
                        builder.append('\n').append(line);
                        count++;
                    }
                }
            }
            return builder.toString();
        }
    }
}
