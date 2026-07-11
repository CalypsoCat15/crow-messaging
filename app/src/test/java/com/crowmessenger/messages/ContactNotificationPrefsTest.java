package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ContactNotificationPrefsTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("contact_notification_sounds", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void groupSoundDoesNotApplyToIndividualParticipant() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));

        ContactNotificationPrefs.setSound(context, group, null);

        assertTrue(ContactNotificationPrefs.isSilent(context, group));
        assertFalse(ContactNotificationPrefs.isSilent(context, "15557654321"));
    }

    @Test
    public void silentSettingIsCustomSettingButNotCustomSound() {
        ContactNotificationPrefs.setSound(context, "15551234567", null);

        assertTrue(ContactNotificationPrefs.hasCustomSetting(context, "+1 (555) 123-4567"));
        assertFalse(ContactNotificationPrefs.hasCustomSound(context, "+1 (555) 123-4567"));
        assertTrue(ContactNotificationPrefs.isSilent(context, "+1 (555) 123-4567"));
    }

    @Test
    public void settingReportsDefaultSilentAndCustomSoundStates() {
        ContactNotificationPrefs.NotificationSetting defaultSetting = ContactNotificationPrefs.setting(context, "15551234567");
        assertFalse(defaultSetting.hasCustomSetting());
        assertFalse(defaultSetting.hasCustomSound());
        assertFalse(defaultSetting.isSilent());
        assertNull(defaultSetting.soundUri());

        ContactNotificationPrefs.setSound(context, "15551234567", null);
        ContactNotificationPrefs.NotificationSetting silentSetting = ContactNotificationPrefs.setting(context, "+1 (555) 123-4567");
        assertTrue(silentSetting.hasCustomSetting());
        assertFalse(silentSetting.hasCustomSound());
        assertTrue(silentSetting.isSilent());
        assertNull(silentSetting.soundUri());

        ContactNotificationPrefs.setSound(context, "15551234567", Uri.parse("content://sounds/custom"));
        ContactNotificationPrefs.NotificationSetting soundSetting = ContactNotificationPrefs.setting(context, "+1 (555) 123-4567");
        assertTrue(soundSetting.hasCustomSetting());
        assertTrue(soundSetting.hasCustomSound());
        assertFalse(soundSetting.isSilent());
        assertTrue(Uri.parse("content://sounds/custom").equals(soundSetting.soundUri()));
    }

    @Test
    public void clearingIndividualSoundDoesNotRemoveGroupSound() {
        String group = LocalMmsStore.conversationAddress("", List.of(
                "15550001111",
                "15551234567",
                "15557654321"
        ));
        ContactNotificationPrefs.setSound(context, group, Uri.parse("content://sounds/group"));

        ContactNotificationPrefs.useDefault(context, "15557654321");

        assertTrue(ContactNotificationPrefs.hasCustomSound(context, group));
    }

    @Test
    public void setSoundRemovesLegacyMatchingPhoneSoundKeys() {
        SharedPreferences prefs = context.getSharedPreferences("contact_notification_sounds", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("+1 (555) 123-4567", "content://sounds/old")
                .commit();

        ContactNotificationPrefs.setSound(context, "15551234567", Uri.parse("content://sounds/new"));

        assertEquals("content://sounds/new", prefs.getString("15551234567", ""));
        assertFalse(prefs.contains("+1 (555) 123-4567"));
    }
}
