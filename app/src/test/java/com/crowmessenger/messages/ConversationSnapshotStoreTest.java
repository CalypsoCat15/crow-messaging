package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ConversationSnapshotStoreTest {
    private static final String PREFS = "conversation_snapshot_test";
    private static final String KEY = "rows";
    private static final int VERSION = 3;

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void load_skipsMalformedRowsAndClampsUnreadCount() throws Exception {
        JSONArray rows = new JSONArray()
                .put(new JSONObject()
                        .put("threadId", "1")
                        .put("address", "15551234567")
                        .put("name", "Valid")
                        .put("snippet", "Hello")
                        .put("dateMillis", 1234L)
                        .put("unreadCount", -4))
                .put(new JSONObject().put("name", "Missing address"))
                .put("not-an-object");
        JSONObject snapshot = new JSONObject()
                .put("version", VERSION)
                .put("rows", rows);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, snapshot.toString())
                .commit();

        List<Conversation> loaded = ConversationSnapshotStore.load(
                context, PREFS, KEY, VERSION, 10
        );

        assertEquals(1, loaded.size());
        assertEquals("15551234567", loaded.get(0).address);
        assertEquals(0, loaded.get(0).unreadCount);
    }

    @Test
    public void load_wrongVersionReturnsEmpty() throws Exception {
        JSONObject snapshot = new JSONObject()
                .put("version", VERSION - 1)
                .put("rows", new JSONArray().put(new JSONObject()
                        .put("address", "15551234567")));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, snapshot.toString())
                .commit();

        List<Conversation> loaded = ConversationSnapshotStore.load(
                context, PREFS, KEY, VERSION, 10
        );

        assertTrue(loaded.isEmpty());
    }

    @Test
    public void write_skipsInvalidRowsBeforeApplyingLimit() {
        ArrayList<Conversation> rows = new ArrayList<>();
        rows.add(null);
        rows.add(new Conversation("invalid", "", "Missing address", "Body", 1L, 0));
        rows.add(new Conversation("1", "15551234567", "First", "One", 2L, 0));
        rows.add(new Conversation("2", "15557654321", "Second", "Two", 3L, 0));

        ConversationSnapshotStore.write(
                context, PREFS, KEY, VERSION, 2, rows, true
        );
        List<Conversation> loaded = ConversationSnapshotStore.load(
                context, PREFS, KEY, VERSION, 2
        );

        assertEquals(2, loaded.size());
        assertEquals("15551234567", loaded.get(0).address);
        assertEquals("15557654321", loaded.get(1).address);
    }
}
