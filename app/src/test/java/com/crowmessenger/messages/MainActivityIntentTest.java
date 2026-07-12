package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityIntentTest {
    @Test
    public void composeIntent_readsSendToAddressAndBodyQuery() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+15551234567?body=On+my+way"));

        MainActivity.ComposeIntent request = MainActivity.composeIntent(intent);

        assertNotNull(request);
        assertEquals("+15551234567", request.address);
        assertEquals("On my way", request.body);
    }

    @Test
    public void composeIntent_decodesSendToAddress() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:%2B15551234567?body=On+my+way"));

        MainActivity.ComposeIntent request = MainActivity.composeIntent(intent);

        assertNotNull(request);
        assertEquals("+15551234567", request.address);
        assertEquals("On my way", request.body);
    }

    @Test
    public void composeIntent_readsSharedTextBody() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, " Hello ");

        MainActivity.ComposeIntent request = MainActivity.composeIntent(intent);

        assertNotNull(request);
        assertEquals("", request.address);
        assertEquals("Hello", request.body);
        assertNull(request.imageUri);
    }

    @Test
    public void composeIntent_readsSharedGalleryPictureAndCaption() {
        Uri picture = Uri.parse("content://gallery/picture/42");
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, picture)
                .putExtra(Intent.EXTRA_TEXT, " Caption ");

        MainActivity.ComposeIntent request = MainActivity.composeIntent(intent);

        assertNotNull(request);
        assertEquals("", request.address);
        assertEquals("Caption", request.body);
        assertEquals(picture, request.imageUri);
    }

    @Test
    public void composeIntent_readsSharedPictureFromClipData() {
        Uri picture = Uri.parse("content://gallery/picture/43");
        Intent intent = new Intent(Intent.ACTION_SEND).setType("image/png");
        intent.setClipData(android.content.ClipData.newUri(
                org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
                "picture",
                picture
        ));

        MainActivity.ComposeIntent request = MainActivity.composeIntent(intent);

        assertNotNull(request);
        assertEquals(picture, request.imageUri);
    }

    @Test
    public void composeIntent_readsSeveralSharedGalleryPictures() {
        Uri first = Uri.parse("content://gallery/picture/44");
        Uri second = Uri.parse("content://gallery/picture/45");
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("image/jpeg")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, new java.util.ArrayList<>(java.util.List.of(first, second)))
                .putExtra(Intent.EXTRA_TEXT, "Trip pictures");

        MainActivity.ComposeIntent request = MainActivity.composeIntent(intent);

        assertNotNull(request);
        assertEquals("Trip pictures", request.body);
        assertEquals(java.util.List.of(first, second), request.imageUris);
        assertEquals(first, request.imageUri);
    }

    @Test
    public void selectedImageUris_readsAndDeduplicatesPhotoPickerClipData() {
        Uri first = Uri.parse("content://gallery/picture/46");
        Uri second = Uri.parse("content://gallery/picture/47");
        ClipData clipData = ClipData.newRawUri("pictures", first);
        clipData.addItem(new ClipData.Item(second));
        clipData.addItem(new ClipData.Item(first));
        Intent result = new Intent();
        result.setClipData(clipData);

        assertEquals(java.util.List.of(first, second), MainActivity.selectedImageUris(result));
    }

    @Test
    public void composeIntent_ignoresUnsafeImageLinks() {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("image/jpeg")
                .putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        new java.util.ArrayList<>(java.util.List.of(
                                Uri.parse("file:///sdcard/private.jpg"),
                                Uri.parse("https://example.com/picture.jpg"),
                                Uri.parse("content://gallery/picture/48")
                        ))
                );

        MainActivity.ComposeIntent request = MainActivity.composeIntent(intent);

        assertNotNull(request);
        assertEquals(java.util.List.of(Uri.parse("content://gallery/picture/48")), request.imageUris);
    }

    @Test
    public void composeIntent_dropsMalformedExternalRecipientButKeepsBody() {
        Intent intent = new Intent(
                Intent.ACTION_SENDTO,
                Uri.parse("smsto:call-me-at-5551234567?body=Hello")
        );

        MainActivity.ComposeIntent request = MainActivity.composeIntent(intent);

        assertNotNull(request);
        assertEquals("", request.address);
        assertEquals("Hello", request.body);
    }

    @Test
    public void composeIntent_rejectsEmptyExternalSendIntent() {
        assertNull(MainActivity.composeIntent(new Intent(Intent.ACTION_SEND)));
        assertNull(MainActivity.composeIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("smsto:+15551234567"))));
    }

    @Test
    public void manifest_declaresSeparateTextShareAndSmsSendToFilters() throws Exception {
        Document manifest = loadManifest();
        NodeList filters = manifest.getElementsByTagName("intent-filter");
        boolean textShareFilter = false;
        boolean imageShareFilter = false;
        boolean multipleImageShareFilter = false;
        boolean smsSendToFilter = false;

        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            boolean hasSend = hasAndroidName(filter, "action", Intent.ACTION_SEND);
            boolean hasSendMultiple = hasAndroidName(filter, "action", Intent.ACTION_SEND_MULTIPLE);
            boolean hasSendTo = hasAndroidName(filter, "action", Intent.ACTION_SENDTO);
            boolean hasDefault = hasAndroidName(filter, "category", Intent.CATEGORY_DEFAULT);
            if (hasSend && !hasSendTo && hasDefault && hasAndroidAttribute(filter, "data", "mimeType", "text/plain")) {
                textShareFilter = true;
            }
            if (hasSend && !hasSendTo && hasDefault && hasAndroidAttribute(filter, "data", "mimeType", "image/*")) {
                imageShareFilter = true;
            }
            if (hasSendMultiple && !hasSendTo && hasDefault && hasAndroidAttribute(filter, "data", "mimeType", "image/*")) {
                multipleImageShareFilter = true;
            }
            if (hasSendTo && !hasSend && hasDefault && hasAndroidAttribute(filter, "data", "scheme", "smsto")) {
                smsSendToFilter = true;
            }
        }

        assertTrue(textShareFilter);
        assertTrue(imageShareFilter);
        assertTrue(multipleImageShareFilter);
        assertTrue(smsSendToFilter);
    }

    @Test
    public void fileProviderPaths_allowSharingStoredMessageImages() throws Exception {
        File pathsFile = firstExistingFile(
                new File("src/main/res/xml/camera_file_paths.xml"),
                new File("app/src/main/res/xml/camera_file_paths.xml")
        );
        Document paths = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pathsFile);
        NodeList filePaths = paths.getElementsByTagName("files-path");
        boolean messageImagesAllowed = false;
        for (int index = 0; index < filePaths.getLength(); index++) {
            Element path = (Element) filePaths.item(index);
            if ("mms-images/".equals(path.getAttribute("path"))) {
                messageImagesAllowed = true;
            }
        }

        assertTrue(messageImagesAllowed);
    }

    @Test
    public void wasAnyPermissionGranted_detectsSmsPermissionWithoutContacts() {
        String[] permissions = new String[] {
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS
        };
        int[] grantResults = new int[] {
                PackageManager.PERMISSION_DENIED,
                PackageManager.PERMISSION_GRANTED
        };

        assertFalse(MainActivity.wasPermissionGranted(permissions, grantResults, Manifest.permission.READ_CONTACTS));
        assertTrue(MainActivity.wasAnyPermissionGranted(
                permissions,
                grantResults,
                new String[] { Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS }
        ));
    }

    @Test
    public void shouldSettleComposer_tracksKeyboardAndComposerLayoutChanges() {
        assertTrue(MainActivity.shouldSettleComposer(false, true, false, false));
        assertTrue(MainActivity.shouldSettleComposer(true, true, true, true));
        assertTrue(MainActivity.shouldSettleComposer(true, false, true, false));
        assertFalse(MainActivity.shouldSettleComposer(true, true, false, true));
        assertFalse(MainActivity.shouldSettleComposer(false, false, false, false));
    }

    @Test
    public void removeConversationRows_deduplicatesEquivalentAddressesAndKeepsNewestDetails() {
        java.util.ArrayList<Conversation> rows = new java.util.ArrayList<>();
        rows.add(new Conversation("1", "15551234567", "Older", "", "old", 1000L, 1));
        rows.add(new Conversation("2", "15557654321", "Other", "", "other", 1500L, 0));
        rows.add(new Conversation("1", "+1 (555) 123-4567", "Newer", "", "new", 2000L, 2));

        Conversation previous = MainActivity.removeConversationRows(rows, "+1 555 123 4567");

        assertNotNull(previous);
        assertEquals("Newer", previous.name);
        assertEquals(2000L, previous.dateMillis);
        assertEquals(1, rows.size());
        assertEquals("15557654321", rows.get(0).address);
    }

    @Test
    public void keyboardScrollBottomPadding_reservesTranslatedComposerOverlay() {
        assertEquals(612, MainActivity.keyboardScrollBottomPadding(true, 600, 12));
        assertEquals(12, MainActivity.keyboardScrollBottomPadding(true, -10, 12));
        assertEquals(0, MainActivity.keyboardScrollBottomPadding(false, 600, 12));
    }

    @Test
    public void composerBottomPadding_removesGapOnlyWhileKeyboardIsOpen() {
        assertEquals(0, MainActivity.composerBottomPaddingDp(true));
        assertEquals(12, MainActivity.composerBottomPaddingDp(false));
    }

    @Test
    public void keyboardComposerLift_usesActualSystemInsetWithoutGap() {
        assertEquals(600, MainActivity.keyboardComposerLift(648, 48));
        assertEquals(648, MainActivity.keyboardComposerLift(648, 0));
        assertEquals(0, MainActivity.keyboardComposerLift(40, 48));
        assertEquals(648, MainActivity.keyboardComposerLift(648, -10));
    }

    @Test
    public void initialResume_doesNotSkipPendingIncomingMessageRefresh() {
        assertTrue(MainActivity.shouldSkipInitialRefresh(true, false));
        assertFalse(MainActivity.shouldSkipInitialRefresh(true, true));
        assertFalse(MainActivity.shouldSkipInitialRefresh(false, false));
    }

    @Test
    public void scaledImageHeight_preservesAspectRatioWithinMessageBounds() {
        assertEquals(300, MainActivity.scaledImageHeight(300, 160, 360, 1000, 1000));
        assertEquals(160, MainActivity.scaledImageHeight(300, 160, 360, 2000, 500));
        assertEquals(360, MainActivity.scaledImageHeight(300, 160, 360, 500, 2000));
        assertEquals(300, MainActivity.scaledImageHeight(300, 160, 360, 0, 0));
    }

    @Test
    public void searchExcerpt_keepsLateMatchVisibleInLongPreview() {
        String message = "Their primary job is maintaining the ranch, venue, cabins, food, and every other detail, "
                + "which is enough to keep anyone busy during the summer season.";

        String excerpt = MainActivity.searchExcerpt(message, "busy", 72);

        assertTrue(excerpt.toLowerCase(java.util.Locale.US).contains("busy"));
        assertTrue(excerpt.startsWith("..."));
        assertTrue(excerpt.length() <= 78);
    }

    @Test
    public void searchExcerpt_keepsShortPreviewUnchanged() {
        assertEquals("I am busy today", MainActivity.searchExcerpt("I am busy today", "busy", 72));
        assertEquals("Newest unrelated message", MainActivity.searchExcerpt("Newest unrelated message", "busy", 72));
    }

    @Test
    public void inboxTextMatchCarriesSearchIntoOpenedThread() {
        Conversation result = new Conversation("1", "15551234567", "Daddy", "K good. Busy busy.", 1000L, 0);

        assertEquals("busy", MainActivity.threadSearchForInboxResult(result, " busy ", ""));
    }

    @Test
    public void inboxNameNumberAndDraftMatchesOpenThreadNormally() {
        Conversation nameMatch = new Conversation("1", "15551234567", "Busy Bee", "Unrelated", 1000L, 0);
        Conversation numberMatch = new Conversation("2", "15557654321", "Dad", "15557654321", 1000L, 0);
        Conversation draftMatch = new Conversation("3", "15559876543", "Mom", "Busy tomorrow", 1000L, 0);

        assertEquals("", MainActivity.threadSearchForInboxResult(nameMatch, "busy", ""));
        assertEquals("", MainActivity.threadSearchForInboxResult(numberMatch, "6543", ""));
        assertEquals("", MainActivity.threadSearchForInboxResult(draftMatch, "busy", "Busy tomorrow"));
    }

    @Test
    public void keyboardContentBottomPadding_compactsOnlyWhileKeyboardIsVisible() {
        assertEquals(6, MainActivity.keyboardContentBottomPadding(true, 20, 6));
        assertEquals(20, MainActivity.keyboardContentBottomPadding(false, 20, 6));
        assertEquals(0, MainActivity.keyboardContentBottomPadding(true, 0, 6));
        assertEquals(0, MainActivity.keyboardContentBottomPadding(true, -4, 6));
    }

    @Test
    public void screenCacheKeys_keepScreensAndSearchesSeparate() {
        assertEquals("inbox|", MainActivity.inboxCacheKey(false, null));
        assertEquals("inbox|jordan", MainActivity.inboxCacheKey(false, " Jordan "));
        assertEquals("blocked|jordan", MainActivity.inboxCacheKey(true, "JORDAN"));
        assertEquals("inbox|+15551234", MainActivity.threadCacheKey(" +15551234 ", false));
        assertEquals("blocked|+15551234", MainActivity.threadCacheKey("+15551234", true));
    }

    @Test
    public void cachedRows_skipOnlyTrulyUnchangedContent() {
        Conversation conversation = new Conversation("4", "+15551234", "Jordan", "Hello", 100L, 0);
        Conversation sameConversation = new Conversation("4", "+15551234", "Jordan", "Hello", 100L, 0);
        Conversation unreadConversation = new Conversation("4", "+15551234", "Jordan", "Hello", 100L, 1);
        assertTrue(MainActivity.sameConversationRows(
                java.util.List.of(conversation),
                java.util.List.of(sameConversation)
        ));
        assertFalse(MainActivity.sameConversationRows(
                java.util.List.of(conversation),
                java.util.List.of(unreadConversation)
        ));

        ChatMessage message = new ChatMessage("Hello", "", "", "Sent", "", 100L, true);
        ChatMessage sameMessage = new ChatMessage("Hello", "", "", "Sent", "", 100L, true);
        ChatMessage failedMessage = new ChatMessage("Hello", "", "", "Failed", "", 100L, true);
        assertTrue(MainActivity.sameMessageRows(
                java.util.List.of(message),
                java.util.List.of(sameMessage)
        ));
        assertFalse(MainActivity.sameMessageRows(
                java.util.List.of(message),
                java.util.List.of(failedMessage)
        ));
    }

    @Test
    public void rowsWithConversationRead_clearsOnlyMatchingUnreadConversation() {
        java.util.List<Conversation> rows = new java.util.ArrayList<>();
        rows.add(new Conversation("1", "+1 (555) 123-4567", "Fabletics", "Sale", 2000L, 3));
        rows.add(new Conversation("2", "15557654321", "Dave", "Hello", 1000L, 2));

        java.util.List<Conversation> updated = MainActivity.rowsWithConversationRead(rows, "15551234567");

        assertEquals(0, updated.get(0).unreadCount);
        assertEquals(2, updated.get(1).unreadCount);
        assertEquals(3, rows.get(0).unreadCount);
    }

    @Test
    public void readOverrideChangesCachedRowsEvenWhenAllOtherContentMatches() {
        java.util.List<Conversation> unread = java.util.List.of(
                new Conversation("1", "31354", "Fabletics", "Sale", 2000L, 1)
        );
        java.util.List<Conversation> read = MainActivity.rowsWithConversationRead(unread, "31354");

        assertFalse(MainActivity.sameConversationRows(unread, read));
        assertEquals(0, read.get(0).unreadCount);
    }

    @Test
    public void localReadOverride_survivesReloadUntilANewMessageArrives() {
        java.util.Set<String> locallyRead = new java.util.HashSet<>();
        locallyRead.add("31354");
        java.util.List<Conversation> reloaded = java.util.List.of(
                new Conversation("1", "31354", "Fabletics", "Sale", 2000L, 1)
        );

        assertEquals(0, MainActivity.rowsWithConversationsRead(reloaded, locallyRead).get(0).unreadCount);
        assertTrue(MainActivity.removeMatchingAddress(locallyRead, "31354"));
        assertEquals(1, MainActivity.rowsWithConversationsRead(reloaded, locallyRead).get(0).unreadCount);
    }

    @Test
    public void startupMaintenance_allowsOnlyOneWorkerAtATime() {
        assertTrue(MainActivity.beginStartupMaintenance());
        try {
            assertFalse(MainActivity.beginStartupMaintenance());
        } finally {
            MainActivity.finishStartupMaintenance();
        }

        assertTrue(MainActivity.beginStartupMaintenance());
        MainActivity.finishStartupMaintenance();
    }

    @Test
    public void initialThreadRows_keepsNewestMessagesForFirstFrame() {
        java.util.ArrayList<ChatMessage> messages = new java.util.ArrayList<>();
        for (int index = 0; index < 25; index++) {
            messages.add(new ChatMessage("message-" + index, index, false));
        }

        java.util.List<ChatMessage> initial = MainActivity.initialThreadRows(messages, 18);

        assertEquals(18, initial.size());
        assertEquals("message-7", initial.get(0).body);
        assertEquals("message-24", initial.get(17).body);
    }

    private static Document loadManifest() throws Exception {
        File manifest = firstExistingFile(
                new File("src/main/AndroidManifest.xml"),
                new File("app/src/main/AndroidManifest.xml")
        );
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(manifest);
    }

    private static File firstExistingFile(File... files) {
        for (File file : files) {
            if (file.isFile()) {
                return file;
            }
        }
        Assert.fail("AndroidManifest.xml was not found.");
        return files[0];
    }

    private static boolean hasAndroidName(Element parent, String childTag, String value) {
        return hasAndroidAttribute(parent, childTag, "name", value);
    }

    private static boolean hasAndroidAttribute(Element parent, String childTag, String attribute, String value) {
        NodeList children = parent.getElementsByTagName(childTag);
        for (int i = 0; i < children.getLength(); i++) {
            Element child = (Element) children.item(i);
            if (value.equals(child.getAttributeNS("http://schemas.android.com/apk/res/android", attribute))) {
                return true;
            }
        }
        return false;
    }
}
