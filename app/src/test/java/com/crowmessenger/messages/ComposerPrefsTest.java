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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ComposerPrefsTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("composer_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void voiceButtonVisible_defaultsToShown() {
        assertTrue(ComposerPrefs.voiceButtonVisible(context));
    }

    @Test
    public void setVoiceButtonVisible_storesChoice() {
        ComposerPrefs.setVoiceButtonVisible(context, false);
        assertFalse(ComposerPrefs.voiceButtonVisible(context));

        ComposerPrefs.setVoiceButtonVisible(context, true);
        assertTrue(ComposerPrefs.voiceButtonVisible(context));
    }
}
