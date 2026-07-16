package com.crowmessenger.messages;

import android.net.Uri;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MmsPduUtil {
    private static final int HEADER_CONTENT_LOCATION = 0x83;
    private static final int HEADER_CC = 0x82;
    private static final int HEADER_CONTENT_TYPE = 0x84;
    private static final int HEADER_FROM = 0x89;
    private static final int HEADER_MESSAGE_TYPE = 0x8C;
    private static final int HEADER_TRANSACTION_ID = 0x98;
    private static final int HEADER_TO = 0x97;
    private static final int MESSAGE_TYPE_NOTIFICATION_IND = 0x82;
    private static final int PART_CONTENT_TYPE_TEXT_PLAIN = 0x83;
    private static final int CONTENT_TYPE_MULTIPART_MIXED = 0xA3;
    private static final int CONTENT_TYPE_MULTIPART_RELATED = 0xB3;
    private static final int CONTENT_TYPE_MULTIPART_ALTERNATIVE = 0xB4;
    private static final int MAX_PART_HEADER_BYTES = 512;
    private static final int MAX_TEXT_PART_BYTES = 8192;
    private static final int MAX_MULTIPART_SCAN_BYTES = 65536;
    private static final int MAX_TEXT_CANDIDATES = 8;
    private static final int MAX_PART_COUNT = 25;
    private static final int PART_CONTENT_TYPE_SCAN_BYTES = 24;
    private static final int TEXT_MARKER_SCAN_BYTES = 4096;
    private static final Charset MMS_TEXT_CHARSET = StandardCharsets.ISO_8859_1;
    private static final String[] SMIL_REGION_TOKENS = { "image", "text", "video", "audio" };
    private static final String[] SMIL_TEXT_TOKENS = { "region", "src" };
    private static final String[] SMIL_PAR_TOKENS = { "dur" };
    private static final String[] SMIL_FIT_VALUES = { "meet", "slice", "scroll", "hidden", "fill" };
    private static final String[] LAYOUT_DIMENSION_TOKENS = { "root-layout", "layout", "image", "text", "video", "audio" };

    private MmsPduUtil() {
    }

    static NotificationInfo parseNotification(byte[] pdu) {
        if (pdu == null || pdu.length == 0) {
            return new NotificationInfo("", "", "", false);
        }
        int headerStart = findMmsHeaderStart(pdu);
        String transactionId = findTransactionId(pdu, headerStart);
        String downloadUrl = findDownloadUrl(pdu, headerStart, transactionId);
        String sender = findSender(pdu, headerStart);
        return new NotificationInfo(downloadUrl, sender, transactionId, hasNotificationHeader(pdu));
    }

    static String findDownloadUrl(byte[] pdu) {
        if (pdu == null || pdu.length == 0) {
            return "";
        }
        int headerStart = findMmsHeaderStart(pdu);
        return findDownloadUrl(pdu, headerStart, findTransactionId(pdu, headerStart));
    }

    static String findSender(byte[] pdu) {
        if (pdu == null || pdu.length == 0) {
            return "";
        }
        return findSender(pdu, findMmsHeaderStart(pdu));
    }

    private static String findDownloadUrl(byte[] pdu, int headerStart, String transactionId) {
        String contentLocation = findTextHeader(pdu, HEADER_CONTENT_LOCATION, headerStart, true);
        if (looksLikeUrl(contentLocation)) {
            return completeDownloadUrl(contentLocation, transactionId);
        }

        String text = asciiView(pdu, Math.max(0, headerStart));
        int start = text.indexOf("http");
        if (start < 0) {
            return "";
        }
        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c <= 32 || c == '"' || c == '\'' || c == '<' || c == '>') {
                break;
            }
            end++;
        }
        return completeDownloadUrl(cleanUrl(text.substring(start, end)), transactionId);
    }

    private static String findTransactionId(byte[] pdu, int headerStart) {
        return findTextHeader(pdu, HEADER_TRANSACTION_ID, headerStart, false);
    }

    private static String completeDownloadUrl(String contentLocation, String transactionId) {
        if (!looksLikeUrl(contentLocation)) {
            return "";
        }
        if (!missingQueryValue(contentLocation) || TextUtils.isEmpty(transactionId)) {
            return contentLocation;
        }
        return contentLocation + Uri.encode(transactionId);
    }

    private static boolean missingQueryValue(String url) {
        return !TextUtils.isEmpty(url) && url.endsWith("=");
    }

    private static String findSender(byte[] pdu, int headerStart) {
        String fromHeader = findFromHeader(pdu, headerStart);
        if (!TextUtils.isEmpty(fromHeader)) {
            return fromHeader;
        }
        String text = asciiView(pdu, Math.max(0, headerStart));
        String best = "";
        for (String part : text.split("[^0-9+]+")) {
            String digits = part.replaceAll("[^0-9]", "");
            if (digits.length() >= 10 && digits.length() <= 15 && digits.length() > best.replaceAll("[^0-9]", "").length()) {
                best = part;
            }
        }
        return best;
    }

    static byte[] extractFirstImage(byte[] pdu) {
        if (pdu == null || pdu.length == 0) {
            return new byte[0];
        }
        byte[] multipartImage = extractMultipartImage(pdu);
        if (multipartImage.length > 0) {
            return multipartImage;
        }
        byte[] jpeg = sliceBetween(pdu, new byte[] { (byte) 0xFF, (byte) 0xD8 }, new byte[] { (byte) 0xFF, (byte) 0xD9 }, true);
        if (jpeg.length > 0) {
            return jpeg;
        }
        byte[] png = slicePng(pdu);
        if (png.length > 0) {
            return png;
        }
        return sliceWebp(pdu);
    }

    private static byte[] extractMultipartImage(byte[] pdu) {
        MultipartParse multipart = findMultipartParse(pdu);
        if (multipart == null) {
            return new byte[0];
        }
        byte[] bestImage = new byte[0];
        for (MmsPart part : multipart.parts) {
            if (!part.mediaPart) {
                continue;
            }
            int dataStart = trimmedMediaStart(pdu, part.dataStart, part.dataEnd);
            if (!looksLikeImageData(pdu, dataStart, part.dataEnd)) {
                continue;
            }
            byte[] image = copyRange(pdu, dataStart, part.dataEnd);
            if (image.length > bestImage.length) {
                bestImage = image;
            }
        }
        return bestImage;
    }

    static String extractText(byte[] pdu) {
        if (pdu == null || pdu.length == 0) {
            return "";
        }

        List<TextCandidate> candidates = new ArrayList<>();
        MultipartParse multipart = findMultipartParse(pdu);
        if (multipart != null) {
            collectMultipartTextCandidates(pdu, multipart, candidates);
            if (!candidates.isEmpty()) {
                return bestTextCandidate(candidates);
            }
            if (multipart.confirmedContentTypeHeader || multipart.hasSmilPart && multipart.hasMediaPart) {
                return "";
            }
            candidates.clear();
        }

        collectFallbackTextCandidates(pdu, candidates);
        return bestTextCandidate(candidates);
    }

    private static void collectFallbackTextCandidates(byte[] pdu, List<TextCandidate> candidates) {
        int textPart = indexOfAsciiIgnoreCase(pdu, "text/plain", 0);
        int mediaStart = firstMediaIndex(pdu);
        while (textPart >= 0) {
            int start = textPart + "text/plain".length();
            int end = mediaStart > start ? mediaStart : pdu.length;
            collectHumanTextCandidates(pdu, start, end, false, candidates);
            collectTextNearContentTypeMarker(pdu, textPart, candidates);
            textPart = indexOfAsciiIgnoreCase(pdu, "text/plain", textPart + 1);
        }

        if (candidates.isEmpty()) {
            collectHumanTextCandidates(pdu, 0, mediaStart, false, candidates);
        }
        int mediaEnd = firstMediaEndIndex(pdu);
        if (mediaEnd > 0 && mediaEnd < pdu.length && hasTextAttachmentMarker(pdu, mediaEnd, pdu.length)) {
            collectHumanTextCandidates(pdu, mediaEnd, pdu.length, false, candidates);
        }
        if (candidates.isEmpty()) {
            collectUtf16TextCandidates(pdu, 0, mediaStart, true, false, candidates);
            collectUtf16TextCandidates(pdu, 1, mediaStart, true, false, candidates);
            collectUtf16TextCandidates(pdu, 0, mediaStart, false, false, candidates);
            collectUtf16TextCandidates(pdu, 1, mediaStart, false, false, candidates);
        }
        if (candidates.isEmpty() && mediaEnd > 0 && mediaEnd < pdu.length) {
            collectUtf16TextCandidates(pdu, mediaEnd, pdu.length, true, false, candidates);
            collectUtf16TextCandidates(pdu, mediaEnd + 1, pdu.length, true, false, candidates);
            collectUtf16TextCandidates(pdu, mediaEnd, pdu.length, false, false, candidates);
            collectUtf16TextCandidates(pdu, mediaEnd + 1, pdu.length, false, false, candidates);
        }
    }

    private static void collectTextNearContentTypeMarker(byte[] pdu, int markerStart, List<TextCandidate> candidates) {
        int markerEnd = markerStart + "text/plain".length();
        int scanEnd = Math.min(pdu.length, markerEnd + TEXT_MARKER_SCAN_BYTES);
        int nextMedia = nextMediaIndex(pdu, markerEnd);
        if (nextMedia > markerEnd) {
            scanEnd = Math.min(scanEnd, nextMedia);
        }
        int dataStart = firstZeroAfterReadableHeader(pdu, markerEnd, scanEnd);
        if (dataStart >= 0) {
            collectDecodedTextRange(pdu, dataStart, scanEnd, candidates);
            return;
        }
        collectUtf16TextCandidates(pdu, markerEnd, scanEnd, true, false, candidates);
        collectUtf16TextCandidates(pdu, markerEnd + 1, scanEnd, true, false, candidates);
        collectUtf16TextCandidates(pdu, markerEnd, scanEnd, false, false, candidates);
        collectUtf16TextCandidates(pdu, markerEnd + 1, scanEnd, false, false, candidates);
    }

    private static int firstZeroAfterReadableHeader(byte[] pdu, int start, int end) {
        boolean sawReadableHeader = false;
        for (int i = Math.max(0, start); i < Math.min(pdu.length, end - 1); i++) {
            int value = pdu[i] & 0xFF;
            if (value >= 32 && value <= 126) {
                sawReadableHeader = true;
                continue;
            }
            if (value == 0 && sawReadableHeader && hasReadableTextBytes(pdu, i + 1, end)) {
                return i + 1;
            }
        }
        return -1;
    }

    static String cleanDisplayText(String value) {
        String raw = cleanMmsTextWithoutDecoding(value);
        if (!TextUtils.isEmpty(raw) && looksLikeKnownMmsJunk(raw.toLowerCase(Locale.US))) {
            return "";
        }
        String cleaned = cleanMmsText(value);
        return isSafeDisplayText(cleaned) ? cleaned : "";
    }

    static List<String> extractParticipants(byte[] pdu, String fallbackSender) {
        Set<String> participants = new LinkedHashSet<>();
        addAddressCandidate(participants, fallbackSender);
        if (pdu == null || pdu.length == 0) {
            return new ArrayList<>(participants);
        }

        int headerEnd = Math.min(firstMediaIndex(pdu), 4096);
        addAddressHeaders(participants, pdu, HEADER_FROM, headerEnd);
        addAddressHeaders(participants, pdu, HEADER_TO, headerEnd);
        addAddressHeaders(participants, pdu, HEADER_CC, headerEnd);

        String headerText = printableWindow(pdu, 0, headerEnd);
        Set<String> typedParticipants = new LinkedHashSet<>(participants);
        for (String part : headerText.split("[^0-9+@._=/A-Za-z-]+")) {
            if (part.contains("/TYPE=")) {
                addAddressCandidate(typedParticipants, part);
            }
        }
        if (typedParticipants.size() > participants.size()) {
            return new ArrayList<>(typedParticipants);
        }

        for (String part : headerText.split("[^0-9+@._-]+")) {
            if (part.startsWith("+")) {
                addAddressCandidate(participants, part);
            }
        }
        return new ArrayList<>(participants);
    }

    private static void collectMultipartTextCandidates(byte[] pdu, MultipartParse parse, List<TextCandidate> candidates) {
        for (MmsPart part : parse.parts) {
            if (!part.textPart) {
                continue;
            }
            collectTextPartBody(pdu, part.dataStart, part.dataEnd, candidates);
            if (candidates.size() >= MAX_TEXT_CANDIDATES) {
                return;
            }
        }
    }

    private static MultipartParse findMultipartParse(byte[] pdu) {
        int headerStart = findMmsHeaderStart(pdu);
        MultipartParse best = null;
        for (int i = Math.max(0, headerStart); i < Math.min(pdu.length - 2, MAX_MULTIPART_SCAN_BYTES); i++) {
            if ((pdu[i] & 0xFF) != HEADER_CONTENT_TYPE) {
                continue;
            }
            ContentTypeRange contentType = headerContentTypeRange(pdu, i + 1);
            if (contentType == null || !contentTypeIsMultipart(pdu, contentType.start, contentType.end)) {
                continue;
            }
            MultipartParse parsed = parseMultipartAt(pdu, contentType.next, true);
            if (parsed != null && (best == null || parsed.score > best.score)) {
                best = parsed;
            }
        }
        int scanEnd = Math.min(Math.min(firstMediaIndex(pdu), pdu.length), MAX_MULTIPART_SCAN_BYTES);
        for (int i = Math.max(0, headerStart); i < scanEnd - 4; i++) {
            MultipartParse parsed = parseMultipartAt(pdu, i, false);
            if (parsed != null && (best == null || parsed.score > best.score)) {
                best = parsed;
            }
        }
        return best;
    }

    private static ContentTypeRange headerContentTypeRange(byte[] pdu, int start) {
        if (start < 0 || start >= pdu.length) {
            return null;
        }
        int first = pdu[start] & 0xFF;
        if (first <= 30 && start + 1 + first <= pdu.length) {
            return new ContentTypeRange(start + 1, start + 1 + first, start + 1 + first);
        }
        if (first == 31) {
            Uintvar length = readUintvar(pdu, start + 1, pdu.length);
            int end = length.next + length.value;
            if (length.next > start + 1 && end <= pdu.length) {
                return new ContentTypeRange(length.next, end, end);
            }
            return null;
        }
        if (first >= 32 && first <= 126) {
            int end = start;
            while (end < pdu.length && pdu[end] != 0) {
                end++;
            }
            int next = end < pdu.length ? end + 1 : end;
            return end > start ? new ContentTypeRange(start, end, next) : null;
        }
        return new ContentTypeRange(start, start + 1, start + 1);
    }

    private static MultipartParse parseMultipartAt(byte[] pdu, int start, boolean confirmedContentTypeHeader) {
        if (start < 0 || start >= pdu.length) {
            return null;
        }
        Uintvar count = readUintvar(pdu, start, pdu.length);
        if (count.next <= start || count.value <= 0 || count.value > MAX_PART_COUNT) {
            return null;
        }

        int index = count.next;
        int score = 0;
        List<MmsPart> parts = new ArrayList<>();
        for (int i = 0; i < count.value; i++) {
            Uintvar headerLength = readUintvar(pdu, index, pdu.length);
            if (!isUsefulPartLength(headerLength, index, MAX_PART_HEADER_BYTES)) {
                return null;
            }
            Uintvar dataLength = readUintvar(pdu, headerLength.next, pdu.length);
            if (dataLength.next <= headerLength.next || dataLength.value < 0 || dataLength.value > pdu.length) {
                return null;
            }

            int headerStart = dataLength.next;
            int headerEnd = headerStart + headerLength.value;
            int dataStart = headerEnd;
            int dataEnd = dataStart + dataLength.value;
            if (headerEnd > pdu.length || dataEnd > pdu.length) {
                return null;
            }

            boolean textPart = isTextPartHeader(pdu, headerStart, headerEnd);
            boolean smilPart = isSmilPartHeader(pdu, headerStart, headerEnd);
            boolean mediaPart = looksLikeMediaPart(pdu, headerStart, headerEnd, dataStart, dataEnd);
            if (textPart) {
                score += 50;
            }
            if (smilPart) {
                score += 20;
            }
            if (mediaPart) {
                score += 35;
            }
            if (textPart || smilPart || mediaPart) {
                score += 10;
            }
            parts.add(new MmsPart(headerStart, headerEnd, dataStart, dataEnd, textPart, smilPart, mediaPart));
            index = dataEnd;
        }
        return score > 0 ? new MultipartParse(parts, score - (start / 256), confirmedContentTypeHeader) : null;
    }

    private static boolean isUsefulPartLength(Uintvar value, int start, int maxValue) {
        return value.next > start && value.value > 0 && value.value <= maxValue;
    }

    private static boolean isTextPartHeader(byte[] pdu, int start, int end) {
        ContentTypeRange contentType = partContentTypeRange(pdu, start, end);
        if (contentType == null) {
            return false;
        }
        return contentTypeIsTextPlain(pdu, contentType.start, contentType.end);
    }

    private static boolean isSmilPartHeader(byte[] pdu, int start, int end) {
        ContentTypeRange contentType = partContentTypeRange(pdu, start, end);
        if (contentType == null) {
            return false;
        }
        return contentTypeIsSmil(pdu, contentType.start, contentType.end);
    }

    private static ContentTypeRange partContentTypeRange(byte[] pdu, int start, int end) {
        if (start >= end) {
            return null;
        }
        int first = pdu[start] & 0xFF;
        if (first <= 30 && start + 1 + first <= end) {
            return new ContentTypeRange(start + 1, start + 1 + first);
        }
        if (first == 31) {
            Uintvar length = readUintvar(pdu, start + 1, end);
            int valueEnd = length.next + length.value;
            if (length.next > start + 1 && valueEnd <= end) {
                return new ContentTypeRange(length.next, valueEnd);
            }
            return null;
        }
        return new ContentTypeRange(start, end);
    }

    private static boolean contentTypeIsTextPlain(byte[] pdu, int start, int end) {
        if (start >= end) {
            return false;
        }
        if ((pdu[start] & 0xFF) == PART_CONTENT_TYPE_TEXT_PLAIN) {
            return true;
        }
        String contentType = readTextString(pdu, start, Math.min(end, start + PART_CONTENT_TYPE_SCAN_BYTES))
                .toLowerCase(Locale.US);
        return contentType.equals("text/plain") || contentType.startsWith("text/plain;");
    }

    private static boolean contentTypeIsSmil(byte[] pdu, int start, int end) {
        if (start >= end) {
            return false;
        }
        String contentType = readTextString(pdu, start, Math.min(end, start + PART_CONTENT_TYPE_SCAN_BYTES))
                .toLowerCase(Locale.US);
        return contentType.equals("application/smil") || contentType.startsWith("application/smil;");
    }

    private static boolean contentTypeIsMultipart(byte[] pdu, int start, int end) {
        if (start >= end) {
            return false;
        }
        int first = pdu[start] & 0xFF;
        if (first == CONTENT_TYPE_MULTIPART_MIXED
                || first == CONTENT_TYPE_MULTIPART_RELATED
                || first == CONTENT_TYPE_MULTIPART_ALTERNATIVE) {
            return true;
        }
        String contentType = readTextString(pdu, start, Math.min(end, start + PART_CONTENT_TYPE_SCAN_BYTES))
                .toLowerCase(Locale.US);
        return contentType.startsWith("application/vnd.wap.multipart")
                || contentType.equals("multipart/mixed")
                || contentType.startsWith("multipart/mixed;")
                || contentType.equals("multipart/related")
                || contentType.startsWith("multipart/related;");
    }

    private static boolean looksLikeMediaPart(byte[] pdu, int headerStart, int headerEnd, int dataStart, int dataEnd) {
        return indexOfAsciiIgnoreCase(pdu, "image/", headerStart, headerEnd) >= 0
                || startsWith(pdu, dataStart, dataEnd, new byte[] { (byte) 0xFF, (byte) 0xD8 })
                || startsWith(pdu, dataStart, dataEnd, new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 })
                || startsWith(pdu, dataStart, dataEnd, new byte[] { 0x47, 0x49, 0x46, 0x38 })
                || startsWith(pdu, dataStart, dataEnd, new byte[] { 0x52, 0x49, 0x46, 0x46 });
    }

    private static boolean looksLikeImageData(byte[] pdu, int dataStart, int dataEnd) {
        return startsWith(pdu, dataStart, dataEnd, new byte[] { (byte) 0xFF, (byte) 0xD8 })
                || startsWith(pdu, dataStart, dataEnd, new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 })
                || startsWith(pdu, dataStart, dataEnd, new byte[] { 0x47, 0x49, 0x46, 0x38 })
                || startsWith(pdu, dataStart, dataEnd, new byte[] { 0x52, 0x49, 0x46, 0x46 });
    }

    private static int trimmedMediaStart(byte[] pdu, int start, int end) {
        int limit = Math.min(end, Math.min(pdu.length, start + 16));
        for (int i = Math.max(0, start); i < limit; i++) {
            if (looksLikeImageData(pdu, i, end)) {
                return i;
            }
        }
        return start;
    }

    private static void collectTextPartBody(byte[] pdu, int start, int end, List<TextCandidate> candidates) {
        int dataLength = Math.max(0, end - start);
        int adjustedStart = adjustedTextBodyStart(pdu, start, dataLength);
        if (adjustedStart != start) {
            collectDecodedTextRange(pdu, adjustedStart, Math.min(pdu.length, adjustedStart + dataLength), candidates);
            return;
        }
        collectDecodedTextRange(pdu, trimmedTextStart(pdu, start, end), end, candidates);
    }

    private static int adjustedTextBodyStart(byte[] pdu, int start, int dataLength) {
        int scanEnd = Math.min(pdu.length, start + 8);
        for (int i = Math.max(0, start); i < scanEnd; i++) {
            if (pdu[i] != 0 || i + 1 >= pdu.length) {
                continue;
            }
            int candidateStart = i + 1;
            int candidateEnd = Math.min(pdu.length, candidateStart + dataLength);
            if (hasReadableTextBytes(pdu, candidateStart, candidateEnd)) {
                return candidateStart;
            }
        }
        return start;
    }

    private static boolean hasReadableTextBytes(byte[] pdu, int start, int end) {
        for (int i = Math.max(0, start); i < Math.min(pdu.length, end); i++) {
            int value = pdu[i] & 0xFF;
            if (value >= 32 && value <= 126) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTextAttachmentMarker(byte[] pdu, int start, int end) {
        return indexOfAsciiIgnoreCase(pdu, ".txt", start, end) >= 0
                || indexOfAsciiIgnoreCase(pdu, "<text", start, end) >= 0;
    }

    private static int trimmedTextStart(byte[] pdu, int start, int end) {
        int index = Math.max(0, start);
        int limit = Math.min(pdu.length, end);
        while (index < limit) {
            int value = pdu[index] & 0xFF;
            if (value == 0 || value == 0x7F || value == '"' || value == '\'' || value <= 31 || value >= 128) {
                index++;
                continue;
            }
            return index;
        }
        return start;
    }

    private static void collectDecodedTextRange(byte[] pdu, int start, int end, List<TextCandidate> candidates) {
        int bodyEnd = Math.min(end, pdu.length);
        while (bodyEnd > start && pdu[bodyEnd - 1] == 0) {
            bodyEnd--;
        }
        if (bodyEnd <= start) {
            return;
        }
        addTextCandidate(candidates, new String(pdu, start, bodyEnd - start, MMS_TEXT_CHARSET), true);
        addTextCandidate(candidates, new String(pdu, start, bodyEnd - start, StandardCharsets.UTF_8), true);
        collectHumanTextCandidates(pdu, start, bodyEnd, true, candidates);
        collectUtf16TextCandidates(pdu, start, bodyEnd, true, true, candidates);
        collectUtf16TextCandidates(pdu, start + 1, bodyEnd, true, true, candidates);
        collectUtf16TextCandidates(pdu, start, bodyEnd, false, true, candidates);
        collectUtf16TextCandidates(pdu, start + 1, bodyEnd, false, true, candidates);
    }

    private static void addAddressHeaders(Set<String> participants, byte[] pdu, int header, int end) {
        for (int i = 0; i < end - 2; i++) {
            if ((pdu[i] & 0xFF) != header) {
                continue;
            }
            String parsed = readEncodedStringValue(pdu, i + 1, Math.min(end, i + 140));
            addAddressCandidate(participants, parsed);
        }
    }

    private static void addAddressCandidate(Set<String> participants, String candidate) {
        String cleaned = cleanAddress(candidate);
        if (TextUtils.isEmpty(cleaned) || looksLikeUrl(cleaned)) {
            return;
        }
        String lower = cleaned.toLowerCase(Locale.US);
        if (lower.contains("application/") || lower.contains("image/") || lower.contains("text/")) {
            return;
        }
        String digits = cleaned.replaceAll("[^0-9]", "");
        boolean phone = digits.length() >= 7 && digits.length() <= 15;
        boolean email = cleaned.contains("@") && cleaned.indexOf('@') > 0 && cleaned.indexOf('@') < cleaned.length() - 1;
        if (!phone && !email) {
            return;
        }
        participants.add(phone ? digits : cleaned);
    }

    private static void collectHumanTextCandidates(byte[] pdu, int start, int end, boolean trustedTextPart, List<TextCandidate> candidates) {
        StringBuilder run = new StringBuilder();
        for (int i = Math.max(0, start); i < Math.min(pdu.length, end); i++) {
            int value = pdu[i] & 0xFF;
            if (value >= 32 && value <= 126) {
                run.append((char) value);
            } else {
                addTextCandidate(candidates, run.toString(), trustedTextPart);
                run.setLength(0);
            }
        }
        addTextCandidate(candidates, run.toString(), trustedTextPart);
    }

    private static void collectUtf16TextCandidates(byte[] pdu, int offset, int end, boolean littleEndian, boolean trustedTextPart, List<TextCandidate> candidates) {
        StringBuilder run = new StringBuilder();
        int limit = Math.min(pdu.length, end);
        for (int i = offset; i + 1 < limit; i += 2) {
            int first = pdu[i] & 0xFF;
            int second = pdu[i + 1] & 0xFF;
            int value = littleEndian ? first | (second << 8) : (first << 8) | second;
            if (value >= 32 && value <= 126) {
                run.append((char) value);
            } else {
                addTextCandidate(candidates, run.toString(), trustedTextPart);
                run.setLength(0);
            }
        }
        addTextCandidate(candidates, run.toString(), trustedTextPart);
    }

    private static void addTextCandidate(List<TextCandidate> candidates, String value, boolean trustedTextPart) {
        String cleaned = cleanMmsText(value);
        if (isHumanMessageText(cleaned, trustedTextPart)) {
            addCandidate(candidates, cleaned, trustedTextPart);
            return;
        }
        for (String fragment : readableFragments(cleaned)) {
            if (isLikelyRecoveredCaption(fragment) && isHumanMessageText(fragment, false)) {
                addCandidate(candidates, fragment, false);
            }
        }
    }

    private static void addCandidate(List<TextCandidate> candidates, String value, boolean trustedTextPart) {
        for (int i = 0; i < candidates.size(); i++) {
            TextCandidate existing = candidates.get(i);
            if (TextUtils.equals(existing.value, value)) {
                if (trustedTextPart && !existing.trustedTextPart) {
                    candidates.set(i, new TextCandidate(value, true));
                }
                return;
            }
        }
        candidates.add(new TextCandidate(value, trustedTextPart));
    }

    private static List<String> readableFragments(String value) {
        List<String> fragments = new ArrayList<>();
        if (TextUtils.isEmpty(value)) {
            return fragments;
        }
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isLikelyMessageChar(c)) {
                run.append(c);
            } else {
                addReadableFragment(fragments, run);
            }
        }
        addReadableFragment(fragments, run);
        return fragments;
    }

    private static void addReadableFragment(List<String> fragments, StringBuilder run) {
        String fragment = cleanMmsText(run.toString());
        run.setLength(0);
        if (!TextUtils.isEmpty(fragment) && !fragments.contains(fragment)) {
            fragments.add(fragment);
        }
    }

    private static boolean isLikelyMessageChar(char value) {
        return Character.isLetterOrDigit(value)
                || Character.isWhitespace(value)
                || value == '.' || value == ',' || value == '!' || value == '?'
                || value == '\'' || value == '"' || value == '-' || value == ':'
                || value == ';' || value == '(' || value == ')';
    }

    private static boolean isLikelyRecoveredCaption(String value) {
        if (TextUtils.isEmpty(value) || value.length() < 5) {
            return false;
        }
        int letters = 0;
        int digits = 0;
        int spaces = 0;
        int punctuation = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
            } else if (Character.isDigit(c)) {
                digits++;
            } else if (Character.isWhitespace(c)) {
                spaces++;
            } else {
                punctuation++;
            }
        }
        if (letters < 3 || punctuation > letters) {
            return false;
        }
        if (digits > letters && spaces == 0) {
            return false;
        }
        return spaces > 0 || letters >= 5;
    }

    private static String cleanMmsText(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String cleaned = cleanMmsTextWithoutDecoding(decodeQuotedPrintable(value));
        return cleaned.replaceAll("\\s+", " ");
    }

    private static String cleanMmsTextWithoutDecoding(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').trim();
        while (cleaned.startsWith("\"") || cleaned.startsWith("'")) {
            cleaned = cleaned.substring(1).trim();
        }
        while (cleaned.endsWith("\"") || cleaned.endsWith("'")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned.replaceAll("\\s+", " ");
    }

    private static boolean isHumanMessageText(String value, boolean trustedTextPart) {
        if (TextUtils.isEmpty(value) || value.length() > 500) {
            return false;
        }
        if (!isSafeDisplayText(value)) {
            return false;
        }
        if (!hasEnoughReadableText(value, trustedTextPart)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.US);
        if (!trustedTextPart && value.length() < 3) {
            return false;
        }
        if (!trustedTextPart && !value.matches(".*[A-Za-z].*")) {
            return false;
        }
        if (looksLikeUrl(lower) || lower.contains("@")) {
            return false;
        }
        if (looksLikeKnownMmsJunk(lower)) {
            return false;
        }
        if (value.matches("^[./!?,'\";:()\\[\\]{}\\-_*#&%+$=~`|\\\\<>]{1,20}$")) {
            return trustedTextPart && !value.contains("<") && !value.contains(">");
        }
        if (lower.contains("content-") || lower.contains("content_")
                || lower.contains("application/") || lower.contains("image/")
                || lower.contains("text/plain") || lower.contains("charset")
                || lower.contains("smil") || lower.contains("<") || lower.contains(">")) {
            return false;
        }
        if (lower.matches(".*\\bext[0-9]{3,}\\.?\\b.*")) {
            return false;
        }
        if (lower.matches("[0-9a-f]{12,}")) {
            return false;
        }
        return !(lower.endsWith(".txt") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp"));
    }

    private static boolean isSafeDisplayText(String value) {
        if (TextUtils.isEmpty(value) || value.length() > 500) {
            return false;
        }
        int lettersOrDigits = 0;
        int letters = 0;
        int punctuation = 0;
        int spaces = 0;
        int friendlyNonAscii = 0;
        int unsafeNonAscii = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 32 && c <= 126) {
                if (isAsciiLetterOrDigit(c)) {
                    lettersOrDigits++;
                    if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                        letters++;
                    }
                } else if (Character.isWhitespace(c)) {
                    spaces++;
                } else {
                    punctuation++;
                }
                continue;
            }
            if (Character.isWhitespace(c)) {
                spaces++;
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                lettersOrDigits++;
                if (Character.isLetter(c)) {
                    letters++;
                }
                continue;
            }
            if (Character.isHighSurrogate(c) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                friendlyNonAscii++;
                i++;
                continue;
            }
            if (isFriendlyNonAscii(c)) {
                friendlyNonAscii++;
            } else {
                unsafeNonAscii++;
            }
        }
        if (unsafeNonAscii > 0 || friendlyNonAscii > Math.max(4, lettersOrDigits)) {
            return false;
        }
        if (lettersOrDigits == 0) {
            return friendlyNonAscii > 0 || value.matches("[.!?,]{1,5}");
        }
        if (punctuation > lettersOrDigits + Math.max(1, spaces)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.US);
        return !looksLikeKnownMmsJunk(lower)
                && !looksLikeEncodedToken(value, letters, spaces)
                && !looksLikeMostlyRandomText(value, letters, punctuation, spaces)
                && !lower.contains("application/")
                && !lower.contains("image/")
                && !lower.contains("text/plain")
                && !lower.contains("charset")
                && !lower.contains("smil")
                && !lower.matches(".*[a-z]{1,3}[0-9a-f]{6,}.*");
    }

    private static boolean looksLikeKnownMmsJunk(String lower) {
        return lower.contains("/type=")
                || lower.contains("type=plmn")
                || lower.equals("plmn")
                || lower.equals("te")
                || lower.equals("application")
                || lower.equals("application.")
                || lower.equals("insert-address-token")
                || looksLikeSmilLayoutJunk(lower)
                || looksLikeLayoutDimensionJunk(lower)
                || lower.matches(".*\\bext[0-9]{3,}\\.?\\b.*")
                || lower.matches("[0-9a-f]{12,}");
    }

    private static boolean looksLikeSmilLayoutJunk(String lower) {
        if (lower.length() > 120) {
            return false;
        }
        String value = lower.trim();
        return value.matches("\\W*(smil|head|body|par|seq|region|ref|layout|image|text|video|audio)\\W*")
                || looksLikeSmilImageReference(value)
                || looksLikeRegionIdLabel(value)
                || looksLikeNamedTokenValue(value, "region", SMIL_REGION_TOKENS)
                || looksLikeNamedTokenValue(value, "regionid", SMIL_REGION_TOKENS)
                || looksLikeNamedTokenValue(value, "text", SMIL_TEXT_TOKENS)
                || looksLikeNamedTokenValue(value, "par", SMIL_PAR_TOKENS)
                || value.matches("\\W*src\\W+[^\\s]+\\.(jpg|jpeg|png|gif|webp|txt)\\W*")
                || looksLikeNamedTokenValue(value, "fit", SMIL_FIT_VALUES)
                || value.matches("\\W*(dur|begin|end)\\W+[0-9.]+(ms|s)?\\W*")
                || value.matches("\\W*(left|top)\\W+[0-9.]+(%|px)?\\W*");
    }

    private static boolean looksLikeSmilImageReference(String value) {
        return value.matches("\\W*img\\W+src\\W*")
                || value.matches("\\W*img\\W+src\\W+[^\\s]+\\.(jpg|jpeg|png|gif|webp)\\W*");
    }

    private static boolean looksLikeRegionIdLabel(String value) {
        return value.matches("\\W*region[\\W_]+id\\W*");
    }

    private static boolean looksLikeNamedTokenValue(String value, String name, String[] allowedValues) {
        if (!value.startsWith(name) || value.length() <= name.length()) {
            return false;
        }
        char afterName = value.charAt(name.length());
        if (Character.isLetterOrDigit(afterName)) {
            return false;
        }
        String rest = value.substring(name.length()).replaceFirst("^\\W+", "");
        for (String allowedValue : allowedValues) {
            if (rest.equals(allowedValue)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeLayoutDimensionJunk(String lower) {
        if (lower.length() > 96) {
            return false;
        }
        String value = lower.trim();
        for (String token : LAYOUT_DIMENSION_TOKENS) {
            if (looksLikeLayoutDimensionForToken(value, token)) {
                return true;
            }
        }
        return value.matches("\\W*(width|height)\\W*")
                || looksLikeStandaloneDimensionValue(value)
                || looksLikeDimensionNumberFragment(value);
    }

    private static boolean looksLikeLayoutDimensionForToken(String value, String token) {
        if (!value.startsWith(token) || value.length() <= token.length()) {
            return false;
        }
        char afterToken = value.charAt(token.length());
        if (Character.isLetterOrDigit(afterToken)) {
            return false;
        }
        String rest = value.substring(token.length()).replaceFirst("^\\W+", "");
        return looksLikeDimensionValue(rest, "width") || looksLikeDimensionValue(rest, "height");
    }

    private static boolean looksLikeDimensionValue(String value, String dimension) {
        if (!value.startsWith(dimension)) {
            return false;
        }
        if (value.length() == dimension.length()) {
            return true;
        }
        char next = value.charAt(dimension.length());
        if (next == '=' || next == ':') {
            return true;
        }
        String rest = value.substring(dimension.length()).trim();
        return rest.matches("^[=:].*");
    }

    private static boolean looksLikeStandaloneDimensionValue(String value) {
        return value.matches("\\W*(width|height)\\W+[0-9]{1,5}(%|px)?\\W*");
    }

    private static boolean looksLikeDimensionNumberFragment(String value) {
        return value.matches("\\W*[0-9]{1,5}(%|px)?\\W+(width|height)\\W*");
    }

    private static boolean isFriendlyNonAscii(char value) {
        return value == '\u2018'
                || value == '\u2019'
                || value == '\u201C'
                || value == '\u201D'
                || value == '\u2026'
                || value == '\u2013'
                || value == '\u2014';
    }

    private static boolean looksLikeEncodedToken(String value, int letters, int spaces) {
        if (value.length() < 12 || spaces > 0 || !value.matches("[A-Za-z0-9+/=_-]+")) {
            return false;
        }
        if (looksLikeCompactWords(value)) {
            return false;
        }
        int digits = 0;
        int tokenSymbols = 0;
        int upper = 0;
        int lower = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                digits++;
            } else if (c >= 'A' && c <= 'Z') {
                upper++;
            } else if (c >= 'a' && c <= 'z') {
                lower++;
            } else if (c == '+' || c == '/' || c == '=' || c == '_' || c == '-') {
                tokenSymbols++;
            }
        }
        return letters >= 6 && (digits >= 2 || tokenSymbols > 0) && upper > 0 && lower > 0;
    }

    private static boolean looksLikeCompactWords(String value) {
        int uppercaseWords = 0;
        int lowerRuns = 0;
        int currentLowerRun = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                uppercaseWords++;
                if (currentLowerRun >= 3) {
                    lowerRuns++;
                }
                currentLowerRun = 0;
            } else if (c >= 'a' && c <= 'z') {
                currentLowerRun++;
            } else {
                if (currentLowerRun >= 3) {
                    lowerRuns++;
                }
                currentLowerRun = 0;
            }
        }
        if (currentLowerRun >= 3) {
            lowerRuns++;
        }
        return lowerRuns >= 2 && uppercaseWords <= lowerRuns + 1;
    }

    private static boolean looksLikeMostlyRandomText(String value, int letters, int punctuation, int spaces) {
        if (value.length() < 8 || spaces > 0) {
            return false;
        }
        if (looksLikeNormalPunctuatedCaption(value)) {
            return false;
        }
        int upper = 0;
        int lower = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                upper++;
            } else if (c >= 'a' && c <= 'z') {
                lower++;
            }
        }
        boolean mixedCaseNoise = letters >= 4 && punctuation >= 2 && upper > 0 && lower > 0;
        boolean encodedLookingNoise = value.length() >= 10 && punctuation > 0 && upper > 0 && lower > 0
                && value.matches(".*[0-9].*");
        return mixedCaseNoise || encodedLookingNoise;
    }

    private static boolean looksLikeNormalPunctuatedCaption(String value) {
        return value.matches("[A-Za-z0-9 ]+[.!?]{1,5}")
                || value.matches("[A-Za-z0-9 ]+[,;:][A-Za-z0-9 .!?'-]*");
    }

    private static boolean hasEnoughReadableText(String value, boolean trustedTextPart) {
        int asciiLettersOrDigits = 0;
        int printableAscii = 0;
        int unsafeNonAscii = 0;
        int friendlyNonAscii = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 32 && c <= 126) {
                printableAscii++;
                if (isAsciiLetterOrDigit(c)) {
                    asciiLettersOrDigits++;
                }
            } else if (!Character.isWhitespace(c)) {
                if (Character.isLetterOrDigit(c)) {
                    asciiLettersOrDigits++;
                    continue;
                }
                if (Character.isHighSurrogate(c) && i + 1 < value.length()
                        && Character.isLowSurrogate(value.charAt(i + 1))) {
                    friendlyNonAscii++;
                    i++;
                    continue;
                }
                if (isFriendlyNonAscii(c)) {
                    friendlyNonAscii++;
                } else {
                    unsafeNonAscii++;
                }
            }
        }
        if (unsafeNonAscii > 0) {
            return false;
        }
        if (asciiLettersOrDigits > 0 || friendlyNonAscii > 0) {
            return true;
        }
        return trustedTextPart && printableAscii >= 2;
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9');
    }

    private static String decodeQuotedPrintable(String value) {
        if (TextUtils.isEmpty(value) || !hasQuotedPrintableEscape(value)) {
            return value;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '=' && i + 2 < value.length()) {
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    output.write((high << 4) | low);
                    i += 2;
                    continue;
                }
            }
            String text = String.valueOf(c);
            if (Character.isHighSurrogate(c) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                text = value.substring(i, i + 2);
                i++;
            }
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            output.write(bytes, 0, bytes.length);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean hasQuotedPrintableEscape(String value) {
        for (int i = 0; i + 2 < value.length(); i++) {
            if (value.charAt(i) == '='
                    && Character.digit(value.charAt(i + 1), 16) >= 0
                    && Character.digit(value.charAt(i + 2), 16) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String bestTextCandidate(List<TextCandidate> candidates) {
        String best = "";
        int bestScore = -1;
        for (TextCandidate candidate : candidates) {
            String cleaned = cleanDisplayText(candidate.value);
            if (TextUtils.isEmpty(cleaned)) {
                continue;
            }
            int score = textScore(cleaned) + (candidate.trustedTextPart ? 20 : 0);
            if (score >= bestScore) {
                best = cleaned;
                bestScore = score;
            }
        }
        return best;
    }

    private static int textScore(String value) {
        int score = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                score += 3;
            } else if (Character.isWhitespace(c)) {
                score += 1;
            } else {
                score -= 1;
            }
        }
        return score;
    }

    private static int firstMediaIndex(byte[] pdu) {
        return nextMediaIndex(pdu, 0);
    }

    private static int nextMediaIndex(byte[] pdu, int from) {
        int jpeg = indexOf(pdu, new byte[] { (byte) 0xFF, (byte) 0xD8 }, from);
        int png = indexOf(pdu, new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 }, from);
        int webp = indexOf(pdu, new byte[] { 0x52, 0x49, 0x46, 0x46 }, from);
        int best = pdu.length;
        if (jpeg >= 0) {
            best = Math.min(best, jpeg);
        }
        if (png >= 0) {
            best = Math.min(best, png);
        }
        if (webp >= 0) {
            best = Math.min(best, webp);
        }
        return best;
    }

    private static int firstMediaEndIndex(byte[] pdu) {
        int mediaStart = firstMediaIndex(pdu);
        if (mediaStart >= pdu.length) {
            return -1;
        }
        if (startsWith(pdu, mediaStart, pdu.length, new byte[] { (byte) 0xFF, (byte) 0xD8 })) {
            int end = indexOf(pdu, new byte[] { (byte) 0xFF, (byte) 0xD9 }, mediaStart + 2);
            return end >= 0 ? end + 2 : -1;
        }
        if (startsWith(pdu, mediaStart, pdu.length, new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 })) {
            int end = indexOf(pdu, new byte[] { 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82 }, mediaStart + 4);
            return end >= 0 ? end + 8 : -1;
        }
        if (startsWith(pdu, mediaStart, pdu.length, new byte[] { 0x52, 0x49, 0x46, 0x46 })
                && mediaStart + 12 < pdu.length
                && matches(pdu, new byte[] { 0x57, 0x45, 0x42, 0x50 }, mediaStart + 8)) {
            int size = littleEndianInt(pdu, mediaStart + 4) + 8;
            return size > 12 && mediaStart + size <= pdu.length ? mediaStart + size : -1;
        }
        return -1;
    }

    private static int findMmsHeaderStart(byte[] pdu) {
        if (pdu == null) {
            return -1;
        }
        for (int i = 0; i < pdu.length - 1; i++) {
            if ((pdu[i] & 0xFF) == HEADER_MESSAGE_TYPE
                    && (pdu[i + 1] & 0xFF) == MESSAGE_TYPE_NOTIFICATION_IND) {
                return i;
            }
        }
        for (int i = 0; i < pdu.length - 1; i++) {
            if ((pdu[i] & 0xFF) == HEADER_MESSAGE_TYPE && (pdu[i + 1] & 0x80) != 0) {
                return i;
            }
        }
        return 0;
    }

    private static boolean hasNotificationHeader(byte[] pdu) {
        if (pdu == null) {
            return false;
        }
        for (int i = 0; i < pdu.length - 1; i++) {
            if ((pdu[i] & 0xFF) == HEADER_MESSAGE_TYPE
                    && (pdu[i + 1] & 0xFF) == MESSAGE_TYPE_NOTIFICATION_IND) {
                return true;
            }
        }
        return false;
    }

    private static String findFromHeader(byte[] pdu, int headerStart) {
        for (int i = Math.max(0, headerStart); i < pdu.length - 4; i++) {
            if ((pdu[i] & 0xFF) == HEADER_FROM) {
                String parsed = readEncodedStringValue(pdu, i + 1, Math.min(pdu.length, i + 120));
                if (!TextUtils.isEmpty(parsed)) {
                    return cleanAddress(parsed);
                }
                String candidate = printableRun(pdu, i + 1, Math.min(pdu.length, i + 80));
                for (String part : candidate.split("[^0-9+]+")) {
                    String digits = part.replaceAll("[^0-9]", "");
                    if (digits.length() >= 10 && digits.length() <= 15) {
                        return cleanAddress(part);
                    }
                }
            }
        }
        return "";
    }

    private static String findTextHeader(byte[] pdu, int header, int headerStart, boolean requireUrl) {
        for (int i = Math.max(0, headerStart); i < pdu.length - 2; i++) {
            if ((pdu[i] & 0xFF) != header) {
                continue;
            }
            String direct = readTextString(pdu, i + 1, pdu.length);
            if (isUsefulHeaderText(direct, requireUrl)) {
                return direct;
            }

            int length = pdu[i + 1] & 0xFF;
            if (length > 0 && length < 80 && i + 2 + length <= pdu.length) {
                String encoded = readEncodedStringValue(pdu, i + 2, i + 2 + length);
                if (isUsefulHeaderText(encoded, requireUrl)) {
                    return encoded;
                }
            }
        }
        return "";
    }

    private static boolean isUsefulHeaderText(String value, boolean requireUrl) {
        return requireUrl ? looksLikeUrl(value) : !TextUtils.isEmpty(value);
    }

    private static String readEncodedStringValue(byte[] data, int start, int end) {
        if (start >= end) {
            return "";
        }
        int first = data[start] & 0xFF;
        if (first == 0x81) {
            return "";
        }
        if (first <= 30 && start + 1 + first <= end) {
            int valueEnd = start + 1 + first;
            int index = start + 1;
            index = skipIntegerValue(data, index, valueEnd);
            return readTextString(data, index, valueEnd);
        }
        if (first == 31) {
            Uintvar length = readUintvar(data, start + 1, end);
            int valueEnd = length.next + length.value;
            if (length.next > start + 1 && valueEnd <= end) {
                int index = skipIntegerValue(data, length.next, valueEnd);
                return readTextString(data, index, valueEnd);
            }
        }
        return readTextString(data, start, end);
    }

    private static int skipIntegerValue(byte[] data, int start, int end) {
        if (start >= end) {
            return start;
        }
        int value = data[start] & 0xFF;
        if ((value & 0x80) != 0) {
            return start + 1;
        }
        if (value <= 30 && start + 1 + value <= end) {
            return start + 1 + value;
        }
        return start;
    }

    private static String readTextString(byte[] data, int start, int end) {
        if (start >= end) {
            return "";
        }
        int index = start;
        if ((data[index] & 0xFF) == 0x7F) {
            index++;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = index; i < end; i++) {
            int value = data[i] & 0xFF;
            if (value == 0) {
                break;
            }
            if (value >= 32) {
                output.write(value);
            } else if (output.size() > 0) {
                break;
            }
        }
        return cleanUrl(new String(output.toByteArray(), MMS_TEXT_CHARSET));
    }

    private static boolean looksLikeUrl(String value) {
        return !TextUtils.isEmpty(value)
                && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private static String cleanUrl(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String cleaned = value.trim();
        while (!cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) <= 32) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static String cleanAddress(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String cleaned = value.trim();
        int typeIndex = cleaned.indexOf("/TYPE=");
        if (typeIndex >= 0) {
            cleaned = cleaned.substring(0, typeIndex);
        }
        cleaned = cleaned.replace("\"", "");
        return cleaned.trim();
    }

    private static String printableRun(byte[] data, int start, int end) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = start; i < end; i++) {
            int value = data[i] & 0xFF;
            if (value == 0 && output.size() > 0) {
                break;
            }
            if (value >= 32 && value <= 126) {
                output.write(value);
            } else if (output.size() > 0) {
                output.write(' ');
            }
        }
        return new String(output.toByteArray(), StandardCharsets.US_ASCII);
    }

    private static String printableWindow(byte[] data, int start, int end) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = start; i < end; i++) {
            int value = data[i] & 0xFF;
            output.write(value >= 32 && value <= 126 ? value : ' ');
        }
        return new String(output.toByteArray(), StandardCharsets.US_ASCII);
    }

    private static String asciiView(byte[] data, int start) {
        byte[] ascii = new byte[Math.max(0, data.length - start)];
        for (int i = start; i < data.length; i++) {
            int value = data[i] & 0xFF;
            ascii[i - start] = (byte) (value >= 32 && value <= 126 ? value : ' ');
        }
        return new String(ascii, StandardCharsets.US_ASCII);
    }

    private static Uintvar readUintvar(byte[] data, int start, int end) {
        int value = 0;
        int index = start;
        while (index < end) {
            int next = data[index] & 0xFF;
            value = (value << 7) | (next & 0x7F);
            index++;
            if ((next & 0x80) == 0) {
                return new Uintvar(value, index);
            }
        }
        return new Uintvar(0, start);
    }

    private static byte[] sliceBetween(byte[] data, byte[] start, byte[] end, boolean includeEnd) {
        int startIndex = indexOf(data, start, 0);
        if (startIndex < 0) {
            return new byte[0];
        }
        int endIndex = indexOf(data, end, startIndex + start.length);
        if (endIndex < 0) {
            return new byte[0];
        }
        int length = endIndex - startIndex + (includeEnd ? end.length : 0);
        byte[] slice = new byte[length];
        System.arraycopy(data, startIndex, slice, 0, length);
        return slice;
    }

    private static byte[] copyRange(byte[] data, int start, int end) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(data.length, end);
        if (safeEnd <= safeStart) {
            return new byte[0];
        }
        byte[] slice = new byte[safeEnd - safeStart];
        System.arraycopy(data, safeStart, slice, 0, slice.length);
        return slice;
    }

    private static byte[] slicePng(byte[] data) {
        byte[] start = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 };
        byte[] end = new byte[] { 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82 };
        return sliceBetween(data, start, end, true);
    }

    private static byte[] sliceWebp(byte[] data) {
        byte[] riff = new byte[] { 0x52, 0x49, 0x46, 0x46 };
        byte[] webp = new byte[] { 0x57, 0x45, 0x42, 0x50 };
        int start = indexOf(data, riff, 0);
        while (start >= 0 && start + 12 < data.length) {
            if (matches(data, webp, start + 8)) {
                int size = littleEndianInt(data, start + 4) + 8;
                if (size > 12 && start + size <= data.length) {
                    byte[] slice = new byte[size];
                    System.arraycopy(data, start, slice, 0, size);
                    return slice;
                }
            }
            start = indexOf(data, riff, start + 1);
        }
        return new byte[0];
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            if (matches(data, pattern, i)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfAscii(byte[] data, String value, int from) {
        return indexOf(data, value.getBytes(StandardCharsets.US_ASCII), from);
    }

    private static int indexOfAsciiIgnoreCase(byte[] data, String value, int from) {
        return indexOfAsciiIgnoreCase(data, value, from, data.length);
    }

    private static int indexOfAsciiIgnoreCase(byte[] data, String value, int from, int end) {
        byte[] pattern = value.getBytes(StandardCharsets.US_ASCII);
        int last = Math.min(data.length, end) - pattern.length;
        for (int i = Math.max(0, from); i <= last; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (asciiLower(data[i + j]) != asciiLower(pattern[j])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static int asciiLower(byte value) {
        int next = value & 0xFF;
        return next >= 'A' && next <= 'Z' ? next + 32 : next;
    }

    private static boolean matches(byte[] data, byte[] pattern, int offset) {
        if (offset < 0 || offset + pattern.length > data.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, int start, int end, byte[] pattern) {
        return start >= 0 && start + pattern.length <= end && matches(data, pattern, start);
    }

    private static boolean hasByte(byte[] data, int value, int start, int end) {
        for (int i = Math.max(0, start); i < Math.min(data.length, end); i++) {
            if ((data[i] & 0xFF) == value) {
                return true;
            }
        }
        return false;
    }

    static final class NotificationInfo {
        final String downloadUrl;
        final String sender;
        final String transactionId;
        final boolean parsedHeaders;

        NotificationInfo(String downloadUrl, String sender, String transactionId, boolean parsedHeaders) {
            this.downloadUrl = downloadUrl;
            this.sender = sender;
            this.transactionId = transactionId;
            this.parsedHeaders = parsedHeaders;
        }
    }

    private static final class Uintvar {
        final int value;
        final int next;

        Uintvar(int value, int next) {
            this.value = value;
            this.next = next;
        }
    }

    private static final class MmsPart {
        final int headerStart;
        final int headerEnd;
        final int dataStart;
        final int dataEnd;
        final boolean textPart;
        final boolean smilPart;
        final boolean mediaPart;

        MmsPart(int headerStart, int headerEnd, int dataStart, int dataEnd, boolean textPart, boolean smilPart, boolean mediaPart) {
            this.headerStart = headerStart;
            this.headerEnd = headerEnd;
            this.dataStart = dataStart;
            this.dataEnd = dataEnd;
            this.textPart = textPart;
            this.smilPart = smilPart;
            this.mediaPart = mediaPart;
        }
    }

    private static final class TextCandidate {
        final String value;
        final boolean trustedTextPart;

        TextCandidate(String value, boolean trustedTextPart) {
            this.value = value;
            this.trustedTextPart = trustedTextPart;
        }
    }

    private static final class ContentTypeRange {
        final int start;
        final int end;
        final int next;

        ContentTypeRange(int start, int end) {
            this(start, end, end);
        }

        ContentTypeRange(int start, int end, int next) {
            this.start = start;
            this.end = end;
            this.next = next;
        }
    }

    private static final class MultipartParse {
        final List<MmsPart> parts;
        final int score;
        final boolean confirmedContentTypeHeader;
        final boolean hasTextPart;
        final boolean hasSmilPart;
        final boolean hasMediaPart;

        MultipartParse(List<MmsPart> parts, int score, boolean confirmedContentTypeHeader) {
            this.parts = parts;
            this.score = score;
            this.confirmedContentTypeHeader = confirmedContentTypeHeader;
            boolean text = false;
            boolean smil = false;
            boolean media = false;
            for (MmsPart part : parts) {
                text = text || part.textPart;
                smil = smil || part.smilPart;
                media = media || part.mediaPart;
            }
            this.hasTextPart = text;
            this.hasSmilPart = smil;
            this.hasMediaPart = media;
        }
    }
}
