package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SettingsBackupTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("crow_text_size", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("composer_prefs", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("spam_senders", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void createAndRestorePreservesSafeSettingsOnly() throws Exception {
        TextSizePrefs.setLabel(context, TextSizePrefs.EXTRA_LARGE);
        ComposerPrefs.setVoiceButtonVisible(context, false);
        SpamFilter.addCustomKeywords(context, "Donate, Winner");
        SpamFilter.markSpam(context, "+15551234567");

        String backup = SettingsBackup.create(context);

        assertTrue(backup.contains("Extra large"));
        assertTrue(backup.contains("donate"));
        assertFalse(backup.contains("15551234567"));

        TextSizePrefs.setLabel(context, TextSizePrefs.SMALL);
        ComposerPrefs.setVoiceButtonVisible(context, true);
        SpamFilter.replaceCustomKeywords(context, java.util.List.of("temporary"));
        assertEquals(3, SettingsBackup.restore(context, backup));

        assertEquals(TextSizePrefs.EXTRA_LARGE, TextSizePrefs.currentLabel(context));
        assertFalse(ComposerPrefs.voiceButtonVisible(context));
        assertEquals(java.util.List.of("donate", "winner"), SpamFilter.customKeywords(context));
    }

    @Test(expected = org.json.JSONException.class)
    public void restoreRejectsUnrelatedJson() throws Exception {
        SettingsBackup.restore(context, "{\"format\":\"something-else\",\"version\":1}");
    }

    @Test
    public void restoreRejectsMalformedBackupWithoutChangingAnySettings() throws Exception {
        TextSizePrefs.setLabel(context, TextSizePrefs.LARGE);
        ComposerPrefs.setVoiceButtonVisible(context, false);
        SpamFilter.addCustomKeywords(context, "keep me");
        String malformed = "{"
                + "\"format\":\"crow-settings\","
                + "\"version\":1,"
                + "\"textSize\":\"Small\","
                + "\"microphoneButton\":true,"
                + "\"spamKeywords\":\"not-a-list\""
                + "}";

        org.junit.Assert.assertThrows(
                org.json.JSONException.class,
                () -> SettingsBackup.restore(context, malformed)
        );

        assertEquals(TextSizePrefs.LARGE, TextSizePrefs.currentLabel(context));
        assertFalse(ComposerPrefs.voiceButtonVisible(context));
        assertEquals(java.util.List.of("keep me"), SpamFilter.customKeywords(context));
    }

    @Test
    public void backupReaderAcceptsSmallUtf8SettingsFile() throws Exception {
        java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(
                "{\"format\":\"crow-settings\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertEquals("{\"format\":\"crow-settings\"}", MainActivity.readSmallText(input, 1024));
    }

    @Test(expected = IllegalArgumentException.class)
    public void backupReaderRejectsOversizedFile() throws Exception {
        MainActivity.readSmallText(
                new java.io.ByteArrayInputStream("too large".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                4
        );
    }
}
