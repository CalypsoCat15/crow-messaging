package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SmsSenderTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("pending_sms_sends", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("crow_scheduled_messages", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void cleanupStalePendingSends_marksOldNormalPendingSendFailed() {
        String sendId = "normal-stale";
        seedPending(sendId, "", System.currentTimeMillis() - sevenHoursMillis());

        SmsSender.cleanupStalePendingSends(context);

        assertTrue(pendingPrefs().contains("send_" + sendId + "_address"));
        assertEquals(ChatMessage.STATUS_FAILED, pendingPrefs().getString("send_" + sendId + "_status", ""));
    }

    @Test
    public void cleanupStalePendingSends_keepsExistingLocalStatusRow() {
        String sendId = "already-failed";
        seedPending(sendId, "", System.currentTimeMillis() - sevenHoursMillis());
        pendingPrefs().edit()
                .putString("send_" + sendId + "_status", ChatMessage.STATUS_NOT_SAVED)
                .commit();

        SmsSender.cleanupStalePendingSends(context);

        assertTrue(pendingPrefs().contains("send_" + sendId + "_address"));
        assertEquals(ChatMessage.STATUS_NOT_SAVED, pendingPrefs().getString("send_" + sendId + "_status", ""));
    }

    @Test
    public void cleanupStalePendingSends_marksScheduledMessageFailed() {
        ScheduledMessageStore.ScheduledMessage scheduled = ScheduledMessageStore.save(
                context,
                "15551234567",
                "hello",
                System.currentTimeMillis() - 1000L
        );
        seedPending("scheduled-stale", scheduled.id, System.currentTimeMillis() - sevenHoursMillis());

        SmsSender.cleanupStalePendingSends(context);

        ScheduledMessageStore.ScheduledMessage updated = ScheduledMessageStore.find(context, scheduled.id);
        assertTrue(updated.failed());
    }

    @Test
    public void cleanupStalePendingSends_keepsRecentScheduledPendingSend() {
        ScheduledMessageStore.ScheduledMessage scheduled = ScheduledMessageStore.save(
                context,
                "15551234567",
                "hello",
                System.currentTimeMillis() - 1000L
        );
        seedPending("scheduled-recent", scheduled.id, System.currentTimeMillis());

        SmsSender.cleanupStalePendingSends(context);

        assertTrue(SmsSender.hasPendingScheduled(context, scheduled.id));
        assertFalse(ScheduledMessageStore.find(context, scheduled.id).failed());
    }

    @Test
    public void hasPendingScheduled_ignoresMalformedPendingRecord() {
        ScheduledMessageStore.ScheduledMessage scheduled = ScheduledMessageStore.save(
                context,
                "15551234567",
                "hello",
                System.currentTimeMillis() + 60000L
        );
        pendingPrefs().edit()
                .putString("send_malformed_scheduled_id", scheduled.id)
                .commit();

        assertFalse(SmsSender.hasPendingScheduled(context, scheduled.id));
    }

    @Test
    public void sendAndRecord_rejectsGroupAddressBeforeSending() {
        String group = LocalMmsStore.conversationAddress("", java.util.List.of(
                "15551234567",
                "15557654321",
                "15559876543"
        ));

        try {
            SmsSender.sendAndRecord(context, group, "hello");
        } catch (SmsSender.SendException ex) {
            assertEquals("Group sending is not available yet.", ex.getMessage());
            return;
        }

        throw new AssertionError("Expected group send to be rejected.");
    }

    @Test
    public void preparedMessageParts_rejectsEmptyAndroidResult() {
        try {
            SmsSender.preparedMessageParts("hello", body -> new ArrayList<>());
        } catch (SmsSender.SendException ex) {
            assertEquals("Message could not be prepared for sending.", ex.getMessage());
            return;
        }

        throw new AssertionError("Expected empty message parts to be rejected.");
    }

    @Test
    public void preparedMessageParts_wrapsAndroidPreparationFailure() {
        try {
            SmsSender.preparedMessageParts("hello", body -> {
                throw new IllegalArgumentException("bad message");
            });
        } catch (SmsSender.SendException ex) {
            assertEquals("Message could not be prepared for sending.", ex.getMessage());
            assertTrue(ex.getCause() instanceof IllegalArgumentException);
            return;
        }

        throw new AssertionError("Expected preparation failure to be wrapped.");
    }

    @Test
    public void scheduledReceiver_cleansStalePendingBeforeCheckingSchedule() {
        ScheduledMessageStore.ScheduledMessage scheduled = ScheduledMessageStore.save(
                context,
                "15551234567",
                "hello",
                System.currentTimeMillis() - 1000L
        );
        seedPending("scheduled-stale", scheduled.id, System.currentTimeMillis() - sevenHoursMillis());
        Intent intent = new Intent(context, ScheduledSmsReceiver.class);
        intent.setAction(ScheduledSmsReceiver.ACTION_SEND_SCHEDULED);

        ScheduledSmsReceiver.handleIntent(context, intent);

        assertTrue(ScheduledMessageStore.find(context, scheduled.id).failed());
        assertFalse(SmsSender.hasPendingScheduled(context, scheduled.id));
    }

    @Test
    public void schedule_marksMessageFailedWhenAlarmServiceUnavailable() {
        ScheduledMessageStore.ScheduledMessage scheduled = ScheduledMessageStore.save(
                context,
                "15551234567",
                "hello",
                System.currentTimeMillis() + 60000L
        );

        assertFalse(ScheduledSmsReceiver.schedule(new NoAlarmContext(context), scheduled));

        ScheduledMessageStore.ScheduledMessage updated = ScheduledMessageStore.find(context, scheduled.id);
        assertTrue(updated.failed());
        assertEquals("Scheduled texts are not available on this phone right now.", updated.failureReason);
    }

    @Test
    public void handleSentResult_ignoresDuplicateMultipartCallback() {
        String sendId = "multipart-send";
        seedPending(sendId, "", System.currentTimeMillis(), 2);

        SmsSender.handleSentResult(context, sentIntent(sendId, 0), Activity.RESULT_OK);
        assertTrue(pendingPrefs().contains("send_" + sendId + "_address"));
        assertTrue(pendingPrefs().getStringSet("send_" + sendId + "_completed_parts", new HashSet<>()).contains("0"));

        SmsSender.handleSentResult(context, sentIntent(sendId, 0), Activity.RESULT_OK);
        assertTrue(pendingPrefs().contains("send_" + sendId + "_address"));
        assertFalse(pendingPrefs().getStringSet("send_" + sendId + "_completed_parts", new HashSet<>()).contains("1"));

        SmsSender.handleSentResult(context, sentIntent(sendId, 1), Activity.RESULT_OK);
        assertFalse(pendingPrefs().contains("send_" + sendId + "_address"));
    }

    @Test
    public void handleSentResult_serializesConcurrentMultipartCallbacks() throws Exception {
        String sendId = "concurrent-multipart-send";
        seedPending(sendId, "", System.currentTimeMillis(), 2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> first = executor.submit(() -> SmsSender.handleSentResult(
                context,
                sentIntent(sendId, 0),
                Activity.RESULT_OK
        ));
        Future<?> second = executor.submit(() -> SmsSender.handleSentResult(
                context,
                sentIntent(sendId, 1),
                Activity.RESULT_OK
        ));
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertFalse(pendingPrefs().contains("send_" + sendId + "_address"));
    }

    @Test
    public void handleSentResult_ignoresInvalidPartIndex() {
        String sendId = "invalid-part";
        seedPending(sendId, "", System.currentTimeMillis(), 2);

        SmsSender.handleSentResult(context, sentIntent(sendId, 99), Activity.RESULT_OK);

        assertTrue(pendingPrefs().contains("send_" + sendId + "_address"));
        assertTrue(pendingPrefs().getStringSet("send_" + sendId + "_completed_parts", new HashSet<>()).isEmpty());
    }

    @Test
    public void handleSentResult_ignoresCorruptStoredCompletedParts() {
        String sendId = "corrupt-completed-parts";
        HashSet<String> corruptParts = new HashSet<>();
        corruptParts.add("not-a-number");
        corruptParts.add("99");
        seedPending(sendId, "", System.currentTimeMillis(), 2, corruptParts);

        SmsSender.handleSentResult(context, sentIntent(sendId, 0), Activity.RESULT_OK);

        assertTrue(pendingPrefs().contains("send_" + sendId + "_address"));
        assertTrue(pendingPrefs().getStringSet("send_" + sendId + "_completed_parts", new HashSet<>()).contains("0"));
        assertFalse(pendingPrefs().getStringSet("send_" + sendId + "_completed_parts", new HashSet<>()).contains("99"));
    }

    @Test
    public void handleSentResult_keepsFailedNormalSendVisible() {
        String sendId = "failed-send";
        seedPending(sendId, "", System.currentTimeMillis());

        SmsSender.handleSentResult(context, sentIntent(sendId, 0), Activity.RESULT_CANCELED);

        assertTrue(pendingPrefs().contains("send_" + sendId + "_address"));
        assertEquals(ChatMessage.STATUS_FAILED, pendingPrefs().getString("send_" + sendId + "_status", ""));
    }

    @Test
    public void handleSentResult_marksMultipartFailureImmediately() {
        String sendId = "failed-multipart-send";
        seedPending(sendId, "", System.currentTimeMillis(), 2);

        SmsSender.handleSentResult(context, sentIntent(sendId, 0), Activity.RESULT_CANCELED);

        assertTrue(pendingPrefs().contains("send_" + sendId + "_address"));
        assertEquals(ChatMessage.STATUS_FAILED, pendingPrefs().getString("send_" + sendId + "_status", ""));
    }

    @Test
    public void handleSentResult_marksScheduledMultipartFailureImmediately() {
        ScheduledMessageStore.ScheduledMessage scheduled = ScheduledMessageStore.save(
                context,
                "15551234567",
                "hello",
                System.currentTimeMillis() - 1000L
        );
        String sendId = "failed-scheduled-multipart-send";
        seedPending(sendId, scheduled.id, System.currentTimeMillis(), 2);

        SmsSender.handleSentResult(context, sentIntent(sendId, 0), Activity.RESULT_CANCELED);

        assertFalse(pendingPrefs().contains("send_" + sendId + "_address"));
        assertTrue(ScheduledMessageStore.find(context, scheduled.id).failed());
    }

    @Test
    public void handleSentResult_ignoresLateCallbackForLocalStatusRow() {
        String sendId = "late-callback";
        seedPending(sendId, "", System.currentTimeMillis(), 1, new HashSet<>());
        pendingPrefs().edit()
                .putString("send_" + sendId + "_status", ChatMessage.STATUS_FAILED)
                .commit();

        SmsSender.handleSentResult(context, sentIntent(sendId, 0), Activity.RESULT_OK);

        assertTrue(pendingPrefs().contains("send_" + sendId + "_address"));
        assertEquals(ChatMessage.STATUS_FAILED, pendingPrefs().getString("send_" + sendId + "_status", ""));
        assertTrue(pendingPrefs().getStringSet("send_" + sendId + "_completed_parts", new HashSet<>()).isEmpty());
    }

    @Test
    public void pendingMessagesForAddress_returnsVisibleSendingBubbleForNormalPendingSend() {
        seedPending("normal-pending", "", 1234L);

        ArrayList<ChatMessage> messages = SmsSender.pendingMessagesForAddress(context, "+1 (555) 123-4567");

        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).body);
        assertEquals(ChatMessage.STATUS_SENDING, messages.get(0).status);
        assertTrue(messages.get(0).outgoing);
    }

    @Test
    public void pendingMessagesForAddress_ignoresScheduledPendingSend() {
        seedPending("scheduled-pending", "scheduled-id", 1234L);

        assertTrue(SmsSender.pendingMessagesForAddress(context, "15551234567").isEmpty());
    }

    @Test
    public void pendingMessagesForAddress_showsFailedStatus() {
        seedPending("failed-pending", "", 1234L);
        pendingPrefs().edit()
                .putString("send_failed-pending_status", ChatMessage.STATUS_FAILED)
                .commit();

        ArrayList<ChatMessage> messages = SmsSender.pendingMessagesForAddress(context, "15551234567");

        assertEquals(1, messages.size());
        assertEquals(ChatMessage.STATUS_FAILED, messages.get(0).status);
        assertEquals("failed-pending", messages.get(0).localStatusId);
    }

    @Test
    public void failedMessageForRetry_returnsFailedNormalText() {
        seedPending("failed-retry", "", 1234L);
        pendingPrefs().edit()
                .putString("send_failed-retry_status", ChatMessage.STATUS_FAILED)
                .commit();

        SmsSender.RetryMessage retry = SmsSender.failedMessageForRetry(context, "failed-retry");

        assertNotNull(retry);
        assertEquals("15551234567", retry.address);
        assertEquals("hello", retry.body);
    }

    @Test
    public void failedMessageForRetry_rejectsSendingAndScheduledTexts() {
        seedPending("still-sending", "", 1234L);
        seedPending("scheduled-failure", "scheduled-id", 1234L);
        pendingPrefs().edit()
                .putString("send_scheduled-failure_status", ChatMessage.STATUS_FAILED)
                .commit();

        assertNull(SmsSender.failedMessageForRetry(context, "still-sending"));
        assertNull(SmsSender.failedMessageForRetry(context, "scheduled-failure"));
        assertNull(SmsSender.failedMessageForRetry(context, "missing"));
    }

    @Test
    public void pendingConversations_returnsInboxRowForNormalPendingSend() {
        seedPending("normal-inbox-pending", "", 1234L);

        ArrayList<Conversation> conversations = SmsSender.pendingConversations(context, "");

        assertEquals(1, conversations.size());
        assertEquals("15551234567", conversations.get(0).address);
        assertEquals("hello", conversations.get(0).snippet);
        assertEquals(1234L, conversations.get(0).dateMillis);
    }

    @Test
    public void pendingConversations_ignoresScheduledPendingSend() {
        seedPending("scheduled-inbox-pending", "scheduled-id", 1234L);

        assertTrue(SmsSender.pendingConversations(context, "").isEmpty());
    }

    @Test
    public void pendingConversations_respectsSearchQuery() {
        seedPending("normal-query-pending", "", 1234L);

        assertEquals(1, SmsSender.pendingConversations(context, "hell").size());
        assertTrue(SmsSender.pendingConversations(context, "missing").isEmpty());
    }

    @Test
    public void pendingConversations_searchesLocalStatus() {
        seedPending("failed-query-pending", "", 1234L);
        pendingPrefs().edit()
                .putString("send_failed-query-pending_status", ChatMessage.STATUS_FAILED)
                .commit();

        assertEquals(1, SmsSender.pendingConversations(context, "failed").size());
    }

    @Test
    public void deletePendingForAddress_removesMatchingLocalStatusRows() {
        seedPending("delete-me", "", 1234L);
        seedPending("keep-me", "", 1234L);
        pendingPrefs().edit()
                .putString("send_delete-me_status", ChatMessage.STATUS_FAILED)
                .putString("send_keep-me_status", ChatMessage.STATUS_FAILED)
                .putString("send_keep-me_address", "15557654321")
                .commit();

        assertEquals(1, SmsSender.deletePendingForAddress(context, "+1 (555) 123-4567"));

        assertFalse(pendingPrefs().contains("send_delete-me_address"));
        assertTrue(pendingPrefs().contains("send_keep-me_address"));
    }

    @Test
    public void deletePendingForAddress_keepsActiveSendingRows() {
        seedPending("active-send", "", 1234L);

        assertEquals(0, SmsSender.deletePendingForAddress(context, "+1 (555) 123-4567"));

        assertTrue(pendingPrefs().contains("send_active-send_address"));
    }

    @Test
    public void deletePendingById_removesOnlyRequestedRow() {
        seedPending("delete-id", "", 1234L);
        seedPending("keep-id", "", 1234L);
        pendingPrefs().edit()
                .putString("send_delete-id_status", ChatMessage.STATUS_FAILED)
                .putString("send_keep-id_status", ChatMessage.STATUS_FAILED)
                .commit();

        assertTrue(SmsSender.deletePendingById(context, "delete-id"));

        assertFalse(pendingPrefs().contains("send_delete-id_address"));
        assertTrue(pendingPrefs().contains("send_keep-id_address"));
    }

    @Test
    public void deletePendingById_ignoresActiveSendingRow() {
        seedPending("active-send", "", 1234L);

        assertFalse(SmsSender.deletePendingById(context, "active-send"));

        assertTrue(pendingPrefs().contains("send_active-send_address"));
    }

    private void seedPending(String sendId, String scheduledId, long createdAtMillis) {
        seedPending(sendId, scheduledId, createdAtMillis, 1);
    }

    private void seedPending(String sendId, String scheduledId, long createdAtMillis, int partCount) {
        seedPending(sendId, scheduledId, createdAtMillis, partCount, new HashSet<>());
    }

    private void seedPending(String sendId, String scheduledId, long createdAtMillis, int partCount, HashSet<String> completedParts) {
        pendingPrefs().edit()
                .putString("send_" + sendId + "_address", "15551234567")
                .putString("send_" + sendId + "_body", "hello")
                .putLong("send_" + sendId + "_sent_at", createdAtMillis)
                .putLong("send_" + sendId + "_created_at", createdAtMillis)
                .putInt("send_" + sendId + "_remaining", partCount)
                .putInt("send_" + sendId + "_part_count", partCount)
                .putStringSet("send_" + sendId + "_completed_parts", completedParts)
                .putBoolean("send_" + sendId + "_failed", false)
                .putString("send_" + sendId + "_scheduled_id", scheduledId)
                .commit();
    }

    private Intent sentIntent(String sendId, int partIndex) {
        Intent intent = new Intent(context, SmsSentReceiver.class);
        intent.putExtra(SmsSender.EXTRA_SEND_ID, sendId);
        intent.putExtra(SmsSender.EXTRA_PART_INDEX, partIndex);
        return intent;
    }

    private SharedPreferences pendingPrefs() {
        return context.getSharedPreferences("pending_sms_sends", Context.MODE_PRIVATE);
    }

    private long sevenHoursMillis() {
        return 7L * 60L * 60L * 1000L;
    }

    private static final class NoAlarmContext extends ContextWrapper {
        NoAlarmContext(Context base) {
            super(base);
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.ALARM_SERVICE.equals(name)) {
                return null;
            }
            return super.getSystemService(name);
        }
    }
}
