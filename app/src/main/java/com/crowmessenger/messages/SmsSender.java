package com.crowmessenger.messages;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.telephony.SmsManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class SmsSender {
    static final String ACTION_SMS_SENT = BuildConfig.APPLICATION_ID + ".SMS_SENT";
    static final String EXTRA_SEND_ID = "send_id";
    static final String EXTRA_PART_INDEX = "part_index";
    private static final String PREFS = "pending_sms_sends";
    private static final String KEY_PREFIX = "send_";
    private static final String KEY_ADDRESS_SUFFIX = "_address";
    private static final String KEY_BODY_SUFFIX = "_body";
    private static final String KEY_SENT_AT_SUFFIX = "_sent_at";
    private static final String KEY_CREATED_AT_SUFFIX = "_created_at";
    private static final String KEY_REMAINING_SUFFIX = "_remaining";
    private static final String KEY_PART_COUNT_SUFFIX = "_part_count";
    private static final String KEY_COMPLETED_PARTS_SUFFIX = "_completed_parts";
    private static final String KEY_FAILED_SUFFIX = "_failed";
    private static final String KEY_STATUS_SUFFIX = "_status";
    private static final String KEY_SCHEDULED_ID_SUFFIX = "_scheduled_id";
    private static final String REASON_UNCONFIRMED = "Text send status was not confirmed.";
    private static final String REASON_SEND_FAILED = "Text message could not be sent.";
    private static final String REASON_SENT_NOT_SAVED = "Text was sent, but Crow Messenger could not save it.";
    private static final long STALE_PENDING_SEND_MILLIS = 6L * 60L * 60L * 1000L;

    private SmsSender() {
    }

    static long sendAndRecord(Context context, String address, String body) throws SendException {
        return sendAndTrack(context, address, body, "");
    }

    static long sendScheduled(Context context, ScheduledMessageStore.ScheduledMessage message) throws SendException {
        if (message == null) {
            throw new SendException("Scheduled text could not be found.");
        }
        return sendAndTrack(context, message.address, message.body, message.id);
    }

    static boolean hasPendingScheduled(Context context, String scheduledId) {
        if (TextUtils.isEmpty(scheduledId)) {
            return false;
        }
        for (String sendId : pendingSendIds(context, KEY_SCHEDULED_ID_SUFFIX)) {
            PendingSend pending = pending(context, sendId);
            if (pending != null && scheduledId.equals(pending.scheduledId)) {
                return true;
            }
        }
        return false;
    }

    static ArrayList<ChatMessage> pendingMessagesForAddress(Context context, String address) {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        if (TextUtils.isEmpty(address)) {
            return messages;
        }
        for (String sendId : pendingSendIds(context, KEY_ADDRESS_SUFFIX)) {
            PendingSend pending = pending(context, sendId);
            if (pending == null
                    || !TextUtils.isEmpty(pending.scheduledId)
                    || !AddressUtil.sameConversationAddress(address, pending.address)) {
                continue;
            }
            if (TextUtils.isEmpty(pending.status)) {
                messages.add(ChatMessage.sending(pending.body, pending.sentAtMillis));
            } else {
                messages.add(ChatMessage.outgoingStatus(pending.body, pending.status, sendId, pending.sentAtMillis));
            }
        }
        messages.sort((left, right) -> Long.compare(left.dateMillis, right.dateMillis));
        return messages;
    }

    static ArrayList<Conversation> pendingConversations(Context context, String query) {
        return pendingConversations(context, query, SearchFilter.ALL);
    }

    static ArrayList<Conversation> pendingConversations(Context context, String query, SearchFilter filter) {
        ArrayList<Conversation> conversations = new ArrayList<>();
        for (String sendId : pendingSendIds(context, KEY_ADDRESS_SUFFIX)) {
            PendingSend pending = pending(context, sendId);
            if (pending == null
                    || !TextUtils.isEmpty(pending.scheduledId)
                    || !matchesPendingQuery(context, pending, query, filter)
                    || Blocklist.isBlocked(context, pending.address)
                    || SpamFilter.isMarkedSpam(context, pending.address)) {
                continue;
            }
            conversations.add(new Conversation(
                    "",
                    pending.address,
                    SmsStore.displayNameForAddress(context, pending.address),
                    SmsStore.photoUriForAddress(context, pending.address),
                    conversationSnippet(pending),
                    pending.sentAtMillis,
                    0
            ));
        }
        return conversations;
    }

    private static String conversationSnippet(PendingSend pending) {
        return TextUtils.isEmpty(pending.status) ? pending.body : pending.status + ": " + pending.body;
    }

    private static boolean matchesPendingQuery(Context context, PendingSend pending, String query, SearchFilter filter) {
        return (filter == null ? SearchFilter.ALL : filter).matches(
                query,
                pending.address,
                SmsStore.displayNameForAddress(context, pending.address),
                pending.body + " " + pending.status,
                false
        );
    }

    static void cleanupStalePendingSends(Context context) {
        long cutoff = System.currentTimeMillis() - STALE_PENDING_SEND_MILLIS;
        for (String sendId : pendingSendIds(context, KEY_ADDRESS_SUFFIX)) {
            PendingSend pending = pending(context, sendId);
            if (pending == null || pending.createdAtMillis >= cutoff || !TextUtils.isEmpty(pending.status)) {
                continue;
            }
            if (!TextUtils.isEmpty(pending.scheduledId)) {
                clearPending(context, sendId);
                if (markScheduledFailed(context, pending, REASON_UNCONFIRMED)) {
                    MessageUpdateBroadcaster.broadcast(context, pending.address);
                }
            } else {
                savePending(context, sendId, pending.withStatus(ChatMessage.STATUS_FAILED));
                MessageNotifier.showSendFailed(context, pending.address, REASON_UNCONFIRMED);
                MessageUpdateBroadcaster.broadcast(context, pending.address);
            }
        }
    }

    private static long sendAndTrack(Context context, String address, String body, String scheduledId) throws SendException {
        if (TextUtils.isEmpty(address)) {
            throw new SendException("No recipient was selected.");
        }
        if (LocalMmsStore.isGroupAddress(address)) {
            throw new SendException("Group sending is not available yet.");
        }
        if (TextUtils.isEmpty(body)) {
            throw new SendException("Message is empty.");
        }

        SmsManager smsManager = context.getSystemService(SmsManager.class);
        if (smsManager == null) {
            throw new SendException("Text messaging is not available on this phone right now.");
        }

        ArrayList<String> parts = preparedMessageParts(body, smsManager::divideMessage);

        long sentAt = System.currentTimeMillis();
        String sendId = UUID.randomUUID().toString();
        PendingSend pending = new PendingSend(
                address,
                body,
                sentAt,
                System.currentTimeMillis(),
                Math.max(1, parts.size()),
                Collections.emptySet(),
                false,
                "",
                scheduledId
        );
        if (!savePending(context, sendId, pending)) {
            throw new SendException("Text could not be prepared for tracking.");
        }
        ArrayList<PendingIntent> sentIntents = sentIntents(context, sendId, parts.size());
        try {
            smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, null);
        } catch (Exception ex) {
            clearPending(context, sendId);
            String detail = ex.getMessage();
            throw new SendException(
                    TextUtils.isEmpty(detail) ? "Android could not send the text." : detail,
                    ex
            );
        }
        return sentAt;
    }

    static ArrayList<String> preparedMessageParts(String body, MessageDivider divider) throws SendException {
        ArrayList<String> parts;
        try {
            parts = divider.divide(body);
        } catch (RuntimeException ex) {
            throw new SendException("Message could not be prepared for sending.", ex);
        }
        if (parts == null || parts.isEmpty()) {
            throw new SendException("Message could not be prepared for sending.");
        }
        return parts;
    }

    static synchronized void handleSentResult(Context context, Intent intent, int resultCode) {
        String sendId = intent == null ? "" : intent.getStringExtra(EXTRA_SEND_ID);
        PendingSend pending = pending(context, sendId);
        if (pending == null) {
            return;
        }
        if (!TextUtils.isEmpty(pending.status)) {
            return;
        }

        boolean failed = pending.failed || resultCode != Activity.RESULT_OK;
        Set<String> completedParts = new HashSet<>(pending.completedParts);
        int partIndex = intent == null ? -1 : intent.getIntExtra(EXTRA_PART_INDEX, -1);
        if (partIndex < 0 || partIndex >= pending.partCount || !completedParts.add(String.valueOf(partIndex))) {
            return;
        }
        if (failed) {
            PendingSend completed = pending.withProgress(completedParts, true);
            if (TextUtils.isEmpty(pending.scheduledId)) {
                savePending(context, sendId, completed.withStatus(ChatMessage.STATUS_FAILED));
            } else {
                clearPending(context, sendId);
            }
            notifyFailure(context, completed);
            MessageUpdateBroadcaster.broadcast(context, pending.address);
            return;
        }
        if (completedParts.size() < pending.partCount) {
            savePending(context, sendId, pending.withProgress(completedParts, failed));
            return;
        }

        PendingSend completed = pending.withProgress(completedParts, failed);
        if (SmsStore.saveSentSms(context, pending.address, pending.body, pending.sentAtMillis)) {
            clearPending(context, sendId);
            if (!TextUtils.isEmpty(pending.scheduledId)) {
                ScheduledMessageStore.delete(context, pending.scheduledId);
            }
        } else {
            PendingSend notSaved = completed.withStatus(ChatMessage.STATUS_NOT_SAVED).withoutScheduledId();
            savePending(context, sendId, notSaved);
            if (!TextUtils.isEmpty(pending.scheduledId)) {
                ScheduledMessageStore.delete(context, pending.scheduledId);
            }
            MessageNotifier.showSentNotSaved(context, pending.address, REASON_SENT_NOT_SAVED);
        }
        MessageUpdateBroadcaster.broadcast(context, pending.address);
    }

    private static void notifyFailure(Context context, PendingSend pending) {
        if (!TextUtils.isEmpty(pending.scheduledId)) {
            markScheduledFailed(context, pending, REASON_SEND_FAILED);
            return;
        }
        MessageNotifier.showSendFailed(context, pending.address, REASON_SEND_FAILED);
    }

    private static boolean markScheduledFailed(Context context, PendingSend pending, String reason) {
        ScheduledMessageStore.ScheduledMessage scheduled = ScheduledMessageStore.find(context, pending.scheduledId);
        if (scheduled == null || scheduled.failed()) {
            return false;
        }
        ScheduledMessageStore.markFailed(context, scheduled, reason);
        MessageNotifier.showScheduledFailed(context, pending.address, reason);
        return true;
    }

    private static ArrayList<PendingIntent> sentIntents(Context context, String sendId, int partCount) {
        ArrayList<PendingIntent> intents = new ArrayList<>();
        for (int i = 0; i < partCount; i++) {
            Intent intent = new Intent(context, SmsSentReceiver.class);
            intent.setAction(ACTION_SMS_SENT);
            intent.putExtra(EXTRA_SEND_ID, sendId);
            intent.putExtra(EXTRA_PART_INDEX, i);
            intent.setData(Uri.parse("sms-sent://" + Uri.encode(sendId) + "/" + i));
            intents.add(PendingIntent.getBroadcast(
                    context,
                    AddressUtil.stableId(sendId, String.valueOf(i)),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            ));
        }
        return intents;
    }

    private static boolean savePending(
            Context context,
            String sendId,
            PendingSend pending
    ) {
        if (TextUtils.isEmpty(sendId)) {
            return false;
        }
        return prefs(context).edit()
                .putString(key(sendId, KEY_ADDRESS_SUFFIX), pending.address)
                .putString(key(sendId, KEY_BODY_SUFFIX), pending.body)
                .putLong(key(sendId, KEY_SENT_AT_SUFFIX), pending.sentAtMillis)
                .putLong(key(sendId, KEY_CREATED_AT_SUFFIX), pending.createdAtMillis)
                .putInt(key(sendId, KEY_REMAINING_SUFFIX), Math.max(0, pending.partCount - pending.completedParts.size()))
                .putInt(key(sendId, KEY_PART_COUNT_SUFFIX), pending.partCount)
                .putStringSet(key(sendId, KEY_COMPLETED_PARTS_SUFFIX), new HashSet<>(pending.completedParts))
                .putBoolean(key(sendId, KEY_FAILED_SUFFIX), pending.failed)
                .putString(key(sendId, KEY_STATUS_SUFFIX), TextUtils.isEmpty(pending.status) ? "" : pending.status)
                .putString(key(sendId, KEY_SCHEDULED_ID_SUFFIX), TextUtils.isEmpty(pending.scheduledId) ? "" : pending.scheduledId)
                .commit();
    }

    private static PendingSend pending(Context context, String sendId) {
        if (TextUtils.isEmpty(sendId)) {
            return null;
        }
        SharedPreferences prefs = prefs(context);
        String address = prefs.getString(key(sendId, KEY_ADDRESS_SUFFIX), "");
        String body = prefs.getString(key(sendId, KEY_BODY_SUFFIX), "");
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(body)) {
            clearPending(context, sendId);
            return null;
        }
        int remainingParts = prefs.getInt(key(sendId, KEY_REMAINING_SUFFIX), 1);
        int partCount = prefs.getInt(key(sendId, KEY_PART_COUNT_SUFFIX), remainingParts);
        partCount = Math.max(1, partCount);
        Set<String> completedParts = validCompletedParts(
                prefs.getStringSet(key(sendId, KEY_COMPLETED_PARTS_SUFFIX), Collections.emptySet()),
                partCount
        );
        return new PendingSend(
                address,
                body,
                prefs.getLong(key(sendId, KEY_SENT_AT_SUFFIX), System.currentTimeMillis()),
                prefs.getLong(key(sendId, KEY_CREATED_AT_SUFFIX), System.currentTimeMillis()),
                partCount,
                completedParts,
                prefs.getBoolean(key(sendId, KEY_FAILED_SUFFIX), false),
                prefs.getString(key(sendId, KEY_STATUS_SUFFIX), ""),
                prefs.getString(key(sendId, KEY_SCHEDULED_ID_SUFFIX), "")
        );
    }

    private static void clearPending(Context context, String sendId) {
        if (TextUtils.isEmpty(sendId)) {
            return;
        }
        prefs(context).edit()
                .remove(key(sendId, KEY_ADDRESS_SUFFIX))
                .remove(key(sendId, KEY_BODY_SUFFIX))
                .remove(key(sendId, KEY_SENT_AT_SUFFIX))
                .remove(key(sendId, KEY_CREATED_AT_SUFFIX))
                .remove(key(sendId, KEY_REMAINING_SUFFIX))
                .remove(key(sendId, KEY_PART_COUNT_SUFFIX))
                .remove(key(sendId, KEY_COMPLETED_PARTS_SUFFIX))
                .remove(key(sendId, KEY_FAILED_SUFFIX))
                .remove(key(sendId, KEY_STATUS_SUFFIX))
                .remove(key(sendId, KEY_SCHEDULED_ID_SUFFIX))
                .apply();
    }

    static int deletePendingForAddress(Context context, String address) {
        if (TextUtils.isEmpty(address)) {
            return 0;
        }
        int deleted = 0;
        for (String sendId : pendingSendIds(context, KEY_ADDRESS_SUFFIX)) {
            PendingSend pending = pending(context, sendId);
            if (pending != null
                    && !TextUtils.isEmpty(pending.status)
                    && TextUtils.isEmpty(pending.scheduledId)
                    && AddressUtil.sameConversationAddress(address, pending.address)) {
                clearPending(context, sendId);
                deleted++;
            }
        }
        return deleted;
    }

    static boolean deletePendingById(Context context, String sendId) {
        PendingSend pending = pending(context, sendId);
        if (pending == null || TextUtils.isEmpty(pending.status) || !TextUtils.isEmpty(pending.scheduledId)) {
            return false;
        }
        clearPending(context, sendId);
        return true;
    }

    static RetryMessage failedMessageForRetry(Context context, String sendId) {
        PendingSend pending = pending(context, sendId);
        if (pending == null
                || !ChatMessage.STATUS_FAILED.equals(pending.status)
                || !TextUtils.isEmpty(pending.scheduledId)) {
            return null;
        }
        return new RetryMessage(pending.address, pending.body);
    }

    private static ArrayList<String> pendingSendIds(Context context, String suffix) {
        ArrayList<String> ids = new ArrayList<>();
        for (String key : prefs(context).getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX) && key.endsWith(suffix)) {
                ids.add(key.substring(KEY_PREFIX.length(), key.length() - suffix.length()));
            }
        }
        return ids;
    }

    private static Set<String> validCompletedParts(Set<String> storedParts, int partCount) {
        Set<String> valid = new HashSet<>();
        for (String part : storedParts) {
            try {
                int index = Integer.parseInt(part);
                if (index >= 0 && index < partCount) {
                    valid.add(part);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return valid;
    }

    private static String key(String sendId, String suffix) {
        return KEY_PREFIX + sendId + suffix;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static final class PendingSend {
        final String address;
        final String body;
        final long sentAtMillis;
        final long createdAtMillis;
        final int partCount;
        final Set<String> completedParts;
        final boolean failed;
        final String status;
        final String scheduledId;

        PendingSend(String address, String body, long sentAtMillis, long createdAtMillis, int partCount, Set<String> completedParts, boolean failed, String status, String scheduledId) {
            this.address = address;
            this.body = body;
            this.sentAtMillis = sentAtMillis;
            this.createdAtMillis = createdAtMillis;
            this.partCount = partCount;
            this.completedParts = new HashSet<>(completedParts);
            this.failed = failed;
            this.status = TextUtils.isEmpty(status) ? "" : status;
            this.scheduledId = TextUtils.isEmpty(scheduledId) ? "" : scheduledId;
        }

        PendingSend withProgress(Set<String> completedParts, boolean failed) {
            return new PendingSend(
                    address,
                    body,
                    sentAtMillis,
                    createdAtMillis,
                    partCount,
                    completedParts,
                    failed,
                    status,
                    scheduledId
            );
        }

        PendingSend withStatus(String status) {
            return new PendingSend(
                    address,
                    body,
                    sentAtMillis,
                    createdAtMillis,
                    partCount,
                    completedParts,
                    failed || !TextUtils.isEmpty(status),
                    status,
                    scheduledId
            );
        }

        PendingSend withoutScheduledId() {
            return new PendingSend(
                    address,
                    body,
                    sentAtMillis,
                    createdAtMillis,
                    partCount,
                    completedParts,
                    failed,
                    status,
                    ""
            );
        }
    }

    static final class SendException extends Exception {
        SendException(String message) {
            super(message);
        }

        SendException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class RetryMessage {
        final String address;
        final String body;

        RetryMessage(String address, String body) {
            this.address = address;
            this.body = body;
        }
    }

    interface MessageDivider {
        ArrayList<String> divide(String body);
    }
}
