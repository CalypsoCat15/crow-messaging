package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LocalMmsStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        SmsStore.clearContactCaches();
        FakeContactsProvider.clear();
    }

    @Test
    public void savePending_rejectsMissingIdOrPath() {
        assertFalse(LocalMmsStore.savePending(context, "", "15551234567", "/tmp/message.pdu"));
        assertFalse(LocalMmsStore.savePending(context, "download-id", "15551234567", ""));

        assertEquals("", LocalMmsStore.pending(context, "").pduPath);
        assertTrue(LocalMmsStore.pendingIds(context).isEmpty());
    }

    @Test
    public void savePending_storesRetryMetadata() {
        assertTrue(LocalMmsStore.savePending(
                context,
                "download-id",
                "15551234567",
                "/tmp/message.pdu",
                "http://mms.example/message",
                4
        ));

        LocalMmsStore.Pending pending = LocalMmsStore.pending(context, "download-id");
        assertEquals("15551234567", pending.address);
        assertEquals("/tmp/message.pdu", pending.pduPath);
        assertEquals("http://mms.example/message", pending.downloadUrl);
        assertEquals(4, pending.subscriptionId);
        assertEquals(0, pending.retryCount);

        assertEquals(1, LocalMmsStore.incrementPendingRetry(context, "download-id"));
        assertEquals(1, LocalMmsStore.pending(context, "download-id").retryCount);

        LocalMmsStore.clearPending(context, "download-id");
        assertEquals("", LocalMmsStore.pending(context, "download-id").downloadUrl);
    }

    @Test
    public void clearPendingForAddress_removesMatchingPendingDownloadAndFile() throws Exception {
        File downloadDir = MmsFiles.appFileDir(context, MmsFiles.DOWNLOADS_DIR);
        File pduFile = new File(downloadDir, "download-id.pdu");
        assertTrue(pduFile.createNewFile());
        assertTrue(LocalMmsStore.savePending(context, "download-id", "15551234567", pduFile.getAbsolutePath()));
        assertTrue(LocalMmsStore.savePending(context, "other-id", "15557654321", "/tmp/other.pdu"));

        assertEquals(1, LocalMmsStore.clearPendingForAddress(context, "+1 (555) 123-4567"));

        assertEquals("", LocalMmsStore.pending(context, "download-id").pduPath);
        assertFalse(pduFile.exists());
        assertEquals("/tmp/other.pdu", LocalMmsStore.pending(context, "other-id").pduPath);
    }

    @Test
    public void oldMessagesWithoutReadFlagDoNotBecomeUnread() {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        String id = "old-message";
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("address_" + id, "15551234567")
                .putString("body_" + id, "Picture message")
                .putLong("date_" + id, 1000L)
                .commit();

        List<Conversation> conversations = LocalMmsStore.loadConversations(context, false, "");

        assertEquals(1, conversations.size());
        assertEquals(0, conversations.get(0).unreadCount);
    }

    @Test
    public void newMessagesAreUnreadUntilOpened() {
        LocalMmsStore.saveNotice(context, "15551234567", "Picture message", 1000L);

        List<Conversation> unread = LocalMmsStore.loadConversations(context, false, "");
        assertEquals(1, unread.size());
        assertEquals(1, unread.get(0).unreadCount);

        LocalMmsStore.markAddressRead(context, "15551234567");

        List<Conversation> read = LocalMmsStore.loadConversations(context, false, "");
        assertEquals(1, read.size());
        assertEquals(0, read.get(0).unreadCount);
    }

    @Test
    public void sentImagesLoadAsOutgoingAndRead() {
        LocalMmsStore.saveSentImage(context, "15551234567", "hello", "file:///tmp/sent.jpg", 1000L);

        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        List<Conversation> conversations = LocalMmsStore.loadConversations(context, false, "");

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).outgoing);
        assertEquals("hello", messages.get(0).body);
        assertEquals(1, conversations.size());
        assertEquals(0, conversations.get(0).unreadCount);
    }

    @Test
    public void sentImageWithExplicitId_tracksExactFailureWhenImageUrisMatch() {
        String imageUri = "file:///tmp/shared-sent-image.jpg";
        assertTrue(LocalMmsStore.saveSentImage(
                context,
                "first-picture-id",
                "15551234567",
                "first",
                imageUri,
                1000L
        ));
        assertTrue(LocalMmsStore.saveSentImage(
                context,
                "second-picture-id",
                "15551234567",
                "second",
                imageUri,
                2000L
        ));

        assertTrue(LocalMmsStore.markSentMessageFailed(
                context,
                "second-picture-id",
                "+1 (555) 123-4567"
        ));

        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals(2, messages.size());
        assertEquals("first-picture-id", messages.get(0).localStatusId);
        assertEquals("", messages.get(0).status);
        assertEquals("second-picture-id", messages.get(1).localStatusId);
        assertEquals(ChatMessage.STATUS_FAILED, messages.get(1).status);
    }

    @Test
    public void rollbackSentMessage_removesOnlyExactRowAndItsOwnedImage() throws Exception {
        File imageDirectory = MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR);
        File firstImage = new File(imageDirectory, "rollback-first.jpg");
        File secondImage = new File(imageDirectory, "rollback-second.jpg");
        Files.write(firstImage.toPath(), new byte[] { 1, 2, 3 });
        Files.write(secondImage.toPath(), new byte[] { 4, 5, 6 });
        assertTrue(LocalMmsStore.saveSentImage(
                context,
                "rollback-first-id",
                "15551234567",
                "first",
                Uri.fromFile(firstImage).toString(),
                1000L
        ));
        assertTrue(LocalMmsStore.saveSentImage(
                context,
                "rollback-second-id",
                "15551234567",
                "second",
                Uri.fromFile(secondImage).toString(),
                2000L
        ));

        assertTrue(LocalMmsStore.rollbackSentMessage(
                context,
                "rollback-first-id",
                "+1 (555) 123-4567"
        ));

        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals(1, messages.size());
        assertEquals("rollback-second-id", messages.get(0).localStatusId);
        assertFalse(firstImage.exists());
        assertTrue(secondImage.exists());
        assertFalse(LocalMmsStore.rollbackSentMessage(
                context,
                "rollback-second-id",
                "15550000000"
        ));
        assertTrue(secondImage.exists());

        assertTrue(LocalMmsStore.rollbackSentMessage(
                context,
                "rollback-second-id",
                "15551234567"
        ));
        assertFalse(secondImage.exists());
    }

    @Test
    public void markSentImageFailed_updatesOnlyMatchingOutgoingPicture() {
        LocalMmsStore.saveSentImage(context, "15551234567", "first", "file:///tmp/first.jpg", 1000L);
        LocalMmsStore.saveSentImage(context, "15551234567", "second", "file:///tmp/second.jpg", 2000L);
        LocalMmsStore.saveImage(context, "15551234567", "15551234567", "incoming", "file:///tmp/incoming.jpg", 3000L);

        assertTrue(LocalMmsStore.markSentImageFailed(
                context,
                "+1 (555) 123-4567",
                "file:///tmp/second.jpg"
        ));

        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals("", messages.get(0).status);
        assertEquals(ChatMessage.STATUS_FAILED, messages.get(1).status);
        assertEquals("", messages.get(2).status);
        assertFalse(LocalMmsStore.markSentImageFailed(
                context,
                "15551234567",
                "file:///tmp/missing.jpg"
        ));
    }

    @Test
    public void failedPictureCanBeLoadedForRetryAndRemoved() {
        LocalMmsStore.saveSentImage(
                context,
                "15551234567",
                "caption",
                "file:///tmp/retry-picture.jpg",
                1000L
        );
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals(1, messages.size());
        String id = messages.get(0).localStatusId;
        assertFalse(id.isEmpty());
        assertTrue(LocalMmsStore.markSentImageFailed(
                context,
                "15551234567",
                "file:///tmp/retry-picture.jpg"
        ));

        LocalMmsStore.RetryMessage retry = LocalMmsStore.failedMessageForRetry(context, id);

        assertNotNull(retry);
        assertEquals("15551234567", retry.address);
        assertEquals("caption", retry.body);
        assertTrue(retry.hasImage());
        assertTrue(LocalMmsStore.deleteFailedMessageById(context, id));
        assertTrue(LocalMmsStore.loadForAddress(context, "15551234567").isEmpty());
    }

    @Test
    public void sentGroupTextLoadsOutgoingAndTracksExactFailure() {
        String group = LocalMmsStore.outgoingGroupAddress(List.of("15551234567", "15557654321"));
        LocalMmsStore.saveSentText(context, "group-text-id", group, "image width", 1000L);

        LocalMmsStore.cleanupAttachmentNameMessages(context);
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, group);

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).outgoing);
        assertEquals("image width", messages.get(0).body);
        assertEquals("", messages.get(0).status);
        assertTrue(LocalMmsStore.markSentMessageFailed(context, "group-text-id", group));
        assertEquals(
                ChatMessage.STATUS_FAILED,
                LocalMmsStore.loadForAddress(context, group).get(0).status
        );
        assertFalse(LocalMmsStore.markSentMessageFailed(context, "missing-id", group));
    }

    @Test
    public void failedGroupTextCanBeLoadedForRetryAndRemoved() {
        String group = LocalMmsStore.outgoingGroupAddress(List.of("15551234567", "15557654321"));
        LocalMmsStore.saveSentText(context, "failed-group-text", group, "hello group", 1000L);
        assertTrue(LocalMmsStore.markSentMessageFailed(context, "failed-group-text", group));

        LocalMmsStore.RetryMessage retry = LocalMmsStore.failedMessageForRetry(context, "failed-group-text");

        assertNotNull(retry);
        assertEquals(group, retry.address);
        assertEquals("hello group", retry.body);
        assertFalse(retry.hasImage());
        assertTrue(LocalMmsStore.deleteFailedMessageById(context, "failed-group-text"));
        assertTrue(LocalMmsStore.loadForAddress(context, group).isEmpty());
    }

    @Test
    public void markAllReadClearsLocalUnreadCounts() {
        LocalMmsStore.saveNotice(context, "15551234567", "one", 1000L);
        LocalMmsStore.saveNotice(context, "15557654321", "two", 2000L);

        LocalMmsStore.markAllRead(context);

        List<Conversation> conversations = LocalMmsStore.loadConversations(context, false, "");
        assertEquals(2, conversations.size());
        assertEquals(0, conversations.get(0).unreadCount);
        assertEquals(0, conversations.get(1).unreadCount);
    }

    @Test
    public void groupMmsFromBlockedSenderMovesToBlockedInbox() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15551234567",
                "15557654321",
                "15559876543"
        ));
        Blocklist.block(context, "15551234567");
        LocalMmsStore.saveNotice(context, group, "15551234567", "Picture message", 1000L);

        assertEquals(0, LocalMmsStore.loadConversations(context, false, "").size());
        assertEquals(1, LocalMmsStore.loadConversations(context, true, "").size());
    }

    @Test
    public void groupMmsFromSpamMarkedSenderMovesToBlockedInbox() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15551234567",
                "15557654321",
                "15559876543"
        ));
        SpamFilter.markSpam(context, "15551234567");
        LocalMmsStore.saveNotice(context, group, "15551234567", "Picture message", 1000L);

        assertEquals(0, LocalMmsStore.loadConversations(context, false, "").size());
        assertEquals(1, LocalMmsStore.loadConversations(context, true, "").size());
    }

    @Test
    public void outgoingGroupAddress_createsGroupForTwoOrMoreRemoteRecipients() {
        assertEquals("", LocalMmsStore.outgoingGroupAddress(List.of("15551234567")));

        String group = LocalMmsStore.outgoingGroupAddress(List.of("15551234567", "15557654321"));

        assertTrue(LocalMmsStore.isGroupAddress(group));
        assertEquals(List.of("15551234567", "15557654321"), LocalMmsStore.participantsForAddress(group));
    }

    @Test
    public void groupParticipants_rejectSenderIdsAndKeepEmailDigitsSeparate() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "ALERT5551234",
                "5551234",
                "5559876",
                "person5551234@example.com"
        ));

        assertEquals(
                List.of("5551234", "5559876", "person5551234@example.com"),
                LocalMmsStore.participantsForAddress(group)
        );
    }

    @Test
    public void outgoingGroupAddress_reusesReceivedGroupWithOwnNumber() {
        String receivedGroup = LocalMmsStore.conversationAddress("", List.of(
                "15550000001",
                "15550000002",
                "15550000003"
        ));
        LocalMmsStore.saveNotice(context, receivedGroup, "Picture message", 1000L);

        String outgoingGroup = LocalMmsStore.outgoingGroupAddress(context, List.of(
                "15550000001",
                "15550000002"
        ));

        assertEquals(receivedGroup, outgoingGroup);
    }

    @Test
    public void receivedGroupReply_reusesOutgoingTwoRecipientGroup() {
        String outgoingGroup = LocalMmsStore.outgoingGroupAddress(List.of(
                "+1 555 000 0001",
                "5550000002"
        ));
        LocalMmsStore.saveSentImage(context, outgoingGroup, "sent", "file:///tmp/sent.jpg", 1000L);

        String receivedGroup = LocalMmsStore.bestConversationAddress(context, "", List.of(
                "5550000001",
                "+1 555 000 0002",
                "5550000003"
        ));

        assertEquals(outgoingGroup, receivedGroup);
    }

    @Test
    public void cleanupAttachmentNameMessages_mergesCountryCodeVariantOfSameGroup() {
        String outgoingGroup = LocalMmsStore.outgoingGroupAddress(List.of(
                "+1 555 000 0001",
                "5550000002"
        ));
        String incomingGroup = LocalMmsStore.conversationAddress("", List.of(
                "5550000001",
                "+1 555 000 0002",
                "5550000003"
        ));
        LocalMmsStore.saveSentImage(context, outgoingGroup, "sent", "file:///tmp/sent.jpg", 1000L);
        LocalMmsStore.saveNotice(context, incomingGroup, "5550000001", "reply", 2000L);

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, outgoingGroup);
        assertEquals(2, messages.size());
        assertEquals("sent", messages.get(0).body);
        assertEquals("reply", messages.get(1).body);
        assertEquals(1, LocalMmsStore.loadConversations(context, false, "").size());
    }

    @Test
    public void loadForAddress_honorsKeywordSpamView() {
        SpamFilter.addCustomKeywords(context, "free gift");
        LocalMmsStore.saveNotice(context, "15551234567", "See you soon", 1000L);
        LocalMmsStore.saveNotice(context, "15551234567", "Claim your free gift", 2000L);

        List<ChatMessage> normal = LocalMmsStore.loadForAddress(context, "15551234567", false);
        List<ChatMessage> blocked = LocalMmsStore.loadForAddress(context, "15551234567", true);

        assertEquals(1, normal.size());
        assertEquals("See you soon", normal.get(0).body);
        assertEquals(1, blocked.size());
        assertEquals("Claim your free gift", blocked.get(0).body);
    }

    @Test
    public void loadForAddress_allowsKeywordFromSavedContact() {
        ShadowContentResolver.registerProviderInternal("com.android.contacts", new FakeContactsProvider());
        FakeContactsProvider.add("15551234567", "Dad");
        SpamFilter.addCustomKeywords(context, "donate");
        LocalMmsStore.saveNotice(context, "15551234567", "Did you donate anything?", 1000L);

        List<ChatMessage> normal = LocalMmsStore.loadForAddress(context, "15551234567", false);
        List<ChatMessage> blocked = LocalMmsStore.loadForAddress(context, "15551234567", true);

        assertEquals(1, normal.size());
        assertTrue(blocked.isEmpty());
    }

    @Test
    public void loadForAddress_neverFiltersOutgoingKeywordMatch() {
        SpamFilter.addCustomKeywords(context, "donate");
        LocalMmsStore.saveSentImage(
                context,
                "15557654321",
                "I already donate there",
                "file:///tmp/donation.jpg",
                1000L
        );

        List<ChatMessage> normal = LocalMmsStore.loadForAddress(context, "15557654321", false);
        List<ChatMessage> blocked = LocalMmsStore.loadForAddress(context, "15557654321", true);

        assertEquals(1, normal.size());
        assertTrue(blocked.isEmpty());
    }

    @Test
    public void loadConversations_matchesIndividualLocalMmsByContactName() {
        ShadowContentResolver.registerProviderInternal("com.android.contacts", new FakeContactsProvider());
        FakeContactsProvider.add("15551234567", "Alex");
        LocalMmsStore.saveNotice(context, "15551234567", "Picture message", 1000L);

        List<Conversation> conversations = LocalMmsStore.loadConversations(context, false, "alex");

        assertEquals(1, conversations.size());
        assertEquals("Alex", conversations.get(0).name);
    }

    @Test
    public void cleanupAttachmentNameMessages_updatesLegacyAppNamePlaceholder() {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        String id = "legacy-unreadable";
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("address_" + id, "15551234567")
                .putString("body_" + id, "Message received, but Crow Messenger could not read it yet.")
                .putLong("date_" + id, 1000L)
                .commit();

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        assertEquals(
                LocalMmsStore.UNREADABLE_MESSAGE,
                prefs.getString("body_" + id, "")
        );
    }

    @Test
    public void replaceClosestUnreadableMessage_preservesRowAndReadState() {
        String address = "15551234567";
        long receivedAt = 10_000L;
        LocalMmsStore.saveNotice(
                context,
                address,
                address,
                LocalMmsStore.UNREADABLE_MESSAGE,
                receivedAt
        );
        LocalMmsStore.markAddressRead(context, address);

        assertTrue(LocalMmsStore.replaceClosestUnreadableMessage(
                context,
                address,
                address,
                "Recovered text",
                "",
                receivedAt + 500L
        ));

        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, address);
        assertEquals(1, messages.size());
        assertEquals("Recovered text", messages.get(0).body);
        assertEquals(receivedAt, messages.get(0).dateMillis);
        assertEquals(0, LocalMmsStore.loadConversations(context, false, "").get(0).unreadCount);
    }

    @Test
    public void replaceArchivedMedia_repairsMisidentifiedVideoAndDeletesBadImage() throws Exception {
        String archiveId = "carrier-video-repair";
        File mediaDir = MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR);
        File badImage = new File(mediaDir, archiveId + ".jpg");
        File video = new File(mediaDir, archiveId + ".mp4");
        Files.write(badImage.toPath(), new byte[] { 1, 2, 3 });
        Files.write(video.toPath(), new byte[] { 0, 0, 0, 8, 'f', 't', 'y', 'p' });
        LocalMmsStore.saveImage(
                context,
                "15551234567",
                "15551234567",
                "M%",
                Uri.fromFile(badImage).toString(),
                3000L
        );

        assertEquals(
                "15551234567",
                LocalMmsStore.replaceArchivedMedia(
                        context,
                        archiveId,
                        "",
                        Uri.fromFile(video).toString()
                )
        );

        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, "15551234567");
        assertEquals(1, messages.size());
        assertEquals(LocalMmsStore.VIDEO_MESSAGE, messages.get(0).body);
        assertEquals(Uri.fromFile(video).toString(), messages.get(0).imageUri);
        assertFalse(badImage.exists());
        assertTrue(video.exists());
    }

    @Test
    public void cleanupAttachmentNameMessages_removesMessagesWithoutAddress() {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        String id = "missing-address";
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("body_" + id, "Picture message")
                .putLong("date_" + id, 1000L)
                .commit();

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        assertFalse(prefs.getStringSet("ids", new HashSet<>()).contains(id));
        assertFalse(prefs.contains("body_" + id));
    }

    @Test
    public void cleanupAttachmentNameMessages_deletesOwnedImageForMalformedRecord() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        String id = "missing-address-image";
        File image = new File(MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR), id + ".jpg");
        assertTrue(image.createNewFile());
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("body_" + id, "Picture message")
                .putString("image_" + id, Uri.fromFile(image).toString())
                .putLong("date_" + id, 1000L)
                .commit();

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        assertFalse(prefs.getStringSet("ids", new HashSet<>()).contains(id));
        assertFalse(image.exists());
    }

    @Test
    public void cleanupAttachmentNameMessages_doesNotLeaveOrphanLegacyBody() {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        String id = "orphan-legacy";
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("body_" + id, "Message received, but Crow Messenger could not read it yet.")
                .putLong("date_" + id, 1000L)
                .commit();

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        assertFalse(prefs.getStringSet("ids", new HashSet<>()).contains(id));
        assertFalse(prefs.contains("body_" + id));
    }

    @Test
    public void cleanupAttachmentNameMessages_removesLayoutJunkWithoutImage() {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        String id = "text-width-junk";
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("address_" + id, "15551234567")
                .putString("body_" + id, "text\" width")
                .putLong("date_" + id, 1000L)
                .commit();

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        assertFalse(prefs.getStringSet("ids", new HashSet<>()).contains(id));
        assertFalse(prefs.contains("body_" + id));
    }

    @Test
    public void cleanupAttachmentNameMessages_keepsRealImageWidthCaption() {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        String id = "real-caption";
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("address_" + id, "15551234567")
                .putString("body_" + id, "The image width looks wrong.")
                .putString("image_" + id, "file:///tmp/picture.jpg")
                .putLong("date_" + id, 1000L)
                .commit();

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        assertTrue(prefs.getStringSet("ids", new HashSet<>()).contains(id));
        assertEquals("The image width looks wrong.", prefs.getString("body_" + id, ""));
    }

    @Test
    public void cleanupAttachmentNameMessages_replacesImgSrcCaptionOnImageMessage() {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        String id = "img-src-junk";
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("address_" + id, "15551234567")
                .putString("body_" + id, "img src")
                .putString("image_" + id, "file:///tmp/picture.jpg")
                .putLong("date_" + id, 1000L)
                .commit();

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        assertTrue(prefs.getStringSet("ids", new HashSet<>()).contains(id));
        assertEquals(LocalMmsStore.PICTURE_MESSAGE, prefs.getString("body_" + id, ""));
    }

    @Test
    public void cleanupAttachmentNameMessages_replacesTextRegionCaptionOnImageMessage() {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        String id = "text-region-junk";
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("address_" + id, "15551234567")
                .putString("body_" + id, "text region")
                .putString("image_" + id, "file:///tmp/picture.jpg")
                .putLong("date_" + id, 1000L)
                .commit();

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        assertTrue(prefs.getStringSet("ids", new HashSet<>()).contains(id));
        assertEquals(LocalMmsStore.PICTURE_MESSAGE, prefs.getString("body_" + id, ""));
    }

    @Test
    public void cleanupAttachmentNameMessages_recoversCaptionFromArchivedRawDownload() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("local_mms", Context.MODE_PRIVATE);
        File imageDir = MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR);
        File rawDir = MmsFiles.appFileDir(context, MmsFiles.RAW_DOWNLOADS_DIR);
        File imageFile = new File(imageDir, "recover-caption.jpg");
        assertTrue(imageFile.createNewFile());
        Files.write(new File(rawDir, "recover-caption.pdu").toPath(), captionOnlyPdu("Test"));

        String id = "recover-caption";
        prefs.edit()
                .putStringSet("ids", new HashSet<>(Collections.singletonList(id)))
                .putString("address_" + id, "15551234567")
                .putString("body_" + id, "text src")
                .putString("image_" + id, Uri.fromFile(imageFile).toString())
                .putLong("date_" + id, 1000L)
                .commit();

        LocalMmsStore.cleanupAttachmentNameMessages(context);

        assertTrue(prefs.getStringSet("ids", new HashSet<>()).contains(id));
        assertEquals("Test", prefs.getString("body_" + id, ""));
    }

    @Test
    public void bestConversationAddress_doesNotMergeDifferentSameSizeGroups() {
        String firstGroup = LocalMmsStore.conversationAddress("", List.of(
                "15550000001",
                "15550000002",
                "15550000003"
        ));
        LocalMmsStore.saveNotice(context, firstGroup, "Picture message", 1000L);

        String secondGroup = LocalMmsStore.bestConversationAddress(context, "", List.of(
                "15550000001",
                "15550000002",
                "15550000004"
        ));

        assertEquals(LocalMmsStore.conversationAddress("", List.of(
                "15550000001",
                "15550000002",
                "15550000004"
        )), secondGroup);
    }

    @Test
    public void bestConversationAddress_mergesWhenOnlyOneParticipantIsMissing() {
        String fullGroup = LocalMmsStore.conversationAddress("", List.of(
                "15550000001",
                "15550000002",
                "15550000003",
                "15550000004"
        ));
        LocalMmsStore.saveNotice(context, fullGroup, "Picture message", 1000L);

        String recoveredGroup = LocalMmsStore.bestConversationAddress(context, "", List.of(
                "15550000001",
                "15550000002",
                "15550000003"
        ));

        assertEquals(fullGroup, recoveredGroup);
    }

    @Test
    public void concurrentMessageSaves_keepEveryStoredMessageIndexed() throws Exception {
        int messageCount = 40;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<?>> saves = new ArrayList<>();
        for (int index = 0; index < messageCount; index++) {
            int messageIndex = index;
            saves.add(executor.submit(() -> LocalMmsStore.saveNotice(
                    context,
                    "15551234567",
                    "message-" + messageIndex,
                    1000L + messageIndex
            )));
        }
        for (Future<?> save : saves) {
            save.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertEquals(messageCount, LocalMmsStore.loadForAddress(context, "15551234567").size());
    }

    public static final class FakeContactsProvider extends ContentProvider {
        private static final Map<String, String> NAMES_BY_NUMBER = new HashMap<>();

        static void add(String number, String name) {
            NAMES_BY_NUMBER.put(number, name);
        }

        static void clear() {
            NAMES_BY_NUMBER.clear();
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            String number = uri == null ? "" : uri.getLastPathSegment();
            String name = NAMES_BY_NUMBER.get(number);
            String[] columns = projection == null
                    ? new String[] { ContactsContract.PhoneLookup.DISPLAY_NAME }
                    : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            if (name != null) {
                Object[] values = new Object[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    values[i] = ContactsContract.PhoneLookup.DISPLAY_NAME.equals(columns[i]) ? name : "";
                }
                cursor.addRow(values);
            }
            return cursor;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }
    }

    private static byte[] captionOnlyPdu(String text) {
        byte[] caption = text.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x84);
        output.write(0x01);
        output.write(0xA3);
        output.write(1);
        output.write(1);
        output.write(caption.length);
        output.write(0x83);
        output.write(caption, 0, caption.length);
        return output.toByteArray();
    }
}
