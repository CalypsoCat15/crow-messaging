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

    static synchronized void save(Context context, List<Conversation> conversations) {
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
                .commit();
    }

    static synchronized List<Conversation> load(Context context) {
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

    static synchronized List<Conversation> upsertIncoming(
            Context context,
            String address,
            String body,
            long dateMillis
    ) {
        List<Conversation> rows = loadVisible(context);
        if (TextUtils.isEmpty(address)
                || TextUtils.isEmpty(body)
                || MessageNotifier.shouldSuppressIncoming(context, address, "", body)) {
            return rows;
        }
        TrashStore.restore(context, address);
        Conversation previous = null;
        for (int index = rows.size() - 1; index >= 0; index--) {
            Conversation row = rows.get(index);
            if (row == null || !AddressUtil.sameConversationAddress(row.address, address)) {
                continue;
            }
            if (previous == null || row.dateMillis > previous.dateMillis) {
                previous = row;
            }
            rows.remove(index);
        }
        long safeDate = dateMillis > 0L ? dateMillis : System.currentTimeMillis();
        if (previous != null
                && TextUtils.equals(previous.snippet, body)
                && previous.dateMillis == safeDate) {
            rows.add(previous);
        } else {
            Conversation details = previous != null ? previous : SmsStore.conversationForAddress(context, address);
            rows.add(new Conversation(
                    details == null ? "" : details.threadId,
                    address,
                    details == null ? address : details.name,
                    details == null ? "" : details.photoUri,
                    body,
                    safeDate,
                    previous == null
                            ? Math.max(1, details == null ? 0 : details.unreadCount)
                            : previous.unreadCount + 1
            ));
        }
        PinnedStore.sortConversations(context, rows);
        save(context, rows);
        return rows;
    }

    static synchronized void remove(Context context, String address) {
        List<Conversation> rows = load(context);
        rows.removeIf(conversation -> AddressUtil.sameConversationAddress(conversation.address, address));
        save(context, rows);
    }
}
