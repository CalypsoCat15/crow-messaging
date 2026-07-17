package com.crowmessenger.messages;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ConversationSnapshotStore {
    private ConversationSnapshotStore() {
    }

    @SuppressLint("ApplySharedPref")
    static void write(
            Context context,
            String preferencesName,
            String snapshotKey,
            int version,
            int maximumRows,
            List<Conversation> conversations,
            boolean durable
    ) {
        JSONArray rows = new JSONArray();
        if (conversations != null) {
            for (Conversation conversation : conversations) {
                if (conversation == null
                        || TextUtils.isEmpty(conversation.address)
                        || rows.length() >= maximumRows) {
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
            snapshot.put("version", version);
            snapshot.put("rows", rows);
        } catch (JSONException ignored) {
        }
        SharedPreferences.Editor editor = context
                .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .edit()
                .putString(snapshotKey, snapshot.toString());
        if (durable) {
            // Background receivers must finish only after the snapshot reaches disk.
            editor.commit();
        } else {
            editor.apply();
        }
    }

    static List<Conversation> load(
            Context context,
            String preferencesName,
            String snapshotKey,
            int version,
            int maximumRows
    ) {
        ArrayList<Conversation> conversations = new ArrayList<>();
        String raw = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString(snapshotKey, "");
        if (TextUtils.isEmpty(raw)) {
            return conversations;
        }
        try {
            JSONObject snapshot = new JSONObject(raw);
            if (snapshot.optInt("version") != version) {
                return conversations;
            }
            JSONArray rows = snapshot.optJSONArray("rows");
            if (rows == null) {
                return conversations;
            }
            for (int index = 0; index < rows.length() && conversations.size() < maximumRows; index++) {
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
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                    .edit()
                    .remove(snapshotKey)
                    .apply();
        }
        return conversations;
    }
}
