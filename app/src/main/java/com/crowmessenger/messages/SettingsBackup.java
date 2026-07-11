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

        int restored = 0;
        if (backup.has("textSize")) {
            TextSizePrefs.setLabel(context, backup.optString("textSize", TextSizePrefs.NORMAL));
            restored++;
        }
        if (backup.has("microphoneButton")) {
            ComposerPrefs.setVoiceButtonVisible(context, backup.optBoolean("microphoneButton", true));
            restored++;
        }
        if (backup.has("spamKeywords")) {
            JSONArray keywords = backup.optJSONArray("spamKeywords");
            List<String> restoredKeywords = new ArrayList<>();
            if (keywords != null) {
                for (int index = 0; index < Math.min(keywords.length(), 200); index++) {
                    String keyword = keywords.optString(index, "");
                    if (!TextUtils.isEmpty(keyword)) {
                        restoredKeywords.add(keyword);
                    }
                }
            }
            SpamFilter.replaceCustomKeywords(context, restoredKeywords);
            restored++;
        }
        return restored;
    }
}
