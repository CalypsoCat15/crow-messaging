package com.crowmessenger.messages;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;

final class MmsTextPduComposer {
    private static final int HEADER_CONTENT_TYPE = 0x84;
    private static final int HEADER_FROM = 0x89;
    private static final int HEADER_MESSAGE_TYPE = 0x8C;
    private static final int HEADER_MMS_VERSION = 0x8D;
    private static final int HEADER_TRANSACTION_ID = 0x98;
    private static final int HEADER_TO = 0x97;
    private static final int MESSAGE_TYPE_SEND_REQ = 0x80;
    private static final int MMS_VERSION_1_2 = 0x92;
    private static final int INSERT_ADDRESS_TOKEN = 0x81;
    private static final int CONTENT_TYPE_TEXT_PLAIN = 0x83;
    private static final int CONTENT_TYPE_MULTIPART_MIXED = 0xA3;
    private static final byte[] IMAGE_JPEG_HEADER = asciiHeader("image/jpeg");

    private MmsTextPduComposer() {
    }

    static byte[] compose(String transactionId, List<String> recipients, String body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeSendHeaders(out, transactionId, recipients);
        writeMultipartText(out, body);
        return out.toByteArray();
    }

    static byte[] composeImage(String transactionId, List<String> recipients, String body, byte[] imageBytes) {
        return composeImage(transactionId, recipients, body, "image/jpeg", imageBytes);
    }

    static byte[] composeImage(
            String transactionId,
            List<String> recipients,
            String body,
            String imageContentType,
            byte[] imageBytes
    ) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeSendHeaders(out, transactionId, recipients);
        writeMultipartImage(out, body, imageContentType, imageBytes);
        return out.toByteArray();
    }

    private static void writeSendHeaders(ByteArrayOutputStream out, String transactionId, List<String> recipients) {
        out.write(HEADER_MESSAGE_TYPE);
        out.write(MESSAGE_TYPE_SEND_REQ);
        out.write(HEADER_TRANSACTION_ID);
        writeTextString(out, transactionId);
        out.write(HEADER_MMS_VERSION);
        out.write(MMS_VERSION_1_2);
        out.write(HEADER_FROM);
        out.write(1);
        out.write(INSERT_ADDRESS_TOKEN);
        for (String address : normalizedRecipients(recipients)) {
            out.write(HEADER_TO);
            writeTextString(out, pduAddress(address));
        }
        out.write(HEADER_CONTENT_TYPE);
        out.write(CONTENT_TYPE_MULTIPART_MIXED);
    }

    private static void writeMultipartText(ByteArrayOutputStream out, String body) {
        byte[] text = safe(body).getBytes(StandardCharsets.UTF_8);
        byte[] partHeaders = new byte[] { (byte) CONTENT_TYPE_TEXT_PLAIN };
        writeUintvar(out, 1);
        writeUintvar(out, partHeaders.length);
        writeUintvar(out, text.length);
        out.write(partHeaders, 0, partHeaders.length);
        out.write(text, 0, text.length);
    }

    private static void writeMultipartImage(
            ByteArrayOutputStream out,
            String body,
            String imageContentType,
            byte[] imageBytes
    ) {
        byte[] image = imageBytes == null ? new byte[0] : imageBytes;
        byte[] text = safe(body).trim().getBytes(StandardCharsets.UTF_8);
        int partCount = text.length > 0 ? 2 : 1;
        writeUintvar(out, partCount);
        byte[] imageHeader = "image/gif".equalsIgnoreCase(safe(imageContentType).trim())
                ? asciiHeader("image/gif")
                : IMAGE_JPEG_HEADER;
        writePart(out, imageHeader, image);
        if (text.length > 0) {
            writePart(out, new byte[] { (byte) CONTENT_TYPE_TEXT_PLAIN }, text);
        }
    }

    private static void writePart(ByteArrayOutputStream out, byte[] headers, byte[] data) {
        byte[] safeHeaders = headers == null ? new byte[0] : headers;
        byte[] safeData = data == null ? new byte[0] : data;
        writeUintvar(out, safeHeaders.length);
        writeUintvar(out, safeData.length);
        out.write(safeHeaders, 0, safeHeaders.length);
        out.write(safeData, 0, safeData.length);
    }

    private static void writeTextString(ByteArrayOutputStream out, String value) {
        byte[] bytes = safe(value).getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
        out.write(0);
    }

    private static void writeUintvar(ByteArrayOutputStream out, int value) {
        int stack = value & 0x7F;
        value >>>= 7;
        while (value > 0) {
            stack <<= 8;
            stack |= 0x80 | (value & 0x7F);
            value >>>= 7;
        }
        while (true) {
            out.write(stack & 0xFF);
            if ((stack & 0x80) == 0) {
                break;
            }
            stack >>>= 8;
        }
    }

    private static String pduAddress(String recipient) {
        return recipient.contains("@") ? recipient + "/TYPE=EMAIL" : recipient + "/TYPE=PLMN";
    }

    private static LinkedHashSet<String> normalizedRecipients(List<String> recipients) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (recipients == null) {
            return normalized;
        }
        for (String recipient : recipients) {
            GroupMmsRecipients.addUniqueRecipient(normalized, GroupMmsRecipients.normalizedRecipient(recipient));
        }
        return normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static byte[] asciiHeader(String value) {
        byte[] raw = value.getBytes(StandardCharsets.US_ASCII);
        byte[] header = new byte[raw.length + 1];
        System.arraycopy(raw, 0, header, 0, raw.length);
        return header;
    }
}
