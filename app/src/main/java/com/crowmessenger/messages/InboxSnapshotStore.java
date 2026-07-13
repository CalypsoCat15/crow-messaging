package com.crowmessenger.messages;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class InboxSnapshotStore {
    private static final String PREFS = "inbox_snapshot";
    private static final String KEY_SNAPSHOT = "rows";
    private static final int VERSION = 2;
    private static final int MAX_ROWS = 24;

    private InboxSnapshotStore() {
    }

    static void save(Context context, List<Conversation> conversations) {
        JSONArray rows = new JSONArray();
        if (conversations != null) {
            for (Conversation conversation : conversations) {
                if (conversation == null || TextUtils.isEmpty(conversation.address) || rows.length() >= MAX_ROWS) {
                    continue;
                }
                JSONObject row = new JSONObject();
                try {
                    row.put("threadId", conversation.threadId);
                    row.put("address", conversation.address);
                    row.put("name", conversation.name);
                    row.put("photoUri", conversation.photoUri);
                    row.put("snippet", conversation.snippet);
                    row.put("dateMillis", conversation.dateMillis);
                    row.put("unreadCount", conversation.unreadCount);
                    rows.put(row);
                } catch (JSONException ignored) {
                }
            }
        }
        JSONObject snapshot = new JSONObject();
        try {
            snapshot.put("version", VERSION);
            snapshot.put("rows", rows);
        } catch (JSONException ignored) {
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SNAPSHOT, snapshot.toString())
                .apply();
    }

    static List<Conversation> load(Context context) {
        ArrayList<Conversation> conversations = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SNAPSHOT, "");
        if (TextUtils.isEmpty(raw)) {
            return conversations;
        }
        try {
            JSONObject snapshot = new JSONObject(raw);
            if (snapshot.optInt("version") != VERSION) {
                return conversations;
            }
            JSONArray rows = snapshot.optJSONArray("rows");
            if (rows == null) {
                return conversations;
            }
            for (int index = 0; index < rows.length() && conversations.size() < MAX_ROWS; index++) {
                JSONObject row = rows.optJSONObject(index);
                if (row == null || TextUtils.isEmpty(row.optString("address"))) {
                    continue;
                }
                conversations.add(new Conversation(
                        row.optString("threadId"),
                        row.optString("address"),
                        row.optString("name"),
                        row.optString("photoUri"),
                        row.optString("snippet"),
                        row.optLong("dateMillis"),
                        Math.max(0, row.optInt("unreadCount"))
                ));
            }
        } catch (JSONException ignored) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_SNAPSHOT)
                    .apply();
        }
        return conversations;
    }

    static List<Conversation> loadVisible(Context context) {
        List<Conversation> conversations = load(context);
        conversations.removeIf(conversation -> Blocklist.isBlocked(context, conversation.address)
                || SpamFilter.isMarkedSpam(context, conversation.address));
        TrashStore.removeHidden(context, conversations);
        return conversations;
    }

    static void remove(Context context, String address) {
        List<Conversation> rows = load(context);
        rows.removeIf(conversation -> AddressUtil.sameConversationAddress(conversation.address, address));
        save(context, rows);
    }
}
