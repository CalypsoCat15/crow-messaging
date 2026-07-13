package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DraftStore {
    private static final String PREFS = "message_drafts";
    private static final String KEY_PREFIX = "draft:";
    private static final String DATE_SUFFIX = ":date";
    private static final String ADDRESS_SUFFIX = ":address";

    private DraftStore() {
    }

    static String draft(Context context, String address) {
        String key = key(address);
        if (TextUtils.isEmpty(key)) {
            return "";
        }
        SharedPreferences preferences = prefs(context);
        String legacyKey = matchingLegacyKey(preferences, address);
        String draft = preferences.getString(key, "");
        if (hasDraftText(draft)) {
            if (TextUtils.isEmpty(preferences.getString(addressKey(key), ""))) {
                preferences.edit()
                        .putString(addressKey(key), address)
                        .apply();
            }
            return draft;
        }
        String legacyDraft = preferences.getString(legacyKey, "");
        if (hasDraftText(legacyDraft)) {
            preferences.edit()
                    .putString(key, legacyDraft)
                    .putLong(dateKey(key), System.currentTimeMillis())
                    .putString(addressKey(key), address)
                    .remove(legacyKey)
                    .remove(dateKey(legacyKey))
                    .remove(addressKey(legacyKey))
                    .apply();
            return legacyDraft;
        }
        for (String matchingKey : matchingDraftBodyKeys(preferences, address)) {
            if (key.equals(matchingKey)) {
                continue;
            }
            String matchingDraft = preferences.getString(matchingKey, "");
            if (!hasDraftText(matchingDraft)) {
                continue;
            }
            long dateMillis = preferences.getLong(dateKey(matchingKey), System.currentTimeMillis());
            SharedPreferences.Editor editor = preferences.edit()
                    .putString(key, matchingDraft)
                    .putLong(dateKey(key), dateMillis)
                    .putString(addressKey(key), address);
            removeDraftKeys(editor, matchingKey, "");
            removeEquivalentDraftKeys(preferences, editor, address, key);
            editor.apply();
            return matchingDraft;
        }
        SharedPreferences.Editor cleanup = removeDraftKeys(preferences.edit(), key, legacyKey);
        removeEquivalentDraftKeys(preferences, cleanup, address, "");
        cleanup.apply();
        return "";
    }

    static List<Draft> drafts(Context context) {
        List<Draft> drafts = new ArrayList<>();
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor cleanup = null;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean legacy = isLegacyDraftBodyKey(preferences, key);
            if ((!isDraftBodyKey(key) && !legacy) || !(value instanceof String)) {
                continue;
            }
            String body = (String) value;
            if (!hasDraftText(body)) {
                if (cleanup == null) {
                    cleanup = preferences.edit();
                }
                cleanup.remove(key).remove(dateKey(key)).remove(addressKey(key));
                continue;
            }
            String dateKey = dateKey(key);
            String address = draftAddress(preferences, key);
            if (TextUtils.isEmpty(address)) {
                if (cleanup == null) {
                    cleanup = preferences.edit();
                }
                cleanup.remove(key).remove(dateKey).remove(addressKey(key));
                continue;
            }
            long dateMillis = preferences.getLong(dateKey, 0);
            if (dateMillis <= 0) {
                dateMillis = System.currentTimeMillis();
                if (cleanup == null) {
                    cleanup = preferences.edit();
                }
                cleanup.putLong(dateKey, dateMillis);
            }
            if (TextUtils.isEmpty(preferences.getString(addressKey(key), ""))) {
                if (cleanup == null) {
                    cleanup = preferences.edit();
                }
                cleanup.putString(addressKey(key), address);
            }
            if (legacy) {
                String modernKey = key(address);
                if (!TextUtils.isEmpty(modernKey)) {
                    if (cleanup == null) {
                        cleanup = preferences.edit();
                    }
                    if (hasDraftText(preferences.getString(modernKey, ""))) {
                        cleanup.remove(key)
                                .remove(dateKey(key))
                                .remove(addressKey(key));
                        continue;
                    }
                    cleanup.putString(modernKey, body)
                            .putLong(dateKey(modernKey), dateMillis)
                            .putString(addressKey(modernKey), address)
                            .remove(key)
                            .remove(dateKey(key))
                            .remove(addressKey(key));
                }
            }
            drafts.add(new Draft(
                    address,
                    body,
                    dateMillis
            ));
        }
        if (cleanup != null) {
            cleanup.apply();
        }
        return drafts;
    }

    static void save(Context context, String address, String body) {
        String key = key(address);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        if (!hasDraftText(body)) {
            clear(context, address);
            return;
        }
        String draft = body == null ? "" : body;
        SharedPreferences preferences = prefs(context);
        String legacyKey = matchingLegacyKey(preferences, address);
        SharedPreferences.Editor editor = preferences.edit()
                .putString(key, draft)
                .putLong(dateKey(key), System.currentTimeMillis())
                .putString(addressKey(key), address);
        removeLegacyDraftKeys(editor, legacyKey);
        removeEquivalentDraftKeys(preferences, editor, address, key);
        editor.apply();
    }

    static boolean clear(Context context, String address) {
        String key = key(address);
        if (TextUtils.isEmpty(key)) {
            return false;
        }
        SharedPreferences preferences = prefs(context);
        String legacyKey = matchingLegacyKey(preferences, address);
        boolean hadDraft = hasDraftText(preferences.getString(key, ""))
                || hasDraftText(preferences.getString(legacyKey, ""));
        for (String matchingKey : matchingDraftBodyKeys(preferences, address)) {
            hadDraft |= hasDraftText(preferences.getString(matchingKey, ""));
        }
        SharedPreferences.Editor editor = removeDraftKeys(preferences.edit(), key, legacyKey);
        removeEquivalentDraftKeys(preferences, editor, address, "");
        editor.apply();
        return hadDraft;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String matchingLegacyKey(SharedPreferences preferences, String address) {
        String candidate = legacyKey(address);
        if (TextUtils.isEmpty(candidate)) {
            return "";
        }
        String storedAddress = preferences.getString(addressKey(candidate), "");
        return TextUtils.isEmpty(storedAddress)
                || AddressUtil.sameConversationAddress(storedAddress, address)
                ? candidate
                : "";
    }

    private static void removeLegacyDraftKeys(SharedPreferences.Editor editor, String legacyKey) {
        if (!TextUtils.isEmpty(legacyKey)) {
            editor.remove(legacyKey)
                    .remove(dateKey(legacyKey))
                    .remove(addressKey(legacyKey));
        }
    }

    private static boolean hasDraftText(String body) {
        return !TextUtils.isEmpty(body) && !TextUtils.isEmpty(body.trim());
    }

    private static String key(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        return KEY_PREFIX + AddressUtil.stableKey(address);
    }

    private static String legacyKey(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        return String.valueOf(AddressUtil.stableId(address));
    }

    private static String dateKey(String key) {
        return key + DATE_SUFFIX;
    }

    private static String addressKey(String key) {
        return key + ADDRESS_SUFFIX;
    }

    private static SharedPreferences.Editor removeDraftKeys(SharedPreferences.Editor editor, String key, String legacyKey) {
        editor.remove(key)
                .remove(dateKey(key))
                .remove(addressKey(key));
        if (!TextUtils.isEmpty(legacyKey)) {
            editor.remove(legacyKey)
                    .remove(dateKey(legacyKey))
                    .remove(addressKey(legacyKey));
        }
        return editor;
    }

    private static void removeEquivalentDraftKeys(
            SharedPreferences preferences,
            SharedPreferences.Editor editor,
            String address,
            String keepKey
    ) {
        for (String matchingKey : matchingDraftBodyKeys(preferences, address)) {
            if (!matchingKey.equals(keepKey)) {
                removeDraftKeys(editor, matchingKey, "");
            }
        }
    }

    private static List<String> matchingDraftBodyKeys(SharedPreferences preferences, String address) {
        List<String> keys = new ArrayList<>();
        if (TextUtils.isEmpty(address)) {
            return keys;
        }
        for (String storedKey : preferences.getAll().keySet()) {
            if (!isDraftBodyKey(storedKey)) {
                continue;
            }
            String storedAddress = draftAddress(preferences, storedKey);
            if (AddressUtil.sameConversationAddress(address, storedAddress)) {
                keys.add(storedKey);
            }
        }
        return keys;
    }

    private static boolean isDraftBodyKey(String key) {
        return key != null
                && key.startsWith(KEY_PREFIX)
                && !key.endsWith(DATE_SUFFIX)
                && !key.endsWith(ADDRESS_SUFFIX);
    }

    private static boolean isLegacyDraftBodyKey(SharedPreferences preferences, String key) {
        return key != null
                && !key.startsWith(KEY_PREFIX)
                && !key.endsWith(DATE_SUFFIX)
                && !key.endsWith(ADDRESS_SUFFIX)
                && !TextUtils.isEmpty(preferences.getString(addressKey(key), ""));
    }

    private static String draftAddress(SharedPreferences preferences, String key) {
        String storedAddress = preferences.getString(addressKey(key), "");
        if (!TextUtils.isEmpty(storedAddress)) {
            return storedAddress;
        }
        if (isDraftBodyKey(key)) {
            return key.substring(KEY_PREFIX.length());
        }
        return "";
    }

    static final class Draft {
        final String address;
        final String body;
        final long dateMillis;

        Draft(String address, String body, long dateMillis) {
            this.address = address;
            this.body = body;
            this.dateMillis = dateMillis;
        }
    }
}
