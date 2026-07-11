package com.crowmessenger.messages;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SettingsBackup {
    private static final String FORMAT = "crow-settings";
    private static final int VERSION = 1;

    private SettingsBackup() {
    }

    static String create(Context context) throws JSONException {
        JSONObject backup = new JSONObject();
        backup.put("format", FORMAT);
        backup.put("version", VERSION);
        backup.put("textSize", TextSizePrefs.currentLabel(context));
        backup.put("microphoneButton", ComposerPrefs.voiceButtonVisible(context));
        backup.put("spamKeywords", new JSONArray(SpamFilter.customKeywords(context)));
        return backup.toString(2);
    }

    static int restore(Context context, String rawBackup) throws JSONException {
        if (context == null || TextUtils.isEmpty(rawBackup)) {
            throw new JSONException("Backup is empty");
        }
        JSONObject backup = new JSONObject(rawBackup);
        if (!FORMAT.equals(backup.optString("format")) || backup.optInt("version", -1) != VERSION) {
            throw new JSONException("Unsupported Crow Messenger backup");
        }

        String textSize = null;
        Boolean microphoneButton = null;
        List<String> restoredKeywords = null;
        if (backup.has("textSize")) {
            Object value = backup.get("textSize");
            if (!(value instanceof String) || !isSupportedTextSize((String) value)) {
                throw new JSONException("Invalid text size");
            }
            textSize = (String) value;
        }
        if (backup.has("microphoneButton")) {
            Object value = backup.get("microphoneButton");
            if (!(value instanceof Boolean)) {
                throw new JSONException("Invalid microphone button setting");
            }
            microphoneButton = (Boolean) value;
        }
        if (backup.has("spamKeywords")) {
            JSONArray keywords = backup.optJSONArray("spamKeywords");
            if (keywords == null || keywords.length() > 200) {
                throw new JSONException("Invalid spam keywords");
            }
            restoredKeywords = new ArrayList<>();
            for (int index = 0; index < keywords.length(); index++) {
                Object value = keywords.get(index);
                if (!(value instanceof String) || ((String) value).length() > 100) {
                    throw new JSONException("Invalid spam keyword");
                }
                if (!TextUtils.isEmpty((String) value)) {
                    restoredKeywords.add((String) value);
                }
            }
        }

        int restored = 0;
        if (textSize != null) {
            TextSizePrefs.setLabel(context, textSize);
            restored++;
        }
        if (microphoneButton != null) {
            ComposerPrefs.setVoiceButtonVisible(context, microphoneButton);
            restored++;
        }
        if (restoredKeywords != null) {
            SpamFilter.replaceCustomKeywords(context, restoredKeywords);
            restored++;
        }
        return restored;
    }

    private static boolean isSupportedTextSize(String label) {
        for (String supported : TextSizePrefs.labels()) {
            if (supported.equals(label)) {
                return true;
            }
        }
        return false;
    }
}
