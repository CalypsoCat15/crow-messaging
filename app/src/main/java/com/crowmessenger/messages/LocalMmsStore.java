package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LocalMmsStore {
    static final String PICTURE_MESSAGE = "Picture message";
    static final String DOWNLOAD_FAILED_MESSAGE = "Picture message could not be downloaded.";
    static final String DISPLAY_FAILED_MESSAGE = "Picture message could not be displayed.";
    static final String UNREADABLE_MESSAGE = "Message received, but Crow Messenger could not read it yet.";
    private static final String LEGACY_DISPLAY_FAILED_MESSAGE = "Picture message received, but Crow Messenger could not display it yet.";

    private static final String PREFS = "local_mms";
    private static final String KEY_IDS = "ids";
    private static final String GROUP_PREFIX = "group:";
    private static final String PENDING_ADDRESS_PREFIX = "pending_address_";
    private static final String PENDING_PDU_PREFIX = "pending_pdu_";
    private static final String PENDING_CREATED_PREFIX = "pending_created_";
    private static final String PENDING_URL_PREFIX = "pending_url_";
    private static final String PENDING_SUBSCRIPTION_PREFIX = "pending_subscription_";
    private static final String PENDING_RETRY_PREFIX = "pending_retry_";
    private static final String ADDRESS_PREFIX = "address_";
    private static final String SENDER_PREFIX = "sender_";
    private static final String BODY_PREFIX = "body_";
    private static final String IMAGE_PREFIX = "image_";
    private static final String DATE_PREFIX = "date_";
    private static final String READ_PREFIX = "read_";
    private static final String OUTGOING_PREFIX = "outgoing_";
    private static final String STATUS_PREFIX = "status_";

    private LocalMmsStore() {
    }

    static boolean savePending(Context context, String id, String address, String pduPath) {
        return savePending(context, id, address, pduPath, "", -1);
    }

    static boolean savePending(Context context, String id, String address, String pduPath, String downloadUrl, int subscriptionId) {
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(pduPath)) {
            return false;
        }
        return prefs(context).edit()
                .putString(pendingAddressKey(id), address)
                .putString(pendingPduKey(id), pduPath)
                .putLong(pendingCreatedKey(id), System.currentTimeMillis())
                .putString(pendingUrlKey(id), TextUtils.isEmpty(downloadUrl) ? "" : downloadUrl)
                .putInt(pendingSubscriptionKey(id), subscriptionId)
                .putInt(pendingRetryKey(id), 0)
                .commit();
    }

    static Pending pending(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return new Pending("", "");
        }
        SharedPreferences prefs = prefs(context);
        return new Pending(
                prefs.getString(pendingAddressKey(id), ""),
                prefs.getString(pendingPduKey(id), ""),
                prefs.getLong(pendingCreatedKey(id), 0L),
                prefs.getString(pendingUrlKey(id), ""),
                prefs.getInt(pendingSubscriptionKey(id), -1),
                prefs.getInt(pendingRetryKey(id), 0)
        );
    }

    static int incrementPendingRetry(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return 0;
        }
        SharedPreferences prefs = prefs(context);
        if (TextUtils.isEmpty(prefs.getString(pendingPduKey(id), ""))) {
            return 0;
        }
        int nextRetryCount = prefs.getInt(pendingRetryKey(id), 0) + 1;
        prefs.edit()
                .putInt(pendingRetryKey(id), nextRetryCount)
                .apply();
        return nextRetryCount;
    }

    static void clearPending(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return;
        }
        prefs(context).edit()
                .remove(pendingAddressKey(id))
                .remove(pendingPduKey(id))
                .remove(pendingCreatedKey(id))
                .remove(pendingUrlKey(id))
                .remove(pendingSubscriptionKey(id))
                .remove(pendingRetryKey(id))
                .apply();
    }

    static List<String> pendingIds(Context context) {
        List<String> ids = new ArrayList<>();
        for (String key : prefs(context).getAll().keySet()) {
            if (key.startsWith(PENDING_PDU_PREFIX)) {
                String id = key.substring(PENDING_PDU_PREFIX.length());
                if (!TextUtils.isEmpty(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    static int clearPendingForAddress(Context context, String address) {
        if (TextUtils.isEmpty(address)) {
            return 0;
        }
        int cleared = 0;
        for (String id : pendingIds(context)) {
            Pending pending = pending(context, id);
            if (!AddressUtil.sameConversationAddress(address, pending.address)) {
                continue;
            }
            MmsFiles.deleteAppFile(context, MmsFiles.DOWNLOADS_DIR, pending.pduPath);
            clearPending(context, id);
            cleared++;
        }
        return cleared;
    }

    static void saveImage(Context context, String address, String senderAddress, String body, String imageUri, long dateMillis) {
        saveMessage(context, address, senderAddress, body, imageUri, dateMillis, false);
    }

    static void saveSentImage(Context context, String address, String body, String imageUri, long dateMillis) {
        saveMessage(context, address, "", body, imageUri, dateMillis, true);
    }

    static boolean saveSentImage(Context context, String id, String address, String body, String imageUri, long dateMillis) {
        return saveMessage(context, id, address, "", body, imageUri, dateMillis, true, true);
    }

    static boolean saveSentText(Context context, String id, String address, String body, long dateMillis) {
        return saveMessage(context, id, address, "", body, "", dateMillis, true, true);
    }

    static synchronized boolean markSentMessageFailed(Context context, String id, String address) {
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(address)) {
            return false;
        }
        String normalizedAddress = normalizedConversationAddress(address);
        SharedPreferences prefs = prefs(context);
        if (!savedIds(prefs).contains(id)
                || !isOutgoing(prefs, id)
                || !AddressUtil.sameConversationAddress(normalizedAddress, prefs.getString(addressKey(id), ""))) {
            return false;
        }
        return prefs.edit().putString(statusKey(id), ChatMessage.STATUS_FAILED).commit();
    }

    static synchronized boolean markSentImageFailed(Context context, String address, String imageUri) {
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(imageUri)) {
            return false;
        }
        SharedPreferences prefs = prefs(context);
        for (String id : savedIds(prefs)) {
            if (!isOutgoing(prefs, id)
                    || !AddressUtil.sameConversationAddress(address, prefs.getString(addressKey(id), ""))
                    || !TextUtils.equals(imageUri, prefs.getString(imageKey(id), ""))) {
                continue;
            }
            prefs.edit().putString(statusKey(id), ChatMessage.STATUS_FAILED).apply();
            return true;
        }
        return false;
    }

    static RetryMessage failedMessageForRetry(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        SharedPreferences prefs = prefs(context);
        if (!savedIds(prefs).contains(id)
                || !isOutgoing(prefs, id)
                || !ChatMessage.STATUS_FAILED.equals(prefs.getString(statusKey(id), ""))) {
            return null;
        }
        String address = prefs.getString(addressKey(id), "");
        String body = prefs.getString(bodyKey(id), "");
        String imageUri = prefs.getString(imageKey(id), "");
        if (TextUtils.isEmpty(address) || (TextUtils.isEmpty(body) && TextUtils.isEmpty(imageUri))) {
            return null;
        }
        return new RetryMessage(address, body, imageUri);
    }

    static synchronized boolean deleteFailedMessageById(Context context, String id) {
        RetryMessage retry = failedMessageForRetry(context, id);
        if (retry == null) {
            return false;
        }
        SharedPreferences prefs = prefs(context);
        Set<String> keptIds = savedIds(prefs);
        if (!keptIds.remove(id)) {
            return false;
        }
        SharedPreferences.Editor editor = prefs.edit();
        removeMessage(editor, id);
        editor.putStringSet(KEY_IDS, keptIds);
        if (!editor.commit()) {
            return false;
        }
        deleteStoredImage(context, retry.imageUri);
        return true;
    }

    static synchronized boolean deleteMessageById(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return false;
        }
        SharedPreferences prefs = prefs(context);
        Set<String> keptIds = savedIds(prefs);
        if (!keptIds.remove(id)) {
            return false;
        }
        deleteStoredImage(context, prefs.getString(imageKey(id), ""));
        SharedPreferences.Editor editor = prefs.edit();
        removeMessage(editor, id);
        editor.putStringSet(KEY_IDS, keptIds).apply();
        return true;
    }

    static synchronized boolean rollbackSentMessage(Context context, String id, String address) {
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(address)) {
            return false;
        }
        String normalizedAddress = normalizedConversationAddress(address);
        SharedPreferences prefs = prefs(context);
        Set<String> keptIds = savedIds(prefs);
        if (!keptIds.contains(id)
                || !isOutgoing(prefs, id)
                || !AddressUtil.sameConversationAddress(normalizedAddress, prefs.getString(addressKey(id), ""))) {
            return false;
        }
        String imageUri = prefs.getString(imageKey(id), "");
        keptIds.remove(id);
        SharedPreferences.Editor editor = prefs.edit();
        removeMessage(editor, id);
        if (!editor.putStringSet(KEY_IDS, keptIds).commit()) {
            return false;
        }
        deleteStoredImage(context, imageUri);
        return true;
    }

    static void saveNotice(Context context, String address, String body, long dateMillis) {
        saveMessage(context, address, body, "", dateMillis, false);
    }

    static void saveNotice(Context context, String address, String senderAddress, String body, long dateMillis) {
        saveMessage(context, address, senderAddress, body, "", dateMillis, false);
    }

    static synchronized boolean replaceClosestUnreadableMessage(
            Context context,
            String address,
            String senderAddress,
            String body,
            String imageUri,
            long dateHintMillis
    ) {
        if (TextUtils.isEmpty(body) && TextUtils.isEmpty(imageUri)) {
            return false;
        }
        SharedPreferences prefs = prefs(context);
        String cleanSender = normalizedParticipantAddress(senderAddress);
        String bestId = "";
        long bestDistance = Long.MAX_VALUE;
        for (String id : savedIds(prefs)) {
            if (isOutgoing(prefs, id)
                    || !UNREADABLE_MESSAGE.equals(prefs.getString(bodyKey(id), ""))) {
                continue;
            }
            String savedAddress = prefs.getString(addressKey(id), "");
            String savedSender = normalizedParticipantAddress(prefs.getString(senderKey(id), ""));
            boolean sameConversation = AddressUtil.sameConversationAddress(savedAddress, address);
            boolean sameSender = !TextUtils.isEmpty(cleanSender) && TextUtils.equals(savedSender, cleanSender);
            if (!sameConversation && !sameSender) {
                continue;
            }
            long savedDate = prefs.getLong(dateKey(id), 0L);
            long distance = dateHintMillis <= 0 || savedDate <= 0
                    ? 0L
                    : Math.abs(savedDate - dateHintMillis);
            if (distance > 5L * 60L * 1000L || distance >= bestDistance) {
                continue;
            }
            bestId = id;
            bestDistance = distance;
        }
        if (TextUtils.isEmpty(bestId)) {
            return false;
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(bodyKey(bestId), TextUtils.isEmpty(body) ? PICTURE_MESSAGE : body);
        if (!TextUtils.isEmpty(cleanSender)) {
            editor.putString(senderKey(bestId), cleanSender);
        }
        if (!TextUtils.isEmpty(imageUri)) {
            editor.putString(imageKey(bestId), imageUri);
        }
        return editor.commit();
    }

    static synchronized void cleanupAttachmentNameMessages(Context context) {
        SharedPreferences prefs = prefs(context);
        Set<String> ids = savedIds(prefs);
        Set<String> keptIds = new HashSet<>(ids);
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (String id : ids) {
            String address = prefs.getString(addressKey(id), "");
            String body = prefs.getString(bodyKey(id), "");
            String imageUri = prefs.getString(imageKey(id), "");
            if (TextUtils.isEmpty(address) || (TextUtils.isEmpty(body) && TextUtils.isEmpty(imageUri))) {
                keptIds.remove(id);
                deleteStoredImage(context, imageUri);
                removeMessage(editor, id);
                changed = true;
                continue;
            }
            String cleanedAddress = cleanedGroupAddress(address);
            cleanedAddress = preferredStoredGroupAddress(prefs, ids, cleanedAddress);
            if (!TextUtils.equals(address, cleanedAddress)) {
                editor.putString(addressKey(id), cleanedAddress);
                changed = true;
            }
            String sender = prefs.getString(senderKey(id), "");
            if (!TextUtils.isEmpty(sender) && TextUtils.isEmpty(normalizedParticipantAddress(sender))) {
                editor.remove(senderKey(id));
                changed = true;
            }
            if (isOutgoing(prefs, id)) {
                continue;
            }
            if (!looksLikeUnreadableStoredBody(body)) {
                continue;
            }
            if (TextUtils.isEmpty(imageUri)) {
                keptIds.remove(id);
                removeMessage(editor, id);
            } else {
                editor.putString(bodyKey(id), PICTURE_MESSAGE);
            }
            changed = true;
        }
        for (String id : ids) {
            if (!keptIds.contains(id)) {
                continue;
            }
            if (isOutgoing(prefs, id)) {
                continue;
            }
            String body = prefs.getString(bodyKey(id), "");
            String imageUri = prefs.getString(imageKey(id), "");
            if (!TextUtils.isEmpty(imageUri) && looksLikeUnreadableImageCaption(body)) {
                String recoveredText = recoveredArchivedDownloadText(context, imageUri);
                editor.putString(bodyKey(id), TextUtils.isEmpty(recoveredText) ? PICTURE_MESSAGE : recoveredText);
                changed = true;
            }
            if (TextUtils.isEmpty(imageUri) && LEGACY_DISPLAY_FAILED_MESSAGE.equals(body)) {
                editor.putString(bodyKey(id), UNREADABLE_MESSAGE);
                changed = true;
            }
            if (looksLikeLegacyUnreadableNotice(body)) {
                editor.putString(bodyKey(id), UNREADABLE_MESSAGE);
                changed = true;
            }
        }
        if (changed) {
            editor.putStringSet(KEY_IDS, keptIds).apply();
        }
    }

    private static boolean looksLikeLegacyUnreadableNotice(String body) {
        return !TextUtils.isEmpty(body)
                && body.startsWith("Message received, but ")
                && body.endsWith(" Messages could not read it yet.");
    }

    static String conversationAddress(String fallbackAddress, List<String> participants) {
        List<String> keys = participantKeys(participants);
        if (keys.size() < 3) {
            return fallbackAddress;
        }
        return GROUP_PREFIX + TextUtils.join("|", keys);
    }

    static String outgoingGroupAddress(List<String> recipients) {
        List<String> keys = participantKeys(recipients);
        if (keys.size() < 2) {
            return "";
        }
        return GROUP_PREFIX + TextUtils.join("|", keys);
    }

    static String outgoingGroupAddress(Context context, List<String> recipients) {
        String candidate = outgoingGroupAddress(recipients);
        if (!isGroupAddress(candidate)) {
            return candidate;
        }
        String existing = bestExistingOutgoingGroupMatch(context, groupParticipants(candidate));
        return TextUtils.isEmpty(existing) ? candidate : existing;
    }

    private static String cleanedGroupAddress(String address) {
        if (!isGroupAddress(address)) {
            return address;
        }
        String cleaned = conversationAddress("", groupParticipants(address));
        return isGroupAddress(cleaned) ? cleaned : address;
    }

    static String bestConversationAddress(Context context, String fallbackAddress, List<String> participants) {
        String candidate = conversationAddress(fallbackAddress, participants);
        if (!isGroupAddress(candidate)) {
            return candidate;
        }

        List<String> candidateKeys = groupParticipants(candidate);
        String existing = bestExistingGroupMatch(context, candidateKeys);
        return TextUtils.isEmpty(existing) ? candidate : existing;
    }

    static boolean isGroupAddress(String address) {
        return !TextUtils.isEmpty(address) && address.startsWith(GROUP_PREFIX);
    }

    static String displayNameForAddress(Context context, String address) {
        if (!isGroupAddress(address)) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (String participant : groupParticipants(address)) {
            String name = SmsStore.displayNameForAddress(context, participant);
            if (!TextUtils.isEmpty(name)) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return "Group message";
        }
        if (names.size() <= 3) {
            return TextUtils.join(", ", names);
        }
        return names.get(0) + ", " + names.get(1) + " +" + (names.size() - 2);
    }

    static List<String> participantsForAddress(String address) {
        return new ArrayList<>(groupParticipants(address));
    }

    static String displayNameForParticipant(Context context, String participant) {
        if (TextUtils.isEmpty(normalizedParticipantAddress(participant))) {
            return "";
        }
        String name = SmsStore.displayNameForAddress(context, participant);
        return TextUtils.isEmpty(name) ? participant : name;
    }

    static String normalizedParticipantAddress(String participant) {
        return participantKey(participant);
    }

    private static void saveMessage(Context context, String address, String body, String imageUri, long dateMillis, boolean outgoing) {
        saveMessage(context, address, "", body, imageUri, dateMillis, outgoing);
    }

    private static synchronized void saveMessage(Context context, String address, String senderAddress, String body, String imageUri, long dateMillis, boolean outgoing) {
        saveMessage(context, "", address, senderAddress, body, imageUri, dateMillis, outgoing, false);
    }

    private static synchronized boolean saveMessage(
            Context context,
            String requestedId,
            String address,
            String senderAddress,
            String body,
            String imageUri,
            long dateMillis,
            boolean outgoing,
            boolean durable
    ) {
        String cleanAddress = normalizedConversationAddress(address);
        String cleanSenderAddress = normalizedParticipantAddress(senderAddress);
        String cleanBody = TextUtils.isEmpty(body) ? "" : body.trim();
        String cleanImageUri = TextUtils.isEmpty(imageUri) ? "" : imageUri;
        if (!TextUtils.isEmpty(cleanImageUri) && !PICTURE_MESSAGE.equals(cleanBody)) {
            cleanBody = MmsPduUtil.cleanDisplayText(cleanBody);
        }
        if (TextUtils.isEmpty(cleanBody) && !TextUtils.isEmpty(cleanImageUri)) {
            cleanBody = PICTURE_MESSAGE;
        }
        if (TextUtils.isEmpty(cleanAddress) || (TextUtils.isEmpty(cleanBody) && TextUtils.isEmpty(cleanImageUri))) {
            return false;
        }
        String id = TextUtils.isEmpty(requestedId) ? dateMillis + "-" + System.nanoTime() : requestedId;
        SharedPreferences prefs = prefs(context);
        Set<String> ids = savedIds(prefs);
        ids.add(id);
        SharedPreferences.Editor editor = prefs.edit()
                .putStringSet(KEY_IDS, ids)
                .putString(addressKey(id), cleanAddress)
                .putString(senderKey(id), cleanSenderAddress)
                .putString(bodyKey(id), cleanBody)
                .putString(imageKey(id), cleanImageUri)
                .putLong(dateKey(id), dateMillis)
                .putBoolean(readKey(id), outgoing)
                .putBoolean(outgoingKey(id), outgoing);
        if (durable) {
            return editor.commit();
        }
        editor.apply();
        return true;
    }

    private static String normalizedConversationAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        if (isGroupAddress(address)) {
            return cleanedGroupAddress(address);
        }
        String normalized = normalizedParticipantAddress(address);
        return TextUtils.isEmpty(normalized) ? address : normalized;
    }

    static List<ChatMessage> loadForAddress(Context context, String address) {
        return loadForAddress(context, address, false);
    }

    static List<ChatMessage> loadForAddress(Context context, String address, boolean blockedOnly) {
        List<ChatMessage> messages = new ArrayList<>();
        SharedPreferences prefs = prefs(context);
        for (String id : savedIds(prefs)) {
            String savedAddress = prefs.getString(addressKey(id), "");
            if (!AddressUtil.sameConversationAddress(address, savedAddress)) {
                continue;
            }
            String body = prefs.getString(bodyKey(id), PICTURE_MESSAGE);
            String sender = prefs.getString(senderKey(id), "");
            boolean outgoing = isOutgoing(prefs, id);
            if (isFiltered(context, savedAddress, sender, body, outgoing) != blockedOnly) {
                continue;
            }
            messages.add(ChatMessage.storedLocalMms(
                    body,
                    prefs.getString(imageKey(id), ""),
                    sender,
                    prefs.getString(statusKey(id), ""),
                    id,
                    prefs.getLong(dateKey(id), System.currentTimeMillis()),
                    outgoing
            ));
        }
        messages.sort((left, right) -> Long.compare(left.dateMillis, right.dateMillis));
        return messages;
    }

    static List<Conversation> loadConversations(Context context, boolean blockedOnly, String query) {
        List<ConversationBuilder> builders = new ArrayList<>();
        SharedPreferences prefs = prefs(context);
        for (String id : savedIds(prefs)) {
            String address = prefs.getString(addressKey(id), "");
            String body = prefs.getString(bodyKey(id), PICTURE_MESSAGE);
            String sender = prefs.getString(senderKey(id), "");
            boolean filtered = isFiltered(context, address, sender, body, isOutgoing(prefs, id));
            if (filtered != blockedOnly || !matchesQuery(context, address, body, query)) {
                continue;
            }
            long date = prefs.getLong(dateKey(id), System.currentTimeMillis());
            boolean unread = !isOutgoing(prefs, id) && isUnread(prefs, id);
            mergeConversationBuilder(builders, address, body, date, unread);
        }

        List<Conversation> conversations = new ArrayList<>();
        for (ConversationBuilder builder : builders) {
            conversations.add(new Conversation(
                    "",
                    builder.address,
                    conversationName(context, builder.address),
                    conversationPhotoUri(context, builder.address),
                    builder.snippet,
                    builder.dateMillis,
                    builder.unreadCount
            ));
        }
        return conversations;
    }

    static String diagnosticSummary(Context context) {
        SharedPreferences prefs = prefs(context);
        int messages = 0;
        int groups = 0;
        int failed = 0;
        for (String id : savedIds(prefs)) {
            messages++;
            if (isGroupAddress(prefs.getString(addressKey(id), ""))) {
                groups++;
            }
            if (ChatMessage.STATUS_FAILED.equals(prefs.getString(statusKey(id), ""))) {
                failed++;
            }
        }
        return "Local MMS records: total=" + messages
                + ", group=" + groups
                + ", failed=" + failed
                + ", pending downloads=" + pendingIds(context).size();
    }

    private static boolean isFiltered(
            Context context,
            String address,
            String senderAddress,
            String body,
            boolean outgoing
    ) {
        if (Blocklist.isBlocked(context, address) || SpamFilter.isMarkedSpam(context, address)) {
            return true;
        }
        if (!TextUtils.isEmpty(senderAddress)
                && (Blocklist.isBlocked(context, senderAddress) || SpamFilter.isMarkedSpam(context, senderAddress))) {
            return true;
        }
        if (outgoing) {
            return false;
        }
        String keywordSender = TextUtils.isEmpty(senderAddress) ? address : senderAddress;
        return SpamFilter.matchesKeywordForUnknownSender(context, keywordSender, body);
    }

    private static void mergeConversationBuilder(List<ConversationBuilder> builders, String address, String body, long dateMillis, boolean unread) {
        for (int i = 0; i < builders.size(); i++) {
            ConversationBuilder existing = builders.get(i);
            if (!AddressUtil.sameConversationAddress(existing.address, address)) {
                continue;
            }
            if (dateMillis > existing.dateMillis) {
                existing.snippet = body;
                existing.dateMillis = dateMillis;
            }
            if (unread) {
                existing.unreadCount++;
            }
            return;
        }
        ConversationBuilder builder = new ConversationBuilder(address, body, dateMillis);
        if (unread) {
            builder.unreadCount = 1;
        }
        builders.add(builder);
    }

    private static String conversationName(Context context, String address) {
        if (isGroupAddress(address)) {
            return displayNameForAddress(context, address);
        }
        return SmsStore.displayNameForAddress(context, address);
    }

    private static String conversationPhotoUri(Context context, String address) {
        return isGroupAddress(address) ? "" : SmsStore.photoUriForAddress(context, address);
    }

    static synchronized int deleteForAddress(Context context, String address) {
        if (TextUtils.isEmpty(address)) {
            return 0;
        }
        SharedPreferences prefs = prefs(context);
        Set<String> ids = savedIds(prefs);
        Set<String> keptIds = new HashSet<>(ids);
        int deleted = 0;
        SharedPreferences.Editor editor = prefs.edit();
        for (String id : ids) {
            String savedAddress = prefs.getString(addressKey(id), "");
            if (AddressUtil.sameConversationAddress(address, savedAddress)) {
                keptIds.remove(id);
                deleted++;
                deleteStoredImage(context, prefs.getString(imageKey(id), ""));
                removeMessage(editor, id);
            }
        }
        editor.putStringSet(KEY_IDS, keptIds).apply();
        return deleted;
    }

    static void markAddressRead(Context context, String address) {
        if (TextUtils.isEmpty(address)) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = null;
        for (String id : savedIds(prefs)) {
            String savedAddress = prefs.getString(addressKey(id), "");
            if (!AddressUtil.sameConversationAddress(address, savedAddress)
                    || !isUnread(prefs, id)) {
                continue;
            }
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putBoolean(readKey(id), true);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    static void markAllRead(Context context) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = null;
        for (String id : savedIds(prefs)) {
            if (!isUnread(prefs, id)) {
                continue;
            }
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putBoolean(readKey(id), true);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private static void deleteStoredImage(Context context, String imageUri) {
        MmsFiles.deleteAppFileUri(context, MmsFiles.IMAGES_DIR, imageUri);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> savedIds(SharedPreferences prefs) {
        return new HashSet<>(prefs.getStringSet(KEY_IDS, new HashSet<>()));
    }

    private static String readKey(String id) {
        return key(READ_PREFIX, id);
    }

    private static boolean isUnread(SharedPreferences prefs, String id) {
        String key = readKey(id);
        return prefs.contains(key) && !prefs.getBoolean(key, true);
    }

    private static boolean isOutgoing(SharedPreferences prefs, String id) {
        return prefs.getBoolean(outgoingKey(id), false);
    }

    private static String pendingAddressKey(String id) {
        return key(PENDING_ADDRESS_PREFIX, id);
    }

    private static String pendingPduKey(String id) {
        return key(PENDING_PDU_PREFIX, id);
    }

    private static String pendingCreatedKey(String id) {
        return key(PENDING_CREATED_PREFIX, id);
    }

    private static String pendingUrlKey(String id) {
        return key(PENDING_URL_PREFIX, id);
    }

    private static String pendingSubscriptionKey(String id) {
        return key(PENDING_SUBSCRIPTION_PREFIX, id);
    }

    private static String pendingRetryKey(String id) {
        return key(PENDING_RETRY_PREFIX, id);
    }

    private static String addressKey(String id) {
        return key(ADDRESS_PREFIX, id);
    }

    private static String senderKey(String id) {
        return key(SENDER_PREFIX, id);
    }

    private static String bodyKey(String id) {
        return key(BODY_PREFIX, id);
    }

    private static String imageKey(String id) {
        return key(IMAGE_PREFIX, id);
    }

    private static String dateKey(String id) {
        return key(DATE_PREFIX, id);
    }

    private static String outgoingKey(String id) {
        return key(OUTGOING_PREFIX, id);
    }

    private static String statusKey(String id) {
        return key(STATUS_PREFIX, id);
    }

    private static String key(String prefix, String id) {
        return prefix + id;
    }

    private static void removeMessage(SharedPreferences.Editor editor, String id) {
        editor.remove(addressKey(id))
                .remove(senderKey(id))
                .remove(bodyKey(id))
                .remove(imageKey(id))
                .remove(dateKey(id))
                .remove(readKey(id))
                .remove(outgoingKey(id))
                .remove(statusKey(id));
    }

    private static boolean matchesQuery(Context context, String address, String body, String query) {
        if (TextUtils.isEmpty(query)) {
            return true;
        }
        String needle = query.toLowerCase(Locale.getDefault());
        if (address.toLowerCase(Locale.getDefault()).contains(needle)
                || (!TextUtils.isEmpty(body) && body.toLowerCase(Locale.getDefault()).contains(needle))) {
            return true;
        }
        String name = conversationName(context, address);
        return !TextUtils.isEmpty(name) && name.toLowerCase(Locale.getDefault()).contains(needle);
    }

    private static boolean looksLikeAttachmentMetadata(String body) {
        if (TextUtils.isEmpty(body)) {
            return false;
        }
        String cleaned = body.trim().toLowerCase(Locale.US);
        return cleaned.matches("ext[0-9]{3,}\\.?") || cleaned.matches("[0-9a-f]{12,}");
    }

    private static boolean looksLikeMmsHeaderJunk(String body) {
        if (TextUtils.isEmpty(body)) {
            return false;
        }
        String cleaned = body.trim().toLowerCase(Locale.US);
        return cleaned.contains("/type=plmn")
                || cleaned.equals("plmn")
                || cleaned.equals("te")
                || cleaned.equals("<")
                || cleaned.equals(">")
                || cleaned.equals("insert-address-token");
    }

    private static boolean looksLikeUnreadableStoredBody(String body) {
        return looksLikeAttachmentMetadata(body)
                || looksLikeMmsHeaderJunk(body)
                || looksLikeUnreadableImageCaption(body);
    }

    private static boolean looksLikeUnreadableImageCaption(String body) {
        if (TextUtils.isEmpty(body) || PICTURE_MESSAGE.equals(body)) {
            return false;
        }
        return TextUtils.isEmpty(MmsPduUtil.cleanDisplayText(body));
    }

    private static String recoveredArchivedDownloadText(Context context, String imageUri) {
        try {
            Uri uri = Uri.parse(imageUri);
            if (!"file".equals(uri.getScheme()) || TextUtils.isEmpty(uri.getPath())) {
                return "";
            }
            String imageName = new File(uri.getPath()).getName();
            int extensionIndex = imageName.lastIndexOf('.');
            if (extensionIndex <= 0) {
                return "";
            }
            File rawPdu = new File(
                    MmsFiles.appFileDirPath(context, MmsFiles.RAW_DOWNLOADS_DIR),
                    imageName.substring(0, extensionIndex) + ".pdu"
            );
            if (!rawPdu.exists() || rawPdu.length() == 0) {
                return "";
            }
            return MmsPduUtil.cleanDisplayText(MmsPduUtil.extractText(Files.readAllBytes(rawPdu.toPath())));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String bestExistingGroupMatch(Context context, List<String> candidateKeys) {
        if (candidateKeys.size() < 3) {
            return "";
        }
        SharedPreferences prefs = prefs(context);
        String bestAddress = "";
        int bestScore = 0;
        for (String id : savedIds(prefs)) {
            String address = prefs.getString(addressKey(id), "");
            if (!isGroupAddress(address)) {
                continue;
            }
            List<String> existingKeys = groupParticipants(address);
            int score = participantOverlap(candidateKeys, existingKeys);
            if (isLikelySameGroup(candidateKeys.size(), existingKeys.size(), score) && score > bestScore) {
                bestAddress = address;
                bestScore = score;
            }
        }
        return bestAddress;
    }

    private static boolean isLikelySameGroup(int candidateSize, int existingSize, int overlap) {
        int smallerGroupSize = Math.min(candidateSize, existingSize);
        int largerGroupSize = Math.max(candidateSize, existingSize);
        if (smallerGroupSize == 2 && largerGroupSize == 3 && overlap == 2) {
            return true;
        }
        if (smallerGroupSize < 3 || overlap < smallerGroupSize) {
            return false;
        }
        return candidateSize == existingSize || largerGroupSize - smallerGroupSize == 1;
    }

    private static String bestExistingOutgoingGroupMatch(Context context, List<String> recipientKeys) {
        if (context == null || recipientKeys.size() < 2) {
            return "";
        }
        String oneExtraParticipantMatch = "";
        SharedPreferences prefs = prefs(context);
        for (String id : savedIds(prefs)) {
            String address = prefs.getString(addressKey(id), "");
            if (!isGroupAddress(address)) {
                continue;
            }
            List<String> existingKeys = groupParticipants(address);
            int overlap = participantOverlap(recipientKeys, existingKeys);
            if (existingKeys.size() == recipientKeys.size() && overlap == recipientKeys.size()) {
                return address;
            }
            if (TextUtils.isEmpty(oneExtraParticipantMatch)
                    && existingKeys.size() == recipientKeys.size() + 1
                    && overlap == recipientKeys.size()) {
                oneExtraParticipantMatch = address;
            }
        }
        return oneExtraParticipantMatch;
    }

    private static int participantOverlap(List<String> left, List<String> right) {
        int count = 0;
        boolean[] matched = new boolean[right.size()];
        for (String leftItem : left) {
            for (int index = 0; index < right.size(); index++) {
                if (!matched[index] && sameParticipant(leftItem, right.get(index))) {
                    matched[index] = true;
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static boolean sameParticipant(String first, String second) {
        String firstKey = participantKey(first);
        String secondKey = participantKey(second);
        if (TextUtils.isEmpty(firstKey) || TextUtils.isEmpty(secondKey)) {
            return false;
        }
        if (TextUtils.equals(firstKey, secondKey)) {
            return true;
        }
        return AddressUtil.hasSinglePhoneAddress(firstKey)
                && AddressUtil.hasSinglePhoneAddress(secondKey)
                && AddressUtil.sameDigits(firstKey, secondKey);
    }

    private static String preferredStoredGroupAddress(
            SharedPreferences prefs,
            Set<String> ids,
            String currentAddress
    ) {
        if (!isGroupAddress(currentAddress)) {
            return currentAddress;
        }
        List<String> currentParticipants = groupParticipants(currentAddress);
        String preferred = currentAddress;
        int preferredSize = currentParticipants.size();
        for (String id : ids) {
            String candidate = cleanedGroupAddress(prefs.getString(addressKey(id), ""));
            if (!isGroupAddress(candidate) || TextUtils.equals(candidate, currentAddress)) {
                continue;
            }
            List<String> candidateParticipants = groupParticipants(candidate);
            int overlap = participantOverlap(currentParticipants, candidateParticipants);
            if (!isLikelySameGroup(currentParticipants.size(), candidateParticipants.size(), overlap)) {
                continue;
            }
            int candidateSize = candidateParticipants.size();
            if (candidateSize < preferredSize
                    || (candidateSize == preferredSize && candidate.compareTo(preferred) < 0)) {
                preferred = candidate;
                preferredSize = candidateSize;
            }
        }
        return preferred;
    }

    private static List<String> participantKeys(List<String> participants) {
        Set<String> keys = new HashSet<>();
        if (participants != null) {
            for (String participant : participants) {
                String key = participantKey(participant);
                if (!TextUtils.isEmpty(key)) {
                    keys.add(key);
                }
            }
        }
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        return sorted;
    }

    private static String participantKey(String participant) {
        if (TextUtils.isEmpty(participant)) {
            return "";
        }
        String digits = AddressUtil.digits(participant);
        if (AddressUtil.isSendableSmsRecipient(participant)
                && digits.length() >= 7
                && digits.length() <= 15) {
            return digits;
        }
        String cleaned = participant.trim().toLowerCase(Locale.US);
        return looksLikeEmail(cleaned) ? cleaned : "";
    }

    private static boolean looksLikeEmail(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        int at = value.indexOf('@');
        return at > 0 && at < value.length() - 1 && value.indexOf(' ', at) < 0;
    }

    private static List<String> groupParticipants(String address) {
        List<String> participants = new ArrayList<>();
        if (!isGroupAddress(address)) {
            return participants;
        }
        String raw = address.substring(GROUP_PREFIX.length());
        if (TextUtils.isEmpty(raw)) {
            return participants;
        }
        for (String participant : raw.split("\\|")) {
            String key = participantKey(participant);
            if (!TextUtils.isEmpty(key)) {
                participants.add(key);
            }
        }
        return participants;
    }

    private static final class ConversationBuilder {
        final String address;
        String snippet;
        long dateMillis;
        int unreadCount;

        ConversationBuilder(String address, String snippet, long dateMillis) {
            this.address = address;
            this.snippet = snippet;
            this.dateMillis = dateMillis;
        }
    }

    static final class Pending {
        final String address;
        final String pduPath;
        final long createdAtMillis;
        final String downloadUrl;
        final int subscriptionId;
        final int retryCount;

        Pending(String address, String pduPath) {
            this(address, pduPath, 0L);
        }

        Pending(String address, String pduPath, long createdAtMillis) {
            this(address, pduPath, createdAtMillis, "", -1, 0);
        }

        Pending(String address, String pduPath, long createdAtMillis, String downloadUrl, int subscriptionId, int retryCount) {
            this.address = address;
            this.pduPath = pduPath;
            this.createdAtMillis = createdAtMillis;
            this.downloadUrl = TextUtils.isEmpty(downloadUrl) ? "" : downloadUrl;
            this.subscriptionId = subscriptionId;
            this.retryCount = Math.max(0, retryCount);
        }
    }

    static final class RetryMessage {
        final String address;
        final String body;
        final String imageUri;

        RetryMessage(String address, String body, String imageUri) {
            this.address = address;
            this.body = body;
            this.imageUri = imageUri;
        }

        boolean hasImage() {
            return !TextUtils.isEmpty(imageUri);
        }
    }
}
