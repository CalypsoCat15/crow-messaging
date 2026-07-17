package com.crowmessenger.messages;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SpamInboxSnapshotStore {
    private static final String PREFS = "spam_inbox_snapshot";
    private static final String KEY_SNAPSHOT = "rows";
    private static final int VERSION = 1;
    private static final int MAX_ROWS = 100;

    private SpamInboxSnapshotStore() {
    }

    static synchronized void save(Context context, List<Conversation> conversations) {
        write(context, conversations, false);
    }

    static synchronized void seedIfNeeded(Context context) {
        if (!load(context).isEmpty()
                || (!SpamFilter.hasMarkedSpam(context) && !Blocklist.hasBlockedSenders(context))) {
            return;
        }
        write(context, SmsStore.loadConversations(context, true, ""), true);
    }

    static synchronized void upsert(Context context, Conversation conversation) {
        if (conversation == null || TextUtils.isEmpty(conversation.address)) {
            return;
        }
        List<Conversation> rows = load(context);
        removeMatching(rows, conversation.address);
        rows.add(conversation);
        sortNewestFirst(rows);
        write(context, rows, true);
    }

    static synchronized void upsertIncoming(
            Context context,
            String address,
            String body,
            long dateMillis,
            boolean durable
    ) {
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(body)) {
            return;
        }
        List<Conversation> rows = load(context);
        Conversation previous = removeMatching(rows, address);
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
                    previous == null ? 1 : previous.unreadCount + 1
            ));
        }
        sortNewestFirst(rows);
        write(context, rows, durable);
    }

    static synchronized void remove(Context context, String address) {
        List<Conversation> rows = load(context);
        if (removeMatching(rows, address) != null) {
            write(context, rows, true);
        }
    }

    static synchronized List<Conversation> loadVisible(Context context) {
        List<Conversation> rows = load(context);
        Blocklist.Matcher blockMatcher = Blocklist.matcher(context);
        SpamFilter.Matcher spamMatcher = SpamFilter.matcher(context);
        rows.removeIf(conversation -> !isSuppressed(conversation, blockMatcher, spamMatcher));
        TrashStore.removeHidden(context, rows);
        return rows;
    }

    private static boolean isSuppressed(
            Conversation conversation,
            Blocklist.Matcher blockMatcher,
            SpamFilter.Matcher spamMatcher
    ) {
        return conversation != null
                && (blockMatcher.isBlocked(conversation.address)
                || spamMatcher.isMarkedSpam(conversation.address)
                || spamMatcher.matchesKeywordForUnknownSender(conversation.address, conversation.snippet));
    }

    private static Conversation removeMatching(List<Conversation> rows, String address) {
        Conversation newest = null;
        for (int index = rows.size() - 1; index >= 0; index--) {
            Conversation row = rows.get(index);
            if (row == null || !AddressUtil.sameConversationAddress(row.address, address)) {
                continue;
            }
            if (newest == null || row.dateMillis > newest.dateMillis) {
                newest = row;
            }
            rows.remove(index);
        }
        return newest;
    }

    private static void sortNewestFirst(List<Conversation> rows) {
        rows.sort((left, right) -> Long.compare(right.dateMillis, left.dateMillis));
    }

    @SuppressLint("ApplySharedPref")
    private static void write(Context context, List<Conversation> conversations, boolean durable) {
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
        android.content.SharedPreferences.Editor editor = context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SNAPSHOT, snapshot.toString());
        if (durable) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    private static List<Conversation> load(Context context) {
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
}
