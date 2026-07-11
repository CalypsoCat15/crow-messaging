package com.crowmessenger.messages;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VerificationCodeUtil {
    private static final Pattern CODE_PATTERN = Pattern.compile("(?<![0-9])([0-9]{4,8})(?![0-9])");
    private static final Pattern CODE_CONTEXT = Pattern.compile(
            "\\b(code|otp|passcode|pin|verification|verify|security|authentication|login|sign[ -]?in)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private VerificationCodeUtil() {
    }

    static String findCode(String message) {
        if (message == null || message.isEmpty() || !CODE_CONTEXT.matcher(message).find()) {
            return "";
        }
        Matcher matcher = CODE_PATTERN.matcher(message);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!looksLikeYear(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private static boolean looksLikeYear(String value) {
        if (value == null || value.length() != 4) {
            return false;
        }
        try {
            int year = Integer.parseInt(value.toLowerCase(Locale.US));
            return year >= 1900 && year <= 2099;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
