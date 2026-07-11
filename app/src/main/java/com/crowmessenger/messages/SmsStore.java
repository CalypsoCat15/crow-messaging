package com.crowmessenger.messages;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.text.TextUtils;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SmsStore {
    private static final Uri SMS_URI = Uri.parse("content://sms");
    private static final Uri MMS_URI = Uri.parse("content://mms");
    private static final String[] MESSAGE_COLUMNS = new String[] {
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
    };
    private static final String[] SMS_ID_ADDRESS_COLUMNS = new String[] {
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS
    };
    private static final Map<String, String> CONTACT_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> CONTACT_PHOTO_CACHE = new ConcurrentHashMap<>();

    private SmsStore() {
    }

    static List<Conversation> loadConversations(Context context, boolean blockedOnly, String query) {
        Map<String, ConversationBuilder> byThread = new HashMap<>();
        Set<String> hiddenThreads = new HashSet<>();
        ContentResolver resolver = context.getContentResolver();
        Blocklist.Matcher blockMatcher = Blocklist.matcher(context);
        SpamFilter.Matcher spamMatcher = SpamFilter.matcher(context);
        boolean smsUnavailable = false;
        String[] columns = new String[] {
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
                Telephony.Sms.TYPE
        };

        try (Cursor cursor = resolver.query(SMS_URI, columns, null, null, Telephony.Sms.DEFAULT_SORT_ORDER)) {
            if (cursor == null) {
                smsUnavailable = true;
            } else {
                while (cursor.moveToNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        return Collections.emptyList();
                    }
                    String threadId = cursor.getString(0);
                    if (!blockedOnly && hiddenThreads.contains(threadId)) {
                        continue;
                    }
                    String address = cleanAddress(cursor.getString(1));
                    String body = cursor.getString(2);
                    boolean blockedSender = blockMatcher.isBlocked(address) || spamMatcher.isMarkedSpam(address);
                    if (shouldHideWholeThread(blockedOnly, blockedSender)) {
                        byThread.remove(threadId);
                        hiddenThreads.add(threadId);
                        continue;
                    }
                    boolean keywordSpam = !isOutgoingSms(cursor.getInt(5))
                            && spamMatcher.matchesKeywordForUnknownSender(address, body);
                    if (!shouldShowMessage(blockedOnly, blockedSender, keywordSpam)) {
                        continue;
                    }
                    if (!matchesQuery(context, address, body, query)) {
                        continue;
                    }
                    long date = cursor.getLong(3);
                    boolean unread = cursor.getInt(4) == 0;
                    ConversationBuilder builder = byThread.get(threadId);
                    if (builder == null) {
                        builder = new ConversationBuilder(threadId, address, body, date);
                        byThread.put(threadId, builder);
                    }
                    if (unread) {
                        builder.unreadCount++;
                    }
                }
            }
        } catch (SecurityException | IllegalArgumentException ex) {
            smsUnavailable = true;
        }

        List<Conversation> conversations = new ArrayList<>();
        for (ConversationBuilder builder : byThread.values()) {
            if (Thread.currentThread().isInterrupted()) {
                return Collections.emptyList();
            }
            String name = displayNameForAddress(context, builder.address);
            conversations.add(new Conversation(
                    builder.threadId,
                    builder.address,
                    name,
                    photoUriForAddress(context, builder.address),
                    builder.snippet,
                    builder.dateMillis,
                    builder.unreadCount
            ));
        }
        for (Conversation localConversation : LocalMmsStore.loadConversations(context, blockedOnly, query)) {
            mergeLocalConversation(conversations, localConversation);
        }
        if (!blockedOnly) {
            for (Conversation pendingConversation : SmsSender.pendingConversations(context, query)) {
                mergeLocalConversation(conversations, pendingConversation);
            }
        }
        for (DraftStore.Draft draft : DraftStore.drafts(context)) {
            mergeDraftConversation(context, conversations, draft, blockedOnly, query);
        }
        conversations.sort((left, right) -> {
            boolean leftPinned = PinnedStore.isPinned(context, left.address);
            boolean rightPinned = PinnedStore.isPinned(context, right.address);
            if (leftPinned != rightPinned) {
                return leftPinned ? -1 : 1;
            }
            return Long.compare(right.dateMillis, left.dateMillis);
        });
        if (conversations.isEmpty() && smsUnavailable && !blockedOnly && TextUtils.isEmpty(query)) {
            return sampleConversations();
        }
        return conversations;
    }

    static boolean shouldHideWholeThread(boolean blockedOnly, boolean blockedSender) {
        return !blockedOnly && blockedSender;
    }

    static boolean shouldShowMessage(boolean blockedOnly, boolean blockedSender, boolean keywordSpam) {
        return (blockedSender || keywordSpam) == blockedOnly;
    }

    static boolean shouldShowThreadMessage(Context context, String address, String body, boolean blockedOnly) {
        return shouldShowThreadMessage(context, address, body, blockedOnly, false);
    }

    static boolean shouldShowThreadMessage(
            Context context,
            String address,
            String body,
            boolean blockedOnly,
            boolean outgoing
    ) {
        boolean blockedSender = Blocklist.isBlocked(context, address) || SpamFilter.isMarkedSpam(context, address);
        boolean keywordSpam = !outgoing && SpamFilter.matchesKeywordForUnknownSender(context, address, body);
        return shouldShowMessage(blockedOnly, blockedSender, keywordSpam);
    }

    static List<ChatMessage> loadMessages(Context context, String threadId) {
        return loadMessages(context, threadId, findAddressForThread(context, threadId), false);
    }

    private static List<ChatMessage> loadMessages(Context context, String threadId, String address, boolean blockedOnly) {
        List<ChatMessage> messages = loadSmsMessages(
                context,
                address,
                blockedOnly,
                Telephony.Sms.THREAD_ID + "=?",
                new String[] { threadId },
                Telephony.Sms.DATE + " ASC",
                false
        );
        appendLocalMessagesAndSort(context, address, messages, blockedOnly);
        return messages;
    }

    static List<ChatMessage> loadMessagesForAddress(Context context, String address) {
        return loadMessagesForAddress(context, address, false);
    }

    static List<ChatMessage> loadMessagesForAddress(Context context, String address, boolean blockedOnly) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return LocalMmsStore.loadForAddress(context, address, blockedOnly);
        }
        String threadId = findThreadIdForAddress(context, address);
        if (!TextUtils.isEmpty(threadId)) {
            return loadMessages(context, threadId, address, blockedOnly);
        }
        List<ChatMessage> messages = loadSmsMessages(
                context,
                address,
                blockedOnly,
                Telephony.Sms.ADDRESS + "=?",
                new String[] { address },
                Telephony.Sms.DATE + " ASC",
                false
        );
        appendLocalMessagesAndSort(context, address, messages, blockedOnly);
        return messages;
    }

    static List<ChatMessage> loadRecentMessagesForAddress(Context context, String address, int limit) {
        return loadRecentMessagesForAddress(context, address, limit, false);
    }

    static List<ChatMessage> loadRecentMessagesForAddress(Context context, String address, int limit, boolean blockedOnly) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return mostRecent(LocalMmsStore.loadForAddress(context, address, blockedOnly), limit);
        }
        String threadId = findThreadIdForAddress(context, address);
        if (!TextUtils.isEmpty(threadId)) {
            List<ChatMessage> messages = loadSmsMessages(
                    context,
                    address,
                    blockedOnly,
                    Telephony.Sms.THREAD_ID + "=?",
                    new String[] { threadId },
                    Telephony.Sms.DATE + " DESC LIMIT " + Math.max(1, limit),
                    true
            );
            appendLocalMessagesAndSort(context, address, messages, blockedOnly);
            return mostRecent(messages, limit);
        }
        List<ChatMessage> messages = loadSmsMessages(
                context,
                address,
                blockedOnly,
                Telephony.Sms.ADDRESS + "=?",
                new String[] { address },
                Telephony.Sms.DATE + " DESC LIMIT " + Math.max(1, limit),
                true
        );
        appendLocalMessagesAndSort(context, address, messages, blockedOnly);
        return mostRecent(messages, limit);
    }

    private static List<ChatMessage> loadSmsMessages(
            Context context,
            String address,
            boolean blockedOnly,
            String selection,
            String[] selectionArgs,
            String sortOrder,
            boolean newestFirst
    ) {
        List<ChatMessage> messages = new ArrayList<>();
        Blocklist.Matcher blockMatcher = Blocklist.matcher(context);
        SpamFilter.Matcher spamMatcher = SpamFilter.matcher(context);
        try (Cursor cursor = context.getContentResolver().query(
                SMS_URI,
                MESSAGE_COLUMNS,
                selection,
                selectionArgs,
                sortOrder)) {
            if (cursor == null) {
                return messages;
            }
            while (cursor.moveToNext()) {
                int type = cursor.getInt(2);
                String body = cursor.getString(0);
                boolean blockedSender = blockMatcher.isBlocked(address) || spamMatcher.isMarkedSpam(address);
                boolean keywordSpam = !isOutgoingSms(type)
                        && spamMatcher.matchesKeywordForUnknownSender(address, body);
                if (!shouldShowMessage(blockedOnly, blockedSender, keywordSpam)) {
                    continue;
                }
                messages.add(new ChatMessage(
                        body,
                        cursor.getLong(1),
                        isOutgoingSms(type)
                ));
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return messages;
        }
        if (newestFirst) {
            Collections.reverse(messages);
        }
        return messages;
    }

    private static void appendLocalMessagesAndSort(Context context, String address, List<ChatMessage> messages, boolean blockedOnly) {
        messages.addAll(LocalMmsStore.loadForAddress(context, address, blockedOnly));
        if (!blockedOnly) {
            messages.addAll(SmsSender.pendingMessagesForAddress(context, address));
        }
        messages.sort((left, right) -> Long.compare(left.dateMillis, right.dateMillis));
    }

    private static List<ChatMessage> mostRecent(List<ChatMessage> messages, int limit) {
        int count = Math.max(1, limit);
        int from = Math.max(0, messages.size() - count);
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    static void mergeLocalConversation(List<Conversation> conversations, Conversation localConversation) {
        int existingIndex = conversationIndex(conversations, localConversation.address);
        if (existingIndex >= 0) {
            Conversation existing = conversations.get(existingIndex);
            boolean localIsNewest = localConversation.dateMillis > existing.dateMillis;
            conversations.set(existingIndex, new Conversation(
                    existing.threadId,
                    existing.address,
                    existing.name,
                    existing.photoUri,
                    localIsNewest ? localConversation.snippet : existing.snippet,
                    localIsNewest ? localConversation.dateMillis : existing.dateMillis,
                    existing.unreadCount + localConversation.unreadCount
            ));
            return;
        }
        conversations.add(localConversation);
    }

    private static void mergeDraftConversation(
            Context context,
            List<Conversation> conversations,
            DraftStore.Draft draft,
            boolean blockedOnly,
            String query
    ) {
        if (draft == null || TextUtils.isEmpty(draft.address) || blockedOnly
                || Blocklist.isBlocked(context, draft.address)
                || SpamFilter.isMarkedSpam(context, draft.address)
                || !matchesQuery(context, draft.address, draft.body, query)) {
            return;
        }
        int existingIndex = conversationIndex(conversations, draft.address);
        if (existingIndex >= 0) {
            Conversation existing = conversations.get(existingIndex);
            if (draft.dateMillis > existing.dateMillis) {
                conversations.set(existingIndex, new Conversation(
                        existing.threadId,
                        existing.address,
                        existing.name,
                        existing.photoUri,
                        draft.body,
                        draft.dateMillis,
                        existing.unreadCount
                ));
            }
            return;
        }
        conversations.add(draftConversation(context, draft));
    }

    private static int conversationIndex(List<Conversation> conversations, String address) {
        for (int i = 0; i < conversations.size(); i++) {
            if (AddressUtil.sameConversationAddress(conversations.get(i).address, address)) {
                return i;
            }
        }
        return -1;
    }

    private static Conversation draftConversation(Context context, DraftStore.Draft draft) {
        if (LocalMmsStore.isGroupAddress(draft.address)) {
            return new Conversation(
                    "",
                    draft.address,
                    LocalMmsStore.displayNameForAddress(context, draft.address),
                    "",
                    draft.body,
                    draft.dateMillis,
                    0
            );
        }
        return new Conversation(
                "",
                draft.address,
                displayNameForAddress(context, draft.address),
                photoUriForAddress(context, draft.address),
                draft.body,
                draft.dateMillis,
                0
        );
    }

    private static boolean isOutgoingSms(int type) {
        return type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX;
    }

    static Conversation conversationForAddress(Context context, String address) {
        String clean = cleanAddress(address);
        if (LocalMmsStore.isGroupAddress(clean)) {
            return new Conversation(
                    "",
                    clean,
                    LocalMmsStore.displayNameForAddress(context, clean),
                    "",
                    "",
                    System.currentTimeMillis(),
                    0
            );
        }
        String threadId = findThreadIdForAddress(context, clean);
        String displayName = displayNameForAddress(context, clean);
        return new Conversation(
                TextUtils.isEmpty(threadId) ? "" : threadId,
                clean,
                displayName,
                photoUriForAddress(context, clean),
                "",
                System.currentTimeMillis(),
                0
        );
    }

    static String displayNameForAddress(Context context, String address) {
        String clean = cleanAddress(address);
        if (LocalMmsStore.isGroupAddress(clean)) {
            return LocalMmsStore.displayNameForAddress(context, clean);
        }
        String cached = CONTACT_NAME_CACHE.get(clean);
        if (cached != null) {
            return cached;
        }
        String name = resolveContactName(context, clean);
        String displayName = TextUtils.isEmpty(name) ? clean : name;
        CONTACT_NAME_CACHE.put(clean, displayName);
        return displayName;
    }

    static String photoUriForAddress(Context context, String address) {
        String clean = cleanAddress(address);
        if (LocalMmsStore.isGroupAddress(clean)) {
            return "";
        }
        String cached = CONTACT_PHOTO_CACHE.get(clean);
        if (cached != null) {
            return cached;
        }
        String photoUri = resolveContactPhotoUri(context, clean);
        CONTACT_PHOTO_CACHE.put(clean, photoUri);
        return photoUri;
    }

    static void clearContactCaches() {
        CONTACT_NAME_CACHE.clear();
        CONTACT_PHOTO_CACHE.clear();
        ContactLookup.clearCache();
    }

    static boolean saveIncomingSms(Context context, String address, String body, long dateMillis) {
        return saveSms(context, Telephony.Sms.Inbox.CONTENT_URI, address, body, dateMillis, 0, Telephony.Sms.MESSAGE_TYPE_INBOX);
    }

    static boolean saveSentSms(Context context, String address, String body, long dateMillis) {
        return saveSms(context, Telephony.Sms.Sent.CONTENT_URI, address, body, dateMillis, 1, Telephony.Sms.MESSAGE_TYPE_SENT);
    }

    private static boolean saveSms(
            Context context,
            Uri uri,
            String address,
            String body,
            long dateMillis,
            int read,
            int type
    ) {
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.ADDRESS, cleanAddress(address));
        values.put(Telephony.Sms.BODY, body);
        values.put(Telephony.Sms.DATE, dateMillis);
        values.put(Telephony.Sms.READ, read);
        values.put(Telephony.Sms.TYPE, type);
        try {
            return context.getContentResolver().insert(uri, values) != null;
        } catch (SecurityException | IllegalArgumentException ignored) {
            return false;
        }
    }

    static int deleteConversation(Context context, String threadId, String address) {
        int deleted = deleteAndroidConversation(context, threadId, address);
        deleted += LocalMmsStore.deleteForAddress(context, address);
        deleted += LocalMmsStore.clearPendingForAddress(context, address);
        deleted += SmsSender.deletePendingForAddress(context, address);
        if (DraftStore.clear(context, address)) {
            deleted++;
        }
        if (PinnedStore.isPinned(context, address)) {
            PinnedStore.unpin(context, address);
            deleted++;
        }
        MessageNotifier.clearIncomingForAddress(context, address);
        return deleted;
    }

    private static int deleteAndroidConversation(Context context, String threadId, String address) {
        int deleted = 0;
        ContentResolver resolver = context.getContentResolver();
        try {
            if (TextUtils.isEmpty(threadId) && !TextUtils.isEmpty(address)) {
                threadId = findThreadIdForAddress(context, address);
            }
            if (!TextUtils.isEmpty(threadId)) {
                String[] args = new String[] { threadId };
                deleted += resolver.delete(SMS_URI, Telephony.Sms.THREAD_ID + "=?", args);
                deleted += resolver.delete(MMS_URI, "thread_id=?", args);
            } else if (!TextUtils.isEmpty(address)) {
                deleted += resolver.delete(SMS_URI, Telephony.Sms.ADDRESS + "=?", new String[] { cleanAddress(address) });
                deleted += deleteSmsForMatchingAddress(context, address);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return deleted;
        }
        return deleted;
    }

    static void markThreadRead(Context context, String threadId) {
        if (TextUtils.isEmpty(threadId)) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.READ, 1);
        try {
            context.getContentResolver().update(
                    SMS_URI,
                    values,
                    Telephony.Sms.THREAD_ID + "=? AND " + Telephony.Sms.READ + "=0",
                    new String[] { threadId }
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    static void markAddressRead(Context context, String address) {
        markConversationRead(context, findThreadIdForAddress(context, address), address);
    }

    static void markConversationRead(Context context, String threadId, String address) {
        if (!TextUtils.isEmpty(threadId)) {
            markThreadRead(context, threadId);
        } else {
            markSmsAddressRead(context, address);
        }
        LocalMmsStore.markAddressRead(context, address);
    }

    static void markAllRead(Context context) {
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.READ, 1);
        try {
            context.getContentResolver().update(
                    SMS_URI,
                    values,
                    Telephony.Sms.READ + "=0",
                    null
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
        ContentValues mmsValues = new ContentValues();
        mmsValues.put("read", 1);
        try {
            context.getContentResolver().update(
                    MMS_URI,
                    mmsValues,
                    "read=0",
                    null
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    static String formatTime(Context context, long dateMillis) {
        return DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(dateMillis);
    }

    static String formatMessageTimestamp(Context context, long dateMillis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(dateMillis);
    }

    private static String cleanAddress(String address) {
        return TextUtils.isEmpty(address) ? "Unknown" : address;
    }

    private static String resolveContactName(Context context, String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return "";
        }
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[] { ContactsContract.PhoneLookup.DISPLAY_NAME },
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return "";
        }
        return "";
    }

    private static String resolveContactPhotoUri(Context context, String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return "";
        }
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[] { ContactsContract.PhoneLookup.PHOTO_URI },
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String photoUri = cursor.getString(0);
                return TextUtils.isEmpty(photoUri) ? "" : photoUri;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return "";
        }
        return "";
    }

    private static String findThreadIdForAddress(Context context, String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        String exact = findExactThreadIdForAddress(context, address);
        if (!TextUtils.isEmpty(exact)) {
            return exact;
        }
        return findRecentThreadIdForMatchingAddress(context, address);
    }

    private static String findExactThreadIdForAddress(Context context, String address) {
        try (Cursor cursor = context.getContentResolver().query(
                SMS_URI,
                new String[] { Telephony.Sms.THREAD_ID },
                Telephony.Sms.ADDRESS + "=?",
                new String[] { cleanAddress(address) },
                Telephony.Sms.DATE + " DESC LIMIT 1")) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return "";
        }
        return "";
    }

    private static String findRecentThreadIdForMatchingAddress(Context context, String address) {
        String target = AddressUtil.digits(address);
        if (TextUtils.isEmpty(target)) {
            return "";
        }
        try (Cursor cursor = context.getContentResolver().query(
                SMS_URI,
                new String[] { Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS },
                null,
                null,
                Telephony.Sms.DATE + " DESC LIMIT 500")) {
            if (cursor == null) {
                return "";
            }
            while (cursor.moveToNext()) {
                if (AddressUtil.sameDigits(target, cursor.getString(1))) {
                    return cursor.getString(0);
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return "";
        }
        return "";
    }

    private static int deleteSmsForMatchingAddress(Context context, String address) {
        List<String> ids = recentSmsIdsForMatchingAddress(context, address, false);
        int deleted = 0;
        ContentResolver resolver = context.getContentResolver();
        for (String id : ids) {
            try {
                deleted += resolver.delete(SMS_URI, Telephony.Sms._ID + "=?", new String[] { id });
            } catch (SecurityException | IllegalArgumentException ignored) {
                return deleted;
            }
        }
        return deleted;
    }

    private static void markSmsAddressRead(Context context, String address) {
        List<String> ids = recentSmsIdsForMatchingAddress(context, address, true);
        if (ids.isEmpty()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.READ, 1);
        ContentResolver resolver = context.getContentResolver();
        for (String id : ids) {
            try {
                resolver.update(SMS_URI, values, Telephony.Sms._ID + "=?", new String[] { id });
            } catch (SecurityException | IllegalArgumentException ignored) {
                return;
            }
        }
    }

    private static List<String> recentSmsIdsForMatchingAddress(Context context, String address, boolean unreadOnly) {
        String target = AddressUtil.digits(address);
        if (TextUtils.isEmpty(target)) {
            return Collections.emptyList();
        }
        String selection = unreadOnly ? Telephony.Sms.READ + "=0" : null;
        List<String> ids = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                SMS_URI,
                SMS_ID_ADDRESS_COLUMNS,
                selection,
                null,
                Telephony.Sms.DATE + " DESC LIMIT 500")) {
            if (cursor == null) {
                return ids;
            }
            while (cursor.moveToNext()) {
                if (AddressUtil.sameDigits(target, cursor.getString(1))) {
                    ids.add(cursor.getString(0));
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return ids;
        }
        return ids;
    }

    private static String findAddressForThread(Context context, String threadId) {
        if (TextUtils.isEmpty(threadId)) {
            return "";
        }
        try (Cursor cursor = context.getContentResolver().query(
                SMS_URI,
                new String[] { Telephony.Sms.ADDRESS },
                Telephony.Sms.THREAD_ID + "=?",
                new String[] { threadId },
                Telephony.Sms.DATE + " DESC LIMIT 1")) {
            if (cursor != null && cursor.moveToFirst()) {
                return cleanAddress(cursor.getString(0));
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return "";
        }
        return "";
    }

    private static boolean matchesQuery(Context context, String address, String body, String query) {
        if (TextUtils.isEmpty(query)) {
            return true;
        }
        String needle = query.toLowerCase(Locale.getDefault());
        String name = displayNameForAddress(context, address);
        return address.toLowerCase(Locale.getDefault()).contains(needle)
                || (!TextUtils.isEmpty(name) && name.toLowerCase(Locale.getDefault()).contains(needle))
                || (!TextUtils.isEmpty(body) && body.toLowerCase(Locale.getDefault()).contains(needle));
    }

    private static List<Conversation> sampleConversations() {
        List<Conversation> samples = new ArrayList<>();
        long now = System.currentTimeMillis();
        samples.add(new Conversation("sample-alex", "+15551234567", "Alex", "Set Crow Messenger as your default SMS app to show your real chats.", now, 1));
        samples.add(new Conversation("sample-family", "+15557654321", "Family", "Your conversations will appear here once permission is granted.", now - 7200000, 0));
        return samples;
    }

    private static final class ConversationBuilder {
        final String threadId;
        final String address;
        final String snippet;
        final long dateMillis;
        int unreadCount;

        ConversationBuilder(String threadId, String address, String snippet, long dateMillis) {
            this.threadId = threadId;
            this.address = address;
            this.snippet = snippet;
            this.dateMillis = dateMillis;
        }
    }

}
