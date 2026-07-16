package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsImageSenderTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void gifPayloadLimit_reservesRoomForMmsHeadersAndHonorsLowerCarrierLimits() {
        assertEquals(900 * 1024, MmsImageSender.gifPayloadLimitForConfiguredMax(0));
        assertEquals(440 * 1024, MmsImageSender.gifPayloadLimitForConfiguredMax(512 * 1024));
        assertEquals(952 * 1024, MmsImageSender.gifPayloadLimitForConfiguredMax(1024 * 1024));
    }

    @Test
    public void recipientsForAddress_usesSingleRecipientForOneToOneThread() throws Exception {
        assertEquals(
                Arrays.asList("5550102000"),
                MmsImageSender.recipientsForAddress("5550102000", new HashSet<>())
        );
    }

    @Test
    public void recipientsForAddress_usesRemoteGroupRecipients() throws Exception {
        String group = LocalMmsStore.conversationAddress("", Arrays.asList(
                "15550101000",
                "5550102000",
                "5550103000"
        ));

        List<String> recipients = MmsImageSender.recipientsForAddress(
                group,
                new HashSet<>(Arrays.asList("15550101000"))
        );

        assertEquals(Arrays.asList("5550102000", "5550103000"), recipients);
    }

    @Test
    public void recipientsForAddress_rejectsGroupWithTooFewRemoteRecipients() {
        String group = LocalMmsStore.conversationAddress("", Arrays.asList(
                "15550101000",
                "5550102000",
                "5550103000"
        ));

        assertThrows(
                SmsSender.SendException.class,
                () -> MmsImageSender.recipientsForAddress(
                        group,
                        new HashSet<>(Arrays.asList("15550101000", "5550102000"))
                )
        );
    }

    @Test
    public void sendAndRecord_durablySavesExactPictureBeforeCarrierCall() throws Exception {
        String address = "15551234567";
        File sourceImage = createSourceJpeg("durable-picture-source.jpg");
        boolean[] carrierCalled = { false };
        String[] sentId = { "" };
        String[] pduName = { "" };
        String[] copiedImageUri = { "" };

        long sentAt = MmsImageSender.sendAndRecord(
                context,
                address,
                List.of(address),
                "picture caption",
                Uri.fromFile(sourceImage),
                (sendContext, pduUri, sentIntent) -> {
                    carrierCalled[0] = true;
                    android.content.Intent callbackIntent = Shadows.shadowOf(sentIntent).getSavedIntent();
                    sentId[0] = callbackIntent.getStringExtra(MmsSentReceiver.EXTRA_LOCAL_MESSAGE_ID);
                    copiedImageUri[0] = callbackIntent.getStringExtra(MmsSentReceiver.EXTRA_IMAGE_URI);
                    pduName[0] = pduUri.getLastPathSegment();

                    List<ChatMessage> messages = LocalMmsStore.loadForAddress(sendContext, address);
                    assertEquals(1, messages.size());
                    assertEquals(sentId[0], messages.get(0).localStatusId);
                    assertEquals("picture caption", messages.get(0).body);
                    assertEquals(copiedImageUri[0], messages.get(0).imageUri);
                    assertTrue(sendContext.getSharedPreferences("local_mms", Context.MODE_PRIVATE)
                            .getStringSet("ids", Set.of())
                            .contains(sentId[0]));
                    assertTrue(new File(Uri.parse(copiedImageUri[0]).getPath()).exists());
                    assertTrue(new File(
                            MmsFiles.appFileDirPath(sendContext, MmsFiles.OUTGOING_DIR),
                            pduName[0]
                    ).exists());
                }
        );

        assertTrue(carrierCalled[0]);
        assertFalse(sentId[0].isEmpty());
        assertFalse(Uri.fromFile(sourceImage).toString().equals(copiedImageUri[0]));
        List<ChatMessage> messages = LocalMmsStore.loadForAddress(context, address);
        assertEquals(sentAt, messages.get(0).dateMillis);
        assertTrue(LocalMmsStore.rollbackSentMessage(context, sentId[0], address));
        MmsSentReceiver.deleteOutgoingPdu(context, pduName[0]);
        assertTrue(sourceImage.delete());
    }

    @Test
    public void sendAndRecord_synchronousCarrierExceptionRollsBackRowPduAndCopiedImage() throws Exception {
        String address = "15551234567";
        File sourceImage = createSourceJpeg("failed-picture-source.jpg");
        Set<String> pduNamesBefore = fileNames(MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR));
        Set<String> imageNamesBefore = fileNames(MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR));
        boolean[] sawSavedRow = { false };

        SmsSender.SendException exception = assertThrows(
                SmsSender.SendException.class,
                () -> MmsImageSender.sendAndRecord(
                        context,
                        address,
                        List.of(address),
                        "carrier failure",
                        Uri.fromFile(sourceImage),
                        (sendContext, pduUri, sentIntent) -> {
                            String id = Shadows.shadowOf(sentIntent)
                                    .getSavedIntent()
                                    .getStringExtra(MmsSentReceiver.EXTRA_LOCAL_MESSAGE_ID);
                            List<ChatMessage> messages = LocalMmsStore.loadForAddress(sendContext, address);
                            sawSavedRow[0] = messages.size() == 1
                                    && id.equals(messages.get(0).localStatusId);
                            throw new SecurityException("carrier rejected picture");
                        }
                )
        );

        assertEquals("carrier rejected picture", exception.getMessage());
        assertTrue(sawSavedRow[0]);
        assertTrue(LocalMmsStore.loadForAddress(context, address).isEmpty());
        assertEquals(pduNamesBefore, fileNames(MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR)));
        assertEquals(imageNamesBefore, fileNames(MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR)));
        assertTrue(sourceImage.exists());
        assertTrue(sourceImage.delete());
    }

    @Test
    public void sendAndRecord_preservesGifAnimationBytesAndContentType() throws Exception {
        String address = "15551234567";
        byte[] gif = new byte[] { 'G', 'I', 'F', '8', '9', 'a', 1, 2, 3, 4, 5 };
        File sourceImage = new File(context.getCacheDir(), "animated-source.gif");
        try (FileOutputStream output = new FileOutputStream(sourceImage)) {
            output.write(gif);
        }
        String[] localImageUri = { "" };
        String[] pduName = { "" };

        MmsImageSender.sendAndRecord(
                context,
                address,
                List.of(address),
                "",
                Uri.fromFile(sourceImage),
                (sendContext, pduUri, sentIntent) -> {
                    android.content.Intent callbackIntent = Shadows.shadowOf(sentIntent).getSavedIntent();
                    localImageUri[0] = callbackIntent.getStringExtra(MmsSentReceiver.EXTRA_IMAGE_URI);
                    pduName[0] = pduUri.getLastPathSegment();
                }
        );

        assertTrue(localImageUri[0].endsWith(".gif"));
        assertTrue(Arrays.equals(
                gif,
                java.nio.file.Files.readAllBytes(new File(Uri.parse(localImageUri[0]).getPath()).toPath())
        ));
        byte[] pdu = java.nio.file.Files.readAllBytes(
                new File(MmsFiles.appFileDirPath(context, MmsFiles.OUTGOING_DIR), pduName[0]).toPath()
        );
        assertTrue(new String(pdu, java.nio.charset.StandardCharsets.ISO_8859_1).contains("image/gif"));

        ChatMessage message = LocalMmsStore.loadForAddress(context, address).get(0);
        assertTrue(LocalMmsStore.rollbackSentMessage(context, message.localStatusId, address));
        MmsSentReceiver.deleteOutgoingPdu(context, pduName[0]);
        assertTrue(sourceImage.delete());
    }

    private File createSourceJpeg(String name) throws Exception {
        File file = new File(context.getCacheDir(), name);
        Bitmap bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(40, 210, 170));
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output));
        } finally {
            bitmap.recycle();
        }
        return file;
    }

    private static Set<String> fileNames(File directory) {
        Set<String> names = new HashSet<>();
        File[] files = directory.listFiles(File::isFile);
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        return names;
    }
}
