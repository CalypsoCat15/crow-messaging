package com.crowmessenger.messages;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class MessageNotifier {
    private static final String CHANNEL_ID = "messages";
    private static final String CONTACT_CHANNEL_PREFIX = "messages_contact_";
    private static final String PREFS = "message_notifications";
    private static final String IDS_PREFIX = "ids_";
    private static final String ADDRESS_PREFIX = "address_";
    private static final String NEXT_INCOMING_ID = "next_incoming_id";

    private MessageNotifier() {
    }

    static void showIncoming(Context context, String address, String body) {
        showIncoming(context, address, "", body, 0L);
    }

    static void showIncoming(Context context, String address, String body, long dateMillis) {
        showIncoming(context, address, "", body, dateMillis);
    }

    static void showIncoming(Context context, String address, String senderAddress, String body) {
        showIncoming(context, address, senderAddress, body, 0L);
    }

    private static void showIncoming(Context context, String address, String senderAddress, String body, long dateMillis) {
        if (shouldSuppressIncoming(context, address, senderAddress, body)) {
            return;
        }
        // A real incoming event is the only automatic reason to bring a conversation out of Trash.
        TrashStore.restore(context, address);
        if (!canPostNotifications(context)) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        String channelId = channelIdFor(context, address);
        ensureChannel(context, manager, address, channelId);
        String notificationBody = notificationBody(context, address, senderAddress, body);

        int notificationId = nextIncomingNotificationId(context);
        PendingIntent pendingIntent = incomingContentPendingIntent(
                context,
                address,
                body,
                dateMillis,
                notificationId
        );

        Notification.Builder builder = new Notification.Builder(context, channelId);

        builder.setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(notificationTitle(context, address))
                .setContentText(notificationBody)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(notificationBody))
                .setContentIntent(pendingIntent)
                .addAction(markReadAction(context, address))
                .addAction(replyAction(context, address))
                .setAutoCancel(true);

        rememberIncomingNotification(context, address, notificationId);
        manager.notify(notificationId, builder.build());
    }

    static synchronized int nextIncomingNotificationId(Context context) {
        SharedPreferences preferences = prefs(context);
        long sequence = preferences.getLong(NEXT_INCOMING_ID, 0L) + 1L;
        preferences.edit().putLong(NEXT_INCOMING_ID, sequence).apply();
        // Incoming IDs stay negative so they cannot collide with the non-negative failure IDs.
        return -1 - (int) (sequence % Integer.MAX_VALUE);
    }

    static PendingIntent incomingContentPendingIntent(
            Context context,
            String address,
            String body,
            long dateMillis,
            int notificationId
    ) {
        return PendingIntent.getActivity(
                context,
                notificationId,
                incomingContentIntent(context, address, body, dateMillis),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    static Intent incomingContentIntent(Context context, String address, String body, long dateMillis) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_ADDRESS, address);
        if (!TextUtils.isEmpty(body) && dateMillis > 0L) {
            intent.putExtra(MainActivity.EXTRA_MESSAGE_BODY, body);
            intent.putExtra(MainActivity.EXTRA_MESSAGE_DATE, dateMillis);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    private static Notification.Action markReadAction(Context context, String address) {
        Intent intent = notificationActionIntent(context, NotificationActionReceiver.ACTION_MARK_READ, address);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                AddressUtil.stableId("mark_read", address),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Action.Builder builder = new Notification.Action.Builder(
                android.R.drawable.checkbox_on_background,
                "Mark read",
                pendingIntent
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ);
        }
        return builder.build();
    }

    private static Notification.Action replyAction(Context context, String address) {
        Intent intent = notificationActionIntent(context, NotificationActionReceiver.ACTION_REPLY, address);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                AddressUtil.stableId("reply", address),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        RemoteInput remoteInput = new RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY)
                .setLabel("Reply")
                .build();
        Notification.Action.Builder builder = new Notification.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Reply",
                pendingIntent
        )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY);
        }
        return builder.build();
    }

    static Intent notificationActionIntent(Context context, String action, String address) {
        return new Intent(context, NotificationActionReceiver.class)
                .setAction(action)
                .setData(Uri.parse("crow-notification://" + Uri.encode(action) + "/" + Uri.encode(address)))
                .putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address);
    }

    static void showScheduledFailed(Context context, String address, String reason) {
        showFailure(context, address, "Scheduled text not sent", TextUtils.isEmpty(reason) ? "Scheduled text could not be sent." : reason, "scheduled_failed");
    }

    static void showSendFailed(Context context, String address, String reason) {
        showFailure(context, address, "Text not sent", TextUtils.isEmpty(reason) ? "Text message could not be sent." : reason, "send_failed");
    }

    static void showPictureSendFailed(Context context, String address, String reason) {
        showFailure(context, address, "Picture not sent", TextUtils.isEmpty(reason) ? "Picture message could not be sent." : reason, "picture_send_failed");
    }

    static void showSentNotSaved(Context context, String address, String reason) {
        showFailure(context, address, "Text sent but not saved", TextUtils.isEmpty(reason) ? "Text was sent, but Crow Messenger could not save it." : reason, "sent_not_saved");
    }

    private static void showFailure(Context context, String address, String title, String body, String keyPrefix) {
        if (!canPostNotifications(context)) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        ensureChannel(context, manager, address, CHANNEL_ID);

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_ADDRESS, address);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                AddressUtil.stableId(keyPrefix, address),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);
        builder.setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        manager.notify(AddressUtil.stableId(keyPrefix, address + body), builder.build());
    }

    static synchronized void clearIncomingForAddress(Context context, String address) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        SharedPreferences prefs = prefs(context);
        String directKey = notificationIdsKey(address);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : new HashSet<>(prefs.getAll().keySet())) {
            if (!key.startsWith(IDS_PREFIX)) {
                continue;
            }
            String storedAddress = prefs.getString(notificationAddressKey(key), "");
            boolean storedAddressMatches = AddressUtil.sameConversationAddress(address, storedAddress);
            if ((!TextUtils.isEmpty(storedAddress) && !storedAddressMatches)
                    || (TextUtils.isEmpty(storedAddress) && !key.equals(directKey))) {
                continue;
            }
            Set<String> ids = new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
            cancelNotificationIds(manager, ids);
            editor.remove(key).remove(notificationAddressKey(key));
        }
        editor.apply();
    }

    static synchronized void clearAllIncoming(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith(IDS_PREFIX)) {
                continue;
            }
            Set<String> ids = new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
            cancelNotificationIds(manager, ids);
            editor.remove(key).remove(notificationAddressKey(key));
        }
        editor.apply();
    }

    static void resetContactChannel(Context context, String address) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            String prefix = contactChannelPrefix(address);
            String legacyPrefix = legacyContactChannelPrefix(address);
            for (NotificationChannel channel : manager.getNotificationChannels()) {
                if (channel.getId().startsWith(prefix) || channel.getId().startsWith(legacyPrefix)) {
                    manager.deleteNotificationChannel(channel.getId());
                }
            }
        }
    }

    private static void ensureChannel(Context context, NotificationManager manager, String address, String channelId) {
        if (manager.getNotificationChannel(channelId) != null) {
            return;
        }
        boolean contactChannel = !CHANNEL_ID.equals(channelId);

        NotificationChannel channel = new NotificationChannel(
                channelId,
                channelId.equals(CHANNEL_ID) ? "Messages" : SmsStore.displayNameForAddress(context, address),
                NotificationManager.IMPORTANCE_HIGH
        );

        ContactNotificationPrefs.NotificationSetting setting = ContactNotificationPrefs.setting(context, address);
        if (contactChannel && setting.isSilent()) {
            channel.setSound(null, null);
            channel.enableVibration(false);
        } else if (contactChannel && setting.hasCustomSound()) {
            Uri sound = setting.soundUri();
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(sound, attributes);
        }

        manager.createNotificationChannel(channel);
    }

    private static boolean canPostNotifications(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static String channelIdFor(Context context, String address) {
        ContactNotificationPrefs.NotificationSetting setting = ContactNotificationPrefs.setting(context, address);
        if (!setting.hasCustomSetting()) {
            return CHANNEL_ID;
        }
        String soundKey = setting.key();
        return contactChannelPrefix(address) + AddressUtil.stableId(TextUtils.isEmpty(soundKey) ? "default" : soundKey);
    }

    static boolean shouldSuppressIncoming(Context context, String address, String senderAddress, String body) {
        if (Blocklist.isBlocked(context, address) || SpamFilter.isMarkedSpam(context, address)) {
            return true;
        }
        if (!TextUtils.isEmpty(senderAddress)
                && (Blocklist.isBlocked(context, senderAddress) || SpamFilter.isMarkedSpam(context, senderAddress))) {
            return true;
        }
        String keywordSender = TextUtils.isEmpty(senderAddress) ? address : senderAddress;
        return SpamFilter.matchesKeywordForUnknownSender(context, keywordSender, body);
    }

    static String contactChannelPrefix(String address) {
        return CONTACT_CHANNEL_PREFIX + Uri.encode(channelAddressKey(address)) + "_";
    }

    private static String legacyContactChannelPrefix(String address) {
        return CONTACT_CHANNEL_PREFIX + AddressUtil.stableId(address) + "_";
    }

    private static String channelAddressKey(String address) {
        if (LocalMmsStore.isGroupAddress(address)) {
            return address;
        }
        String digits = AddressUtil.digits(address);
        if (!TextUtils.isEmpty(digits) && AddressUtil.isSendableSmsRecipient(address)) {
            return digits;
        }
        return TextUtils.isEmpty(address) ? "unknown" : address.trim().toLowerCase(Locale.US);
    }

    private static synchronized void rememberIncomingNotification(Context context, String address, int notificationId) {
        SharedPreferences prefs = prefs(context);
        String key = notificationIdsKey(address);
        Set<String> ids = new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
        ids.add(String.valueOf(notificationId));
        prefs.edit()
                .putStringSet(key, ids)
                .putString(notificationAddressKey(key), address)
                .apply();
    }

    private static void cancelNotificationIds(NotificationManager manager, Set<String> ids) {
        if (manager == null) {
            return;
        }
        for (String id : ids) {
            try {
                manager.cancel(Integer.parseInt(id));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    static String notificationIdsKey(String address) {
        return IDS_PREFIX + Uri.encode(AddressUtil.stableKey(address));
    }

    private static String notificationAddressKey(String idsKey) {
        return ADDRESS_PREFIX + idsKey.substring(IDS_PREFIX.length());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String notificationTitle(Context context, String address) {
        String title = SmsStore.displayNameForAddress(context, address);
        if (!TextUtils.isEmpty(title)) {
            return title;
        }
        return TextUtils.isEmpty(address) ? "Messages" : address;
    }

    private static String notificationBody(Context context, String address, String senderAddress, String body) {
        String message = TextUtils.isEmpty(body) ? "New message" : body;
        if (!LocalMmsStore.isGroupAddress(address) || TextUtils.isEmpty(senderAddress)) {
            return message;
        }
        String sender = LocalMmsStore.displayNameForParticipant(context, senderAddress);
        return TextUtils.isEmpty(sender) ? message : sender + ": " + message;
    }
}
