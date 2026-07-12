package com.crowmessenger.messages;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuppressLint("ApplySharedPref")
final class ScheduledMessageStore {
    private static final String PREFS = "crow_scheduled_messages";
    private static final String KEY_IDS = "ids";
    private static final String KEY_PREFIX = "message_";

    private ScheduledMessageStore() {
    }

    static synchronized ScheduledMessage save(Context context, String address, String body, long sendAtMillis) {
        String cleanAddress = cleanText(address);
        String cleanBody = cleanText(body);
        if (TextUtils.isEmpty(cleanAddress) || TextUtils.isEmpty(cleanBody) || sendAtMillis <= 0) {
            return null;
        }
        String id = UUID.randomUUID().toString();
        ScheduledMessage message = new ScheduledMessage(id, cleanAddress, cleanBody, sendAtMillis, System.currentTimeMillis());
        SharedPreferences prefs = prefs(context);
        Set<String> ids = ids(prefs);
        ids.add(id);
        boolean saved = prefs.edit()
                .putStringSet(KEY_IDS, ids)
                .putString(KEY_PREFIX + id, message.toJson())
                .commit();
        return saved ? message : null;
    }

    static ScheduledMessage find(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        return storedMessage(prefs(context), id);
    }

    static synchronized List<ScheduledMessage> all(Context context) {
        SharedPreferences prefs = prefs(context);
        Set<String> ids = ids(prefs);
        Set<String> validIds = new HashSet<>(ids);
        List<ScheduledMessage> messages = new ArrayList<>();
        SharedPreferences.Editor cleanup = null;
        for (String id : ids) {
            ScheduledMessage message = storedMessage(prefs, id);
            if (message != null) {
                messages.add(message);
            } else {
                if (cleanup == null) {
                    cleanup = prefs.edit();
                }
                validIds.remove(id);
                cleanup.remove(KEY_PREFIX + id);
            }
        }
        if (cleanup != null) {
            cleanup.putStringSet(KEY_IDS, validIds).apply();
        }
        Collections.sort(messages, (left, right) -> Long.compare(left.sendAtMillis, right.sendAtMillis));
        return messages;
    }

    static List<ScheduledMessage> forAddress(Context context, String address) {
        return matchingAddressMessages(context, address);
    }

    static boolean hasForAddress(Context context, String address) {
        return !matchingAddressMessages(context, address).isEmpty();
    }

    static synchronized List<ScheduledMessage> deleteForAddress(Context context, String address) {
        List<ScheduledMessage> deleted = matchingAddressMessages(context, address);
        if (deleted.isEmpty()) {
            return deleted;
        }
        SharedPreferences prefs = prefs(context);
        Set<String> keptIds = ids(prefs);
        SharedPreferences.Editor editor = prefs.edit();
        for (ScheduledMessage message : deleted) {
            keptIds.remove(message.id);
            editor.remove(KEY_PREFIX + message.id);
        }
        editor.putStringSet(KEY_IDS, keptIds).commit();
        return deleted;
    }

    private static List<ScheduledMessage> matchingAddressMessages(Context context, String address) {
        List<ScheduledMessage> matches = new ArrayList<>();
        if (TextUtils.isEmpty(address)) {
            return matches;
        }
        for (ScheduledMessage message : all(context)) {
            if (AddressUtil.sameConversationAddress(message.address, address)) {
                matches.add(message);
            }
        }
        return matches;
    }

    static synchronized void delete(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        Set<String> ids = ids(prefs);
        ids.remove(id);
        prefs.edit()
                .putStringSet(KEY_IDS, ids)
                .remove(KEY_PREFIX + id)
                .commit();
    }

    static synchronized void markFailed(Context context, ScheduledMessage message, String reason) {
        if (message == null || TextUtils.isEmpty(message.id)) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        Set<String> ids = ids(prefs);
        ids.add(message.id);
        ScheduledMessage failed = new ScheduledMessage(
                message.id,
                message.address,
                message.body,
                message.sendAtMillis,
                message.createdAtMillis,
                TextUtils.isEmpty(reason) ? "Could not send." : reason,
                System.currentTimeMillis()
        );
        prefs.edit()
                .putStringSet(KEY_IDS, ids)
                .putString(KEY_PREFIX + failed.id, failed.toJson())
                .commit();
    }

    private static Set<String> ids(SharedPreferences prefs) {
        return new HashSet<>(prefs.getStringSet(KEY_IDS, Collections.emptySet()));
    }

    private static ScheduledMessage storedMessage(SharedPreferences prefs, String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        String json = prefs.getString(KEY_PREFIX + id, "");
        ScheduledMessage message = ScheduledMessage.fromJson(json);
        if (message == null || !id.equals(message.id)) {
            return null;
        }
        return message;
    }

    private static String cleanText(String value) {
        return TextUtils.isEmpty(value) ? "" : value.trim();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static final class ScheduledMessage {
        final String id;
        final String address;
        final String body;
        final long sendAtMillis;
        final long createdAtMillis;
        final String failureReason;
        final long failedAtMillis;

        ScheduledMessage(String id, String address, String body, long sendAtMillis, long createdAtMillis) {
            this(id, address, body, sendAtMillis, createdAtMillis, "", 0);
        }

        ScheduledMessage(
                String id,
                String address,
                String body,
                long sendAtMillis,
                long createdAtMillis,
                String failureReason,
                long failedAtMillis
        ) {
            this.id = id;
            this.address = address;
            this.body = body;
            this.sendAtMillis = sendAtMillis;
            this.createdAtMillis = createdAtMillis;
            this.failureReason = failureReason;
            this.failedAtMillis = failedAtMillis;
        }

        boolean failed() {
            return failedAtMillis > 0;
        }

        String toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("address", address);
                object.put("body", body);
                object.put("sendAtMillis", sendAtMillis);
                object.put("createdAtMillis", createdAtMillis);
                object.put("failureReason", failureReason);
                object.put("failedAtMillis", failedAtMillis);
            } catch (JSONException ignored) {
            }
            return object.toString();
        }

        static ScheduledMessage fromJson(String json) {
            if (TextUtils.isEmpty(json)) {
                return null;
            }
            try {
                JSONObject object = new JSONObject(json);
                ScheduledMessage message = new ScheduledMessage(
                        object.optString("id"),
                        cleanText(object.optString("address")),
                        cleanText(object.optString("body")),
                        object.optLong("sendAtMillis"),
                        object.optLong("createdAtMillis"),
                        object.optString("failureReason"),
                        object.optLong("failedAtMillis")
                );
                if (!message.hasRequiredFields()) {
                    return null;
                }
                return message;
            } catch (JSONException ignored) {
                return null;
            }
        }

        private boolean hasRequiredFields() {
            return !TextUtils.isEmpty(id)
                    && !TextUtils.isEmpty(cleanText(address))
                    && !TextUtils.isEmpty(cleanText(body))
                    && sendAtMillis > 0;
        }
    }
}
