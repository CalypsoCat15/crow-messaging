package com.crowmessenger.messages;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TextSizePrefsTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("crow_text_size", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void currentLabel_defaultsInvalidSavedValueToNormal() {
        context.getSharedPreferences("crow_text_size", Context.MODE_PRIVATE)
                .edit()
                .putString("label", "Huge")
                .commit();

        assertEquals(TextSizePrefs.NORMAL, TextSizePrefs.currentLabel(context));
    }

    @Test
    public void setLabel_storesValidLabelsAndNormalizesInvalidOnes() {
        TextSizePrefs.setLabel(context, TextSizePrefs.EXTRA_LARGE);
        assertEquals(TextSizePrefs.EXTRA_LARGE, TextSizePrefs.currentLabel(context));

        TextSizePrefs.setLabel(context, "Tiny");
        assertEquals(TextSizePrefs.NORMAL, TextSizePrefs.currentLabel(context));
    }

    @Test
    public void labels_returnsSupportedChoices() {
        assertArrayEquals(
                new String[] {
                        TextSizePrefs.SMALL,
                        TextSizePrefs.NORMAL,
                        TextSizePrefs.LARGE,
                        TextSizePrefs.EXTRA_LARGE
                },
                TextSizePrefs.labels()
        );
    }

    @Test
    public void textSizes_matchSupportedLabels() {
        assertTextSizes(TextSizePrefs.SMALL, 16, 13, 15, 12, 11, 15);
        assertTextSizes(TextSizePrefs.NORMAL, 17, 14, 16, 12, 11, 16);
        assertTextSizes(TextSizePrefs.LARGE, 19, 16, 18, 13, 12, 18);
        assertTextSizes(TextSizePrefs.EXTRA_LARGE, 21, 18, 20, 14, 13, 20);
    }

    private void assertTextSizes(
            String label,
            int inboxNameSp,
            int inboxPreviewSp,
            int messageSp,
            int senderSp,
            int timestampSp,
            int composerSp
    ) {
        TextSizePrefs.setLabel(context, label);

        assertEquals(inboxNameSp, TextSizePrefs.inboxNameSp(context));
        assertEquals(inboxPreviewSp, TextSizePrefs.inboxPreviewSp(context));
        assertEquals(messageSp, TextSizePrefs.messageSp(context));
        assertEquals(senderSp, TextSizePrefs.senderSp(context));
        assertEquals(timestampSp, TextSizePrefs.timestampSp(context));
        assertEquals(composerSp, TextSizePrefs.composerSp(context));
    }
}
