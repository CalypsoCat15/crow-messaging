package com.crowmessenger.messages;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class GroupMmsRecipients {
    private GroupMmsRecipients() {
    }

    static List<String> remoteRecipients(String groupAddress, Set<String> ownNumbers) {
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        for (String participant : LocalMmsStore.participantsForAddress(groupAddress)) {
            String normalized = normalizedRecipient(participant);
            if (TextUtils.isEmpty(normalized)
                    || isPlaceholder(normalized)
                    || isOwnNumber(normalized, ownNumbers)) {
                continue;
            }
            addUniqueRecipient(recipients, normalized);
        }
        return new ArrayList<>(recipients);
    }

    static boolean hasEnoughRecipientsForGroupMms(List<String> recipients) {
        return recipients != null && recipients.size() >= 2;
    }

    static int totalPeopleCount(Context context, String groupAddress) {
        List<String> participants = LocalMmsStore.participantsForAddress(groupAddress);
        if (participants.isEmpty()) {
            return 0;
        }
        Set<String> ownNumbers = knownOwnNumbers(context);
        for (String participant : participants) {
            if (isOwnNumber(participant, ownNumbers)) {
                return participants.size();
            }
        }
        return participants.size() + 1;
    }

    @SuppressLint("HardwareIds")
    static Set<String> knownOwnNumbers(Context context) {
        LinkedHashSet<String> ownNumbers = new LinkedHashSet<>();
        if (context == null) {
            return ownNumbers;
        }
        boolean canReadPhoneState = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
        boolean canReadSms = context.checkSelfPermission(Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
        try {
            TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
            if (telephonyManager != null && canReadPhoneState && canReadSms) {
                addOwnNumber(ownNumbers, telephonyManager.getLine1Number());
            }
        } catch (Exception ignored) {
        }
        try {
            SubscriptionManager subscriptionManager = context.getSystemService(SubscriptionManager.class);
            List<SubscriptionInfo> subscriptions = subscriptionManager == null || !canReadPhoneState
                    ? Collections.emptyList()
                    : subscriptionManager.getActiveSubscriptionInfoList();
            if (subscriptions != null) {
                for (SubscriptionInfo subscription : subscriptions) {
                    addOwnNumber(ownNumbers, subscription.getNumber());
                }
            }
        } catch (Exception ignored) {
        }
        return ownNumbers;
    }

    static String normalizedRecipient(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        String trimmed = address.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (isPlaceholder(lower)) {
            return "";
        }
        String digits = AddressUtil.digits(trimmed);
        if (digits.length() >= 7 && digits.length() <= 15) {
            return digits;
        }
        int at = lower.indexOf('@');
        return at > 0 && at < lower.length() - 1 && lower.indexOf(' ') < 0 ? lower : "";
    }

    private static boolean isPlaceholder(String address) {
        String lower = address.toLowerCase(Locale.US);
        return lower.equals("mms")
                || lower.equals("unknown")
                || lower.equals("insert-address-token")
                || lower.startsWith("thread:");
    }

    static void addUniqueRecipient(LinkedHashSet<String> recipients, String recipient) {
        if (TextUtils.isEmpty(recipient)) {
            return;
        }
        for (String existing : recipients) {
            if (TextUtils.equals(existing, recipient) || AddressUtil.sameDigits(existing, recipient)) {
                return;
            }
        }
        recipients.add(recipient);
    }

    private static boolean isOwnNumber(String recipient, Set<String> ownNumbers) {
        if (ownNumbers == null || ownNumbers.isEmpty()) {
            return false;
        }
        for (String ownNumber : ownNumbers) {
            if (AddressUtil.sameDigits(recipient, ownNumber)) {
                return true;
            }
        }
        return false;
    }

    private static void addOwnNumber(Set<String> ownNumbers, String number) {
        String normalized = normalizedRecipient(number);
        if (!TextUtils.isEmpty(normalized)) {
            ownNumbers.add(normalized);
        }
    }
}
