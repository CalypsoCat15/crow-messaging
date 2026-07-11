package com.crowmessenger.messages;

import java.util.Locale;

enum SearchFilter {
    ALL("All"),
    PEOPLE("People"),
    MESSAGE_TEXT("Message text"),
    PICTURES("Pictures");

    final String label;

    SearchFilter(String label) {
        this.label = label;
    }

    boolean matches(String query, String address, String name, String body, boolean hasPicture) {
        if (this == PICTURES) {
            if (!hasPicture) {
                return false;
            }
            return isEmpty(query) || contains(address, query) || contains(name, query) || contains(body, query);
        }
        if (isEmpty(query)) {
            return true;
        }
        if (this == PEOPLE) {
            return contains(address, query) || contains(name, query);
        }
        if (this == MESSAGE_TEXT) {
            return contains(body, query);
        }
        return contains(address, query) || contains(name, query) || contains(body, query);
    }

    private static boolean contains(String value, String query) {
        return !isEmpty(value)
                && value.toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault()));
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
