package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;

final class TextSizePrefs {
    private static final String PREFS = "crow_text_size";
    private static final String KEY_LABEL = "label";
    static final String SMALL = "Small";
    static final String NORMAL = "Normal";
    static final String LARGE = "Large";
    static final String EXTRA_LARGE = "Extra large";
    private static final TextSize NORMAL_SIZE = new TextSize(NORMAL, 17, 14, 16, 12, 11, 16);
    private static final TextSize[] SIZES = new TextSize[] {
            new TextSize(SMALL, 16, 13, 15, 12, 11, 15),
            NORMAL_SIZE,
            new TextSize(LARGE, 19, 16, 18, 13, 12, 18),
            new TextSize(EXTRA_LARGE, 21, 18, 20, 14, 13, 20)
    };

    private TextSizePrefs() {
    }

    static String currentLabel(Context context) {
        String label = prefs(context).getString(KEY_LABEL, NORMAL);
        return sizeForLabel(label).label;
    }

    static String[] labels() {
        String[] labels = new String[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            labels[i] = SIZES[i].label;
        }
        return labels;
    }

    static void setLabel(Context context, String label) {
        prefs(context).edit()
                .putString(KEY_LABEL, sizeForLabel(label).label)
                .apply();
    }

    static int inboxNameSp(Context context) {
        return currentSize(context).inboxNameSp;
    }

    static int inboxPreviewSp(Context context) {
        return currentSize(context).inboxPreviewSp;
    }

    static int messageSp(Context context) {
        return currentSize(context).messageSp;
    }

    static int senderSp(Context context) {
        return currentSize(context).senderSp;
    }

    static int timestampSp(Context context) {
        return currentSize(context).timestampSp;
    }

    static int composerSp(Context context) {
        return currentSize(context).composerSp;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static TextSize currentSize(Context context) {
        return sizeForLabel(prefs(context).getString(KEY_LABEL, NORMAL));
    }

    private static TextSize sizeForLabel(String label) {
        for (TextSize size : SIZES) {
            if (size.label.equals(label)) {
                return size;
            }
        }
        return NORMAL_SIZE;
    }

    private static final class TextSize {
        final String label;
        final int inboxNameSp;
        final int inboxPreviewSp;
        final int messageSp;
        final int senderSp;
        final int timestampSp;
        final int composerSp;

        TextSize(
                String label,
                int inboxNameSp,
                int inboxPreviewSp,
                int messageSp,
                int senderSp,
                int timestampSp,
                int composerSp
        ) {
            this.label = label;
            this.inboxNameSp = inboxNameSp;
            this.inboxPreviewSp = inboxPreviewSp;
            this.messageSp = messageSp;
            this.senderSp = senderSp;
            this.timestampSp = timestampSp;
            this.composerSp = composerSp;
        }
    }
}
