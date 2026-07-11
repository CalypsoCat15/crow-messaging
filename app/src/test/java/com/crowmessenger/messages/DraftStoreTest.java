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
public class DraftStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("message_drafts", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void drafts_removesMalformedDraftWithoutAddress() {
        context.getSharedPreferences("message_drafts", Context.MODE_PRIVATE)
                .edit()
                .putString("draft:", "unfinished")
                .commit();

        assertTrue(DraftStore.drafts(context).isEmpty());
        assertFalse(context.getSharedPreferences("message_drafts", Context.MODE_PRIVATE)
                .contains("draft:"));
    }

    @Test
    public void drafts_keepsValidDraftWithoutAddressMetadata() {
        DraftStore.save(context, "+1 (555) 123-4567", "unfinished");
        context.getSharedPreferences("message_drafts", Context.MODE_PRIVATE)
                .edit()
                .remove("draft:15551234567:address")
                .commit();

        assertEquals(1, DraftStore.drafts(context).size());
        assertEquals("15551234567", DraftStore.drafts(context).get(0).address);
    }

    @Test
    public void clear_returnsWhetherDraftTextWasRemoved() {
        assertFalse(DraftStore.clear(context, "15551234567"));

        DraftStore.save(context, "15551234567", "unfinished");

        assertTrue(DraftStore.clear(context, "+1 (555) 123-4567"));
        assertFalse(DraftStore.clear(context, "15551234567"));
    }

    @Test
    public void draft_migratesEquivalentNationalAndCountryCodeFormats() {
        DraftStore.save(context, "5551234567", "same conversation");

        assertEquals("same conversation", DraftStore.draft(context, "+1 (555) 123-4567"));
        assertEquals("same conversation", DraftStore.draft(context, "5551234567"));
        assertEquals(1, DraftStore.drafts(context).size());
    }

    @Test
    public void save_replacesEquivalentFormattedDraftInsteadOfDuplicatingIt() {
        DraftStore.save(context, "+1 (555) 123-4567", "first");
        DraftStore.save(context, "5551234567", "updated");

        assertEquals("updated", DraftStore.draft(context, "+1 (555) 123-4567"));
        assertEquals(1, DraftStore.drafts(context).size());
    }
}
