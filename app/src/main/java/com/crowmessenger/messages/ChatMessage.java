package com.crowmessenger.messages;

import android.text.TextUtils;

final class ChatMessage {
    static final String STATUS_SENDING = "Sending";
    static final String STATUS_FAILED = "Failed";
    static final String STATUS_NOT_SAVED = "Sent, not saved";

    final String body;
    final String imageUri;
    final String senderAddress;
    final String status;
    final String localStatusId;
    final long dateMillis;
    final boolean outgoing;

    ChatMessage(String body, long dateMillis, boolean outgoing) {
        this(body, "", dateMillis, outgoing);
    }

    ChatMessage(String body, String imageUri, long dateMillis, boolean outgoing) {
        this(body, imageUri, "", dateMillis, outgoing);
    }

    ChatMessage(String body, String imageUri, String senderAddress, long dateMillis, boolean outgoing) {
        this(body, imageUri, senderAddress, "", dateMillis, outgoing);
    }

    ChatMessage(String body, String imageUri, String senderAddress, String status, long dateMillis, boolean outgoing) {
        this(body, imageUri, senderAddress, status, "", dateMillis, outgoing);
    }

    ChatMessage(String body, String imageUri, String senderAddress, String status, String localStatusId, long dateMillis, boolean outgoing) {
        this.body = TextUtils.isEmpty(body) ? "" : body;
        this.imageUri = TextUtils.isEmpty(imageUri) ? "" : imageUri;
        this.senderAddress = TextUtils.isEmpty(senderAddress) ? "" : senderAddress;
        this.status = TextUtils.isEmpty(status) ? "" : status;
        this.localStatusId = TextUtils.isEmpty(localStatusId) ? "" : localStatusId;
        this.dateMillis = dateMillis;
        this.outgoing = outgoing;
    }

    static ChatMessage sending(String body, long dateMillis) {
        return new ChatMessage(body, "", "", STATUS_SENDING, dateMillis, true);
    }

    static ChatMessage outgoingStatus(String body, String status, String localStatusId, long dateMillis) {
        return new ChatMessage(body, "", "", status, localStatusId, dateMillis, true);
    }

    boolean hasLocalStatus() {
        return !TextUtils.isEmpty(status) && !TextUtils.isEmpty(localStatusId);
    }

    String displayStatus() {
        if (!TextUtils.isEmpty(status)) {
            return status;
        }
        return outgoing ? "Sent" : "";
    }
}
