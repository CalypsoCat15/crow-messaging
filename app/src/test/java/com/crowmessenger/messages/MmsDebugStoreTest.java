package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsDebugStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("mms_debug", Context.MODE_PRIVATE).edit().clear().commit();
        deleteChildren(MmsFiles.appFileDirPath(context, MmsFiles.NOTICES_DIR));
        deleteChildren(MmsFiles.appFileDirPath(context, MmsFiles.UNREADABLE_DIR));
    }

    @Test
    public void record_redactsDottedPhoneNumbers() {
        MmsDebugStore.record(context, "Starting MMS download for 555.123.4567");

        String last = MmsDebugStore.last(context);
        assertFalse(last.contains("555.123.4567"));
        assertTrue(last.contains("***4567"));
    }

    @Test
    public void record_keepsDiagnosticCountsVisible() {
        MmsDebugStore.record(context, "MMS extraction imageBytes=1234567, textLength=42");

        String last = MmsDebugStore.last(context);
        assertTrue(last.contains("imageBytes=1234567"));
        assertTrue(last.contains("textLength=42"));
    }

    @Test
    public void rawPduArchiving_isOffByDefaultAndCanBeEnabled() {
        assertFalse(MmsDebugStore.shouldArchiveRawPdus(context));

        MmsDebugStore.setArchiveRawPdus(context, true);
        assertTrue(MmsDebugStore.shouldArchiveRawPdus(context));

        MmsDebugStore.setArchiveRawPdus(context, false);
        assertFalse(MmsDebugStore.shouldArchiveRawPdus(context));
    }

    @Test
    public void trimArchivedPduFiles_keepsNewestFiveFilesPerArchiveFolder() throws Exception {
        File noticeDir = MmsFiles.appFileDirPath(context, MmsFiles.NOTICES_DIR);
        assertTrue(noticeDir.mkdirs() || noticeDir.exists());
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            File file = new File(noticeDir, "notice-" + i + ".pdu");
            Files.write(file.toPath(), new byte[] { (byte) i });
            assertTrue(file.setLastModified(now - ((7L - i) * 1000L)));
        }

        MmsDebugStore.trimArchivedPduFiles(context);

        File[] files = noticeDir.listFiles((dir, name) -> name.endsWith(".pdu"));
        assertEquals(5, files == null ? 0 : files.length);
        assertFalse(new File(noticeDir, "notice-0.pdu").exists());
        assertFalse(new File(noticeDir, "notice-1.pdu").exists());
        assertTrue(new File(noticeDir, "notice-6.pdu").exists());
    }

    @Test
    public void trimArchivedPduFiles_deletesOldArchivesEvenBelowCountLimit() throws Exception {
        File unreadableDir = MmsFiles.appFileDirPath(context, MmsFiles.UNREADABLE_DIR);
        assertTrue(unreadableDir.mkdirs() || unreadableDir.exists());
        File oldFile = new File(unreadableDir, "old.pdu");
        Files.write(oldFile.toPath(), new byte[] { 1 });
        assertTrue(oldFile.setLastModified(System.currentTimeMillis() - 4L * 24L * 60L * 60L * 1000L));
        File recentFile = new File(unreadableDir, "recent.pdu");
        Files.write(recentFile.toPath(), new byte[] { 2 });

        MmsDebugStore.trimArchivedPduFiles(context);

        assertFalse(oldFile.exists());
        assertTrue(recentFile.exists());
    }

    @Test
    public void trimArchivedPduFiles_keepsNonPduFiles() throws Exception {
        File noticeDir = MmsFiles.appFileDirPath(context, MmsFiles.NOTICES_DIR);
        assertTrue(noticeDir.mkdirs() || noticeDir.exists());
        File note = new File(noticeDir, "readme.txt");
        Files.write(note.toPath(), new byte[] { 1 });
        assertTrue(note.setLastModified(System.currentTimeMillis() - 4L * 24L * 60L * 60L * 1000L));

        MmsDebugStore.trimArchivedPduFiles(context);

        assertTrue(note.exists());
    }

    private void deleteChildren(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }
}
