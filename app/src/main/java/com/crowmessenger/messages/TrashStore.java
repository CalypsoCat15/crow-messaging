package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TrashStore {
    private static final String PREFS = "trashed_conversations";
    private static final String KEY_PREFIX = "trash:";

    private TrashStore() {
    }

    static void moveToTrash(Context context, Conversation conversation) {
        if (context == null || conversation == null || TextUtils.isEmpty(conversation.address)) {
            return;
        }
        JSONObject value = new JSONObject();
        try {
            value.put("threadId", conversation.threadId);
            value.put("address", conversation.address);
            value.put("name", conversation.name);
            value.put("photoUri", conversation.photoUri);
            value.put("snippet", conversation.snippet);
            value.put("dateMillis", conversation.dateMillis);
            value.put("trashedAtMillis", System.currentTimeMillis());
        } catch (JSONException ignored) {
            return;
        }
        prefs(context).edit().putString(key(conversation.address), value.toString()).apply();
    }

    static boolean isTrashed(Context context, String address) {
        return find(context, address) != null;
    }

    static boolean restore(Context context, String address) {
        String storedKey = matchingKey(context, address);
        if (TextUtils.isEmpty(storedKey)) {
            return false;
        }
        prefs(context).edit().remove(storedKey).apply();
        return true;
    }

    static List<Item> all(Context context) {
        List<Item> items = new ArrayList<>();
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor cleanup = null;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (!entry.getKey().startsWith(KEY_PREFIX) || !(entry.getValue() instanceof String)) {
                continue;
            }
            Item item = parse((String) entry.getValue());
            if (item == null) {
                if (cleanup == null) {
                    cleanup = preferences.edit();
                }
                cleanup.remove(entry.getKey());
            } else {
                items.add(item);
            }
        }
        if (cleanup != null) {
            cleanup.apply();
        }
        items.sort((left, right) -> Long.compare(right.trashedAtMillis, left.trashedAtMillis));
        return items;
    }

    static void removeHidden(Context context, List<Conversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return;
        }
        for (int index = conversations.size() - 1; index >= 0; index--) {
            Conversation conversation = conversations.get(index);
            if (find(context, conversation.address) != null) {
                conversations.remove(index);
            }
        }
    }

    private static Item find(Context context, String address) {
        String storedKey = matchingKey(context, address);
        return TextUtils.isEmpty(storedKey) ? null : parse(prefs(context).getString(storedKey, ""));
    }

    private static String matchingKey(Context context, String address) {
        String exact = key(address);
        SharedPreferences preferences = prefs(context);
        if (!TextUtils.isEmpty(exact) && preferences.contains(exact)) {
            return exact;
        }
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                continue;
            }
            Item item = parse((String) entry.getValue());
            if (item != null && AddressUtil.sameConversationAddress(item.address, address)) {
                return entry.getKey();
            }
        }
        return "";
    }

    private static Item parse(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(value);
            String address = json.optString("address", "");
            if (TextUtils.isEmpty(address)) {
                return null;
            }
            return new Item(
                    json.optString("threadId", ""),
                    address,
                    json.optString("name", address),
                    json.optString("photoUri", ""),
                    json.optString("snippet", ""),
                    json.optLong("dateMillis", 0L),
                    json.optLong("trashedAtMillis", 0L)
            );
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String address) {
        return TextUtils.isEmpty(address) ? "" : KEY_PREFIX + AddressUtil.stableKey(address);
    }

    static final class Item {
        final String threadId;
        final String address;
        final String name;
        final String photoUri;
        final String snippet;
        final long dateMillis;
        final long trashedAtMillis;

        Item(String threadId, String address, String name, String photoUri, String snippet, long dateMillis, long trashedAtMillis) {
            this.threadId = threadId;
            this.address = address;
            this.name = name;
            this.photoUri = photoUri;
            this.snippet = snippet;
            this.dateMillis = dateMillis;
            this.trashedAtMillis = trashedAtMillis;
        }

        Conversation conversation() {
            return new Conversation(threadId, address, name, photoUri, snippet, dateMillis, 0);
        }
    }
}
