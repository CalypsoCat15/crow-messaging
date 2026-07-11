package com.crowmessenger.messages;

import android.text.TextUtils;

final class Conversation {
    final String threadId;
    final String address;
    final String name;
    final String photoUri;
    final String snippet;
    final long dateMillis;
    final int unreadCount;

    Conversation(String threadId, String address, String name, String snippet, long dateMillis, int unreadCount) {
        this(threadId, address, name, "", snippet, dateMillis, unreadCount);
    }

    Conversation(String threadId, String address, String name, String photoUri, String snippet, long dateMillis, int unreadCount) {
        this.threadId = TextUtils.isEmpty(threadId) ? "" : threadId;
        this.address = TextUtils.isEmpty(address) ? "" : address;
        this.name = TextUtils.isEmpty(name) ? "" : name;
        this.photoUri = TextUtils.isEmpty(photoUri) ? "" : photoUri;
        this.snippet = TextUtils.isEmpty(snippet) ? "" : snippet;
        this.dateMillis = dateMillis;
        this.unreadCount = unreadCount;
    }
}
