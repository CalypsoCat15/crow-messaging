package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;

final class ComposerPrefs {
    private static final String PREFS = "composer_prefs";
    private static final String KEY_VOICE_BUTTON_VISIBLE = "voice_button_visible";

    private ComposerPrefs() {
    }

    static boolean voiceButtonVisible(Context context) {
        return prefs(context).getBoolean(KEY_VOICE_BUTTON_VISIBLE, true);
    }

    static void setVoiceButtonVisible(Context context, boolean visible) {
        prefs(context).edit()
                .putBoolean(KEY_VOICE_BUTTON_VISIBLE, visible)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
