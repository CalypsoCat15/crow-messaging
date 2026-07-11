package com.crowmessenger.messages;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsFileProviderTest {
    private Context context;
    private MmsFileProvider provider;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE).edit().clear().commit();
        provider = Robolectric.buildContentProvider(MmsFileProvider.class)
                .create(MmsFileProvider.AUTHORITY)
                .get();
    }

    @Test
    public void openFile_rejectsFileWithoutPendingDownload() {
        Uri uri = Uri.parse("content://" + MmsFileProvider.AUTHORITY + "/missing.pdu");

        assertThrows(FileNotFoundException.class, () -> provider.openFile(uri, "w"));
    }

    @Test
    public void openFile_allowsRegisteredPendingDownloadFile() throws Exception {
        String id = "pending-test";
        File pduFile = new File(MmsFiles.appFileDirPath(context, MmsFiles.DOWNLOADS_DIR), id + ".pdu");
        LocalMmsStore.savePending(context, id, "15551234567", pduFile.getAbsolutePath());
        Uri uri = Uri.parse("content://" + MmsFileProvider.AUTHORITY + "/" + id + ".pdu");

        ParcelFileDescriptor descriptor = provider.openFile(uri, "w");

        assertNotNull(descriptor);
        descriptor.close();
    }

    @Test
    public void openFile_allowsExistingOutgoingPduForRead() throws Exception {
        File pduFile = new File(MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR), "outgoing-test.pdu");
        try (FileOutputStream output = new FileOutputStream(pduFile)) {
            output.write(new byte[] { 1, 2, 3 });
        }
        Uri uri = Uri.parse("content://" + MmsFileProvider.AUTHORITY + "/" + pduFile.getName());

        ParcelFileDescriptor descriptor = provider.openFile(uri, "r");

        assertNotNull(descriptor);
        descriptor.close();
    }

    @Test
    public void openFile_rejectsMissingOutgoingPduForRead() {
        Uri uri = Uri.parse("content://" + MmsFileProvider.AUTHORITY + "/missing-outgoing.pdu");

        assertThrows(FileNotFoundException.class, () -> provider.openFile(uri, "r"));
    }

}
