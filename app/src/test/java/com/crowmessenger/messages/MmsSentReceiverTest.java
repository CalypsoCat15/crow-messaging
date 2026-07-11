package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsSentReceiverTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void deleteOutgoingPdu_removesOnlySafePduNames() throws Exception {
        File pdu = new File(MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR), "send-test.pdu");
        try (FileOutputStream output = new FileOutputStream(pdu)) {
            output.write(new byte[] { 1, 2, 3 });
        }

        MmsSentReceiver.deleteOutgoingPdu(context, pdu.getName());

        assertFalse(pdu.exists());
        assertTrue(MmsSentReceiver.isSafePduName("message.pdu"));
        assertFalse(MmsSentReceiver.isSafePduName("../message.pdu"));
        assertFalse(MmsSentReceiver.isSafePduName("message.jpg"));
    }
}
