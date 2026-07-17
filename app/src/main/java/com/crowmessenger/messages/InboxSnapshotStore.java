package com.crowmessenger.messages;

import android.content.Context;
import android.text.TextUtils;

import java.util.List;

final class InboxSnapshotStore {
    private static final String PREFS = "inbox_snapshot";
    private static final String KEY_SNAPSHOT = "rows";
    private static final int VERSION = 2;
    private static final int MAX_ROWS = 24;

    private InboxSnapshotStore() {
    }

    static synchronized void save(Context context, List<Conversation> conversations) {
        write(context, conversations, false);
    }

    private static void write(Context context, List<Conversation> conversations, boolean durable) {
        ConversationSnapshotStore.write(
                context,
                PREFS,
                KEY_SNAPSHOT,
                VERSION,
                MAX_ROWS,
                conversations,
                durable
        );
    }

    static synchronized List<Conversation> load(Context context) {
        return ConversationSnapshotStore.load(context, PREFS, KEY_SNAPSHOT, VERSION, MAX_ROWS);
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
        return upsertIncoming(context, address, body, dateMillis, false);
    }

    static synchronized List<Conversation> upsertIncomingDurably(
            Context context,
            String address,
            String body,
            long dateMillis
    ) {
        return upsertIncoming(context, address, body, dateMillis, true);
    }

    private static List<Conversation> upsertIncoming(
            Context context,
            String address,
            String body,
            long dateMillis,
            boolean durable
    ) {
        List<Conversation> rows = loadVisible(context);
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(body)) {
            return rows;
        }
        if (MessageNotifier.shouldSuppressIncoming(context, address, "", body)) {
            SpamInboxSnapshotStore.upsertIncoming(context, address, body, dateMillis, durable);
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
        write(context, rows, durable);
        return rows;
    }

    static synchronized void remove(Context context, String address) {
        List<Conversation> rows = load(context);
        rows.removeIf(conversation -> AddressUtil.sameConversationAddress(conversation.address, address));
        save(context, rows);
    }
}
