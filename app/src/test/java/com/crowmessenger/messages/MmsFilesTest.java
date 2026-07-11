package com.crowmessenger.messages;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;


@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsFilesTest {
    @Test
    public void appFileDir_createsPrivateAppDirectory() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File directory = MmsFiles.appFileDir(context, "mms-test-dir");

        assertTrue(directory.exists());
        assertTrue(directory.isDirectory());
        assertTrue(directory.getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath()));
    }

    @Test
    public void deleteAppFile_removesOnlyFilesInsideRequestedDirectory() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File directory = MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR);
        File image = new File(directory, "picture.jpg");
        assertTrue(image.createNewFile());
        File outside = new File(context.getFilesDir(), "picture.jpg");
        assertTrue(outside.createNewFile());

        MmsFiles.deleteAppFile(context, MmsFiles.IMAGES_DIR, image.getAbsolutePath());
        MmsFiles.deleteAppFile(context, MmsFiles.IMAGES_DIR, outside.getAbsolutePath());

        assertFalse(image.exists());
        assertTrue(outside.exists());
    }

    @Test
    public void isAppFile_acceptsOnlyFilesInsideRequestedDirectory() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File directory = MmsFiles.appFileDir(context, MmsFiles.DOWNLOADS_DIR);
        File pending = new File(directory, "pending.pdu");
        File outside = new File(context.getFilesDir(), "pending.pdu");

        assertTrue(MmsFiles.isAppFile(context, MmsFiles.DOWNLOADS_DIR, pending));
        assertFalse(MmsFiles.isAppFile(context, MmsFiles.DOWNLOADS_DIR, outside));
    }

    @Test
    public void deleteAppFileUri_ignoresNonFileUris() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File directory = MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR);
        File image = new File(directory, "content-uri-picture.jpg");
        assertTrue(image.createNewFile());

        MmsFiles.deleteAppFileUri(context, MmsFiles.IMAGES_DIR, Uri.fromFile(image).toString());
        MmsFiles.deleteAppFileUri(context, MmsFiles.IMAGES_DIR, "content://example/picture.jpg");

        assertFalse(image.exists());
    }

    @Test
    public void cleanupStaleTemporaryFiles_removesOnlyOldTemporaryFiles() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File outgoingDir = MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR);
        File cameraDir = MmsFiles.appFileDir(context, MmsFiles.CAMERA_DIR);
        File imagesDir = MmsFiles.appFileDir(context, MmsFiles.IMAGES_DIR);
        File oldPdu = new File(outgoingDir, "stale-cleanup.pdu");
        File freshPdu = new File(outgoingDir, "fresh-cleanup.pdu");
        File oldCameraImage = new File(cameraDir, "stale-cleanup.jpg");
        File savedMessageImage = new File(imagesDir, "saved-history-cleanup.jpg");
        assertTrue(oldPdu.createNewFile());
        assertTrue(freshPdu.createNewFile());
        assertTrue(oldCameraImage.createNewFile());
        assertTrue(savedMessageImage.createNewFile());
        long old = System.currentTimeMillis() - 31L * 24L * 60L * 60L * 1000L;
        assertTrue(oldPdu.setLastModified(old));
        assertTrue(oldCameraImage.setLastModified(old));
        assertTrue(savedMessageImage.setLastModified(old));

        int deleted = MmsFiles.cleanupStaleTemporaryFiles(context);

        assertEquals(2, deleted);
        assertFalse(oldPdu.exists());
        assertFalse(oldCameraImage.exists());
        assertTrue(freshPdu.exists());
        assertTrue(savedMessageImage.exists());
        assertTrue(freshPdu.delete());
        assertTrue(savedMessageImage.delete());
    }

}
