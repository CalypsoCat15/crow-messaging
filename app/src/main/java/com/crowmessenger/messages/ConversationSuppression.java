package com.crowmessenger.messages;

import android.content.Context;
import android.text.TextUtils;

final class ConversationSuppression {
    private ConversationSuppression() {
    }

    static void block(Context context, String address) {
        Blocklist.block(context, address);
        if (Blocklist.isBlocked(context, address)) {
            clearSuppressedConversationState(context, address);
        }
    }

    static void markSpam(Context context, String address, String threadId) {
        SpamFilter.markSpam(context, address, threadId);
        if (SpamFilter.isMarkedSpam(context, address)) {
            clearSuppressedConversationState(context, address);
        }
    }

    static void moveToTrash(Context context, Conversation conversation) {
        TrashStore.moveToTrash(context, conversation);
        if (conversation != null && TrashStore.isTrashed(context, conversation.address)) {
            clearSuppressedConversationState(context, conversation.address);
        }
    }

    static String spamReason(Context context, Conversation conversation) {
        if (context == null) {
            return "Filtered message";
        }
        return spamReasonMatcher(context).reason(conversation);
    }

    static SpamReasonMatcher spamReasonMatcher(Context context) {
        return new SpamReasonMatcher(context);
    }

    static final class SpamReasonMatcher {
        private final SpamFilter.Matcher spamMatcher;
        private final Blocklist.Matcher blockMatcher;

        private SpamReasonMatcher(Context context) {
            this.spamMatcher = SpamFilter.matcher(context);
            this.blockMatcher = Blocklist.matcher(context);
        }

        String reason(Conversation conversation) {
            if (conversation == null) {
                return "Filtered message";
            }
            if (spamMatcher.isMarkedSpam(conversation.address, conversation.threadId)) {
                return "You marked this as spam";
            }
            if (blockMatcher.isBlocked(conversation.address)) {
                return "Blocked sender";
            }
            String keyword = spamMatcher.matchingKeywordForUnknownSender(
                    conversation.address,
                    conversation.snippet
            );
            return TextUtils.isEmpty(keyword) ? "Filtered message" : "Matched rule: " + keyword;
        }
    }

    private static void clearSuppressedConversationState(Context context, String address) {
        if (context != null && !TextUtils.isEmpty(address)) {
            MessageNotifier.clearIncomingForAddress(context, address);
            InboxSnapshotStore.remove(context, address);
        }
    }
}
