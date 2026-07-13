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

    private static void clearSuppressedConversationState(Context context, String address) {
        if (context != null && !TextUtils.isEmpty(address)) {
            MessageNotifier.clearIncomingForAddress(context, address);
            InboxSnapshotStore.remove(context, address);
        }
    }
}
