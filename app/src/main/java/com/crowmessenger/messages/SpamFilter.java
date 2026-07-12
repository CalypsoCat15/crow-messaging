package com.crowmessenger.messages;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SpamFilter {
    private static final String PREFS = "spam_senders";
    private static final String KEY_SENDERS = "senders";
    private static final String KEY_CUSTOM_KEYWORDS = "custom_keywords";
    private static final String RAW_PREFIX = "raw:";
    private static final String TEL_PREFIX = "tel:";

    private SpamFilter() {
    }

    static boolean isSpam(Context context, String address, String body) {
        return matcher(context).isSpam(address, body);
    }

    static boolean isMarkedSpam(Context context, String address) {
        return matcher(context).isMarkedSpam(address);
    }

    static void markSpam(Context context, String address) {
        String key = key(address);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        Set<String> updated = new HashSet<>(senders(context));
        AddressUtil.removeMatchingPhoneValues(updated, address, SpamFilter::phoneSenderValue);
        updated.add(key);
        save(context, updated);
    }

    static void unmarkSpam(Context context, String address) {
        String key = key(address);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        Set<String> updated = new HashSet<>(senders(context));
        updated.remove(key);
        AddressUtil.removeMatchingPhoneValues(updated, address, SpamFilter::phoneSenderValue);
        save(context, updated);
    }

    static List<String> customKeywords(Context context) {
        List<String> keywords = new ArrayList<>(keywords(context));
        Collections.sort(keywords);
        return keywords;
    }

    static int addCustomKeywords(Context context, String rawKeywords) {
        if (TextUtils.isEmpty(rawKeywords)) {
            return 0;
        }
        Set<String> updated = new HashSet<>(keywords(context));
        int before = updated.size();
        for (String keyword : rawKeywords.split("[,\\n]")) {
            String clean = cleanKeyword(keyword);
            if (!TextUtils.isEmpty(clean)) {
                updated.add(clean);
            }
        }
        saveKeywords(context, updated);
        return updated.size() - before;
    }

    static int removeCustomKeywords(Context context, List<String> selectedKeywords) {
        if (selectedKeywords == null || selectedKeywords.isEmpty()) {
            return 0;
        }
        Set<String> updated = new HashSet<>(keywords(context));
        int before = updated.size();
        for (String keyword : selectedKeywords) {
            updated.remove(cleanKeyword(keyword));
        }
        saveKeywords(context, updated);
        return before - updated.size();
    }

    static void replaceCustomKeywords(Context context, List<String> replacementKeywords) {
        Set<String> updated = new HashSet<>();
        if (replacementKeywords != null) {
            for (String keyword : replacementKeywords) {
                String clean = cleanKeyword(keyword);
                if (!TextUtils.isEmpty(clean)) {
                    updated.add(clean);
                }
            }
        }
        saveKeywords(context, updated);
    }

    static boolean matchesCustomKeyword(Context context, String body) {
        return matcher(context).matchesCustomKeyword(body);
    }

    static boolean matchesKeywordForUnknownSender(Context context, String address, String body) {
        return matcher(context).matchesKeywordForUnknownSender(address, body);
    }

    static Matcher matcher(Context context) {
        return new Matcher(context.getApplicationContext(), senders(context), keywords(context));
    }

    static final class Matcher {
        private final Context context;
        private final Set<String> senders;
        private final Set<String> keywords;
        private final Map<String, Boolean> savedContacts = new HashMap<>();

        private Matcher(Context context, Set<String> senders, Set<String> keywords) {
            this.context = context;
            this.senders = senders;
            this.keywords = keywords;
        }

        boolean isSpam(String address, String body) {
            return isMarkedSpam(address) || matchesKeywordForUnknownSender(address, body);
        }

        boolean isMarkedSpam(String address) {
            String key = key(address);
            return !TextUtils.isEmpty(key)
                    && (senders.contains(key)
                    || AddressUtil.containsMatchingPhoneValue(senders, address, SpamFilter::phoneSenderValue));
        }

        boolean matchesCustomKeyword(String body) {
            if (TextUtils.isEmpty(body)) {
                return false;
            }
            String lower = body.toLowerCase(Locale.US);
            for (String keyword : keywords) {
                if (!TextUtils.isEmpty(keyword) && lower.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        boolean matchesKeywordForUnknownSender(String address, String body) {
            if (!matchesCustomKeyword(body)) {
                return false;
            }
            String key = AddressUtil.stableKey(address);
            Boolean saved = savedContacts.get(key);
            if (saved == null) {
                saved = ContactLookup.isSavedContact(context, address);
                savedContacts.put(key, saved);
            }
            return !saved;
        }
    }

    private static String cleanKeyword(String keyword) {
        return TextUtils.isEmpty(keyword) ? "" : keyword.trim().toLowerCase(Locale.US);
    }

    private static String key(String address) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return RAW_PREFIX + address.trim().toLowerCase(Locale.US);
        }
        String digits = AddressUtil.digits(address);
        if (!TextUtils.isEmpty(digits)) {
            return TEL_PREFIX + digits;
        }
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        return RAW_PREFIX + address.trim().toLowerCase(Locale.US);
    }

    private static String phoneSenderValue(String sender) {
        if (TextUtils.isEmpty(sender) || sender.startsWith(RAW_PREFIX)) {
            return "";
        }
        if (sender.startsWith(TEL_PREFIX)) {
            return sender.substring(TEL_PREFIX.length());
        }
        return sender;
    }

    private static Set<String> senders(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_SENDERS, new HashSet<>()));
    }

    private static Set<String> keywords(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> raw = new HashSet<>(prefs.getStringSet(KEY_CUSTOM_KEYWORDS, new HashSet<>()));
        Set<String> cleaned = new HashSet<>();
        for (String keyword : raw) {
            String clean = cleanKeyword(keyword);
            if (!TextUtils.isEmpty(clean)) {
                cleaned.add(clean);
            }
        }
        if (!cleaned.equals(raw)) {
            saveKeywords(context, cleaned);
        }
        return cleaned;
    }

    private static void save(Context context, Set<String> senders) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_SENDERS, senders)
                .apply();
    }

    private static void saveKeywords(Context context, Set<String> keywords) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_CUSTOM_KEYWORDS, keywords)
                .apply();
    }
}
