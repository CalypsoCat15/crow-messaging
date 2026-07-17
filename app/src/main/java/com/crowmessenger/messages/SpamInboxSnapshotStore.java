package com.crowmessenger.messages;

import android.content.Context;
import android.text.TextUtils;

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
        boolean hasFilteringRules = SpamFilter.hasMarkedSpam(context)
                || SpamFilter.hasCustomKeywords(context)
                || Blocklist.hasBlockedSenders(context);
        if (!hasFilteringRules) {
            return;
        }
        List<Conversation> savedRows = load(context);
        if (!savedRows.isEmpty() && loadVisible(context).size() == savedRows.size()) {
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
                || spamMatcher.isMarkedSpam(conversation.address, conversation.threadId)
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

    private static List<Conversation> load(Context context) {
        return ConversationSnapshotStore.load(context, PREFS, KEY_SNAPSHOT, VERSION, MAX_ROWS);
    }
}
