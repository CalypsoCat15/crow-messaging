package com.crowmessenger.messages;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Telephony;
import android.text.TextUtils;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.core.content.ContextCompat;

final class TroubleshootingReport {
    private TroubleshootingReport() {
    }

    static String create(Context context) {
        StringBuilder report = new StringBuilder();
        report.append("Crow Messenger troubleshooting report\n")
                .append("Created: ")
                .append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                        .format(new Date()))
                .append("\nApp version: ").append(versionName(context))
                .append("\nAndroid: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")")
                .append("\nDevice: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\nDefault SMS app: ").append(isDefaultSmsApp(context) ? "yes" : "no")
                .append("\nPermissions: SMS=").append(permission(context, Manifest.permission.READ_SMS))
                .append(", phone=").append(permission(context, Manifest.permission.READ_PHONE_STATE))
                .append(", notifications=").append(notificationPermission(context))
                .append("\n").append(LocalMmsStore.diagnosticSummary(context))
                .append("\nRecent MMS events:\n")
                .append(privateMmsEvents(MmsDebugStore.last(context)))
                .append("\n\nPrivacy: names, phone numbers, captions, and message text are not included.");
        return report.toString();
    }

    static String privateMmsEvents(String events) {
        if (TextUtils.isEmpty(events)) {
            return "No MMS events recorded.";
        }
        return events
                .replaceAll("\\*\\*\\*\\d{4}", "***")
                .replaceAll("(?i)https?://\\S+", "[link removed]")
                .replaceAll("(?i)file=[^\\s,]+", "file=[redacted]");
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static boolean isDefaultSmsApp(Context context) {
        return TextUtils.equals(context.getPackageName(), Telephony.Sms.getDefaultSmsPackage(context));
    }

    private static String permission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                ? "granted"
                : "missing";
    }

    private static String notificationPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                ? "granted"
                : permission(context, Manifest.permission.POST_NOTIFICATIONS);
    }
}
