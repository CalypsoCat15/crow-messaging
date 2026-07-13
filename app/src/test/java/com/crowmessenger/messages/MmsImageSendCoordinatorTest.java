package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsImageSendCoordinatorTest {
    private Context context;
    private Uri first;
    private Uri second;
    private Uri third;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE).edit().clear().commit();
        first = Uri.parse("content://pictures/first");
        second = Uri.parse("content://pictures/second");
        third = Uri.parse("content://pictures/third");
    }

    @Test
    public void sendBatch_sendsInOrderWithCaptionOnlyOnFirstAndNullRecipients() {
        RecordingSender sender = new RecordingSender(Arrays.asList(101L, 202L, 303L));
        RecordingCleaner cleaner = new RecordingCleaner();
        MmsImageSendCoordinator.Request request = new MmsImageSendCoordinator.Request(
                "15551234567",
                null,
                "First picture caption",
                Arrays.asList(first, second, third)
        );

        MmsImageSendCoordinator.Result result = MmsImageSendCoordinator.sendBatch(
                context,
                request,
                sender,
                cleaner
        );

        assertEquals(Arrays.asList(first, second, third), sender.imageUris);
        assertEquals(Arrays.asList("First picture caption", "", ""), sender.captions);
        assertEquals(3, sender.recipients.size());
        assertNull(sender.recipients.get(0));
        assertNull(sender.recipients.get(1));
        assertNull(sender.recipients.get(2));
        assertEquals(3, result.sentCount);
        assertEquals(303L, result.lastSentAt);
        assertTrue(result.remainingUris.isEmpty());
        assertTrue(result.error.isEmpty());
        assertTrue(result.captionConsumed);
        assertTrue(result.succeeded());
        assertEquals(Arrays.asList(first, second, third), cleaner.cleaned);
    }

    @Test
    public void request_snapshotsExplicitRecipientsAndImages() {
        ArrayList<String> recipients = new ArrayList<>(Arrays.asList("15550100001", "15550100002"));
        ArrayList<Uri> images = new ArrayList<>(Collections.singletonList(first));
        MmsImageSendCoordinator.Request request = new MmsImageSendCoordinator.Request(
                "group:one|two",
                recipients,
                "Group caption",
                images
        );
        recipients.clear();
        images.clear();
        RecordingSender sender = new RecordingSender(Collections.singletonList(404L));

        MmsImageSendCoordinator.Result result = MmsImageSendCoordinator.sendBatch(
                context,
                request,
                sender,
                new RecordingCleaner()
        );

        assertEquals(1, result.sentCount);
        assertEquals(Collections.singletonList(first), sender.imageUris);
        assertEquals(
                Arrays.asList("15550100001", "15550100002"),
                sender.recipients.get(0)
        );
    }

    @Test
    public void sendBatch_firstFailureKeepsCaptionAndEveryImage() {
        RecordingSender sender = new RecordingSender(Arrays.asList(101L, 202L, 303L));
        sender.failAtIndex = 0;
        RecordingCleaner cleaner = new RecordingCleaner();
        MmsImageSendCoordinator.Request request = new MmsImageSendCoordinator.Request(
                "15551234567",
                null,
                "Keep this caption",
                Arrays.asList(first, second, third)
        );

        MmsImageSendCoordinator.Result result = MmsImageSendCoordinator.sendBatch(
                context,
                request,
                sender,
                cleaner
        );

        assertEquals(Collections.singletonList(first), sender.imageUris);
        assertEquals(Collections.singletonList("Keep this caption"), sender.captions);
        assertEquals(0, result.sentCount);
        assertEquals(0L, result.lastSentAt);
        assertEquals(Arrays.asList(first, second, third), result.remainingUris);
        assertEquals("test failure 0", result.error);
        assertFalse(result.captionConsumed);
        assertFalse(result.succeeded());
        assertTrue(cleaner.cleaned.isEmpty());
    }

    @Test
    public void sendBatch_secondFailureStopsAndKeepsFailedImageAndTail() {
        RecordingSender sender = new RecordingSender(Arrays.asList(101L, 202L, 303L));
        sender.failAtIndex = 1;
        RecordingCleaner cleaner = new RecordingCleaner();
        MmsImageSendCoordinator.Request request = new MmsImageSendCoordinator.Request(
                "15551234567",
                Arrays.asList("15550100001", "15550100002"),
                "Already sent caption",
                Arrays.asList(first, second, third)
        );

        MmsImageSendCoordinator.Result result = MmsImageSendCoordinator.sendBatch(
                context,
                request,
                sender,
                cleaner
        );

        assertEquals(Arrays.asList(first, second), sender.imageUris);
        assertEquals(Arrays.asList("Already sent caption", ""), sender.captions);
        assertEquals(1, result.sentCount);
        assertEquals(101L, result.lastSentAt);
        assertEquals(Arrays.asList(second, third), result.remainingUris);
        assertEquals("test failure 1", result.error);
        assertTrue(result.captionConsumed);
        assertFalse(result.succeeded());
        assertEquals(Collections.singletonList(first), cleaner.cleaned);
    }

    @Test
    public void deleteCameraImageIfOwned_deletesOnlyExactCrowCameraUri() throws Exception {
        File cameraDirectory = MmsFiles.appFileDir(context, MmsFiles.CAMERA_DIR);
        File owned = new File(cameraDirectory, "coordinator-owned.jpg");
        File notCamera = new File(cameraDirectory, "coordinator-not-camera.jpg");
        assertTrue(owned.createNewFile());
        assertTrue(notCamera.createNewFile());

        MmsImageSendCoordinator.deleteCameraImageIfOwned(
                context,
                Uri.parse("content://" + MmsFiles.CAMERA_AUTHORITY + "/camera/" + owned.getName())
        );
        MmsImageSendCoordinator.deleteCameraImageIfOwned(
                context,
                Uri.parse("content://other.provider/camera/" + notCamera.getName())
        );

        assertFalse(owned.exists());
        assertTrue(notCamera.exists());
        assertTrue(notCamera.delete());
    }

    @Test
    public void submit_rejectsDuplicateUntilCompletedResultIsConsumed() throws Exception {
        BlockingSender blockingSender = new BlockingSender(505L);
        String jobKey = "duplicate-test-" + System.nanoTime();
        MmsImageSendCoordinator.Request request = new MmsImageSendCoordinator.Request(
                "15551234567",
                null,
                "",
                Collections.singletonList(first)
        );

        try {
            assertTrue(MmsImageSendCoordinator.submit(
                    context,
                    jobKey,
                    request,
                    null,
                    blockingSender,
                    new RecordingCleaner()
            ));
            assertTrue(blockingSender.entered.await(5, TimeUnit.SECONDS));
            assertTrue(MmsImageSendCoordinator.isActive(jobKey));
            assertFalse(MmsImageSendCoordinator.submit(
                    context,
                    jobKey,
                    request,
                    null,
                    blockingSender,
                    new RecordingCleaner()
            ));
        } finally {
            blockingSender.release.countDown();
        }

        waitForCompletion(jobKey);
        assertTrue(MmsImageSendCoordinator.isActive(jobKey));
        assertFalse(MmsImageSendCoordinator.submit(
                context,
                jobKey,
                request,
                null,
                new RecordingSender(Collections.singletonList(606L)),
                new RecordingCleaner()
        ));
        assertNotNull(MmsImageSendCoordinator.consume(jobKey));
        assertFalse(MmsImageSendCoordinator.isActive(jobKey));

        assertTrue(MmsImageSendCoordinator.submit(
                context,
                jobKey,
                request,
                null,
                new RecordingSender(Collections.singletonList(606L)),
                new RecordingCleaner()
        ));
        waitForCompletion(jobKey);
        assertNotNull(MmsImageSendCoordinator.consume(jobKey));
    }

    @Test
    public void submit_deliversCompletionOnMainThread() throws Exception {
        String jobKey = "main-completion-test-" + System.nanoTime();
        AtomicBoolean completedOnMainThread = new AtomicBoolean(false);
        AtomicReference<MmsImageSendCoordinator.Result> completed = new AtomicReference<>();
        MmsImageSendCoordinator.Listener listener = (completedKey, request, result) -> {
            completedOnMainThread.set(Looper.myLooper() == Looper.getMainLooper());
            completed.set(result);
        };
        MmsImageSendCoordinator.Request request = new MmsImageSendCoordinator.Request(
                "15551234567",
                null,
                "",
                Collections.singletonList(first)
        );

        assertTrue(MmsImageSendCoordinator.submit(
                context,
                jobKey,
                request,
                listener,
                new RecordingSender(Collections.singletonList(606L)),
                new RecordingCleaner()
        ));

        long deadline = System.currentTimeMillis() + 5000L;
        while (completed.get() == null && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10L);
        }
        assertTrue(completedOnMainThread.get());
        assertEquals(606L, completed.get().lastSentAt);
        assertSame(completed.get(), MmsImageSendCoordinator.consume(jobKey));
    }

    @Test
    public void detachAndReattachWhileRunning_deliversOnlyToReplacementListener() throws Exception {
        String jobKey = "running-reattach-test-" + System.nanoTime();
        BlockingSender sender = new BlockingSender(707L);
        AtomicInteger oldCalls = new AtomicInteger();
        AtomicReference<MmsImageSendCoordinator.Result> replacementResult = new AtomicReference<>();
        MmsImageSendCoordinator.Listener oldListener = (key, request, result) -> oldCalls.incrementAndGet();
        MmsImageSendCoordinator.Listener replacementListener =
                (key, request, result) -> replacementResult.set(result);
        MmsImageSendCoordinator.Request request = request(first);

        assertTrue(MmsImageSendCoordinator.submit(
                context,
                jobKey,
                request,
                oldListener,
                sender,
                new RecordingCleaner()
        ));
        assertTrue(sender.entered.await(5, TimeUnit.SECONDS));
        MmsImageSendCoordinator.detach(jobKey, oldListener);
        assertTrue(MmsImageSendCoordinator.attach(jobKey, replacementListener));
        sender.release.countDown();

        waitForResult(replacementResult);
        assertEquals(0, oldCalls.get());
        assertEquals(707L, replacementResult.get().lastSentAt);
        assertNotNull(MmsImageSendCoordinator.consume(jobKey));
    }

    @Test
    public void completedResult_replaysToNewListenerAndSuppressesDetachedQueuedCallback() throws Exception {
        String jobKey = "completed-replay-test-" + System.nanoTime();
        AtomicInteger oldCalls = new AtomicInteger();
        AtomicReference<MmsImageSendCoordinator.Result> replayed = new AtomicReference<>();
        MmsImageSendCoordinator.Listener oldListener = (key, request, result) -> oldCalls.incrementAndGet();
        MmsImageSendCoordinator.Listener replacementListener = (key, request, result) -> replayed.set(result);

        assertTrue(MmsImageSendCoordinator.submit(
                context,
                jobKey,
                request(first),
                oldListener,
                new RecordingSender(Collections.singletonList(808L)),
                new RecordingCleaner()
        ));
        waitForCompletion(jobKey);
        MmsImageSendCoordinator.detach(jobKey, oldListener);
        assertTrue(MmsImageSendCoordinator.attach(jobKey, replacementListener));

        waitForResult(replayed);
        assertEquals(0, oldCalls.get());
        assertEquals(808L, replayed.get().lastSentAt);
        assertNotNull(MmsImageSendCoordinator.consume(jobKey));
    }

    @Test
    public void consume_removesCompletedJobAndItsRetainedRequest() throws Exception {
        String jobKey = "consume-test-" + System.nanoTime();
        MmsImageSendCoordinator.Request request = request(first);
        assertTrue(MmsImageSendCoordinator.submit(
                context,
                jobKey,
                request,
                null,
                new RecordingSender(Collections.singletonList(909L)),
                new RecordingCleaner()
        ));
        assertSame(request, MmsImageSendCoordinator.requestForJob(jobKey));
        waitForCompletion(jobKey);

        MmsImageSendCoordinator.Result consumed = MmsImageSendCoordinator.consume(jobKey);

        assertNotNull(consumed);
        assertEquals(909L, consumed.lastSentAt);
        assertNull(MmsImageSendCoordinator.requestForJob(jobKey));
        assertNull(MmsImageSendCoordinator.consume(jobKey));
        assertFalse(MmsImageSendCoordinator.attach(
                jobKey,
                (key, completedRequest, result) -> { }
        ));
        assertFalse(MmsImageSendCoordinator.isActive(jobKey));
    }

    @Test
    public void successfulRetry_removesReplacedFailedMessageBeforeCompletion() throws Exception {
        String address = "15551234567";
        String failedId = "coordinator-failed-picture";
        saveFailedPicture(failedId, address);
        String jobKey = "retry-replacement-test-" + System.nanoTime();
        MmsImageSendCoordinator.Request request = new MmsImageSendCoordinator.Request(
                address,
                null,
                "old caption",
                Collections.singletonList(first),
                failedId
        );

        assertTrue(MmsImageSendCoordinator.submit(
                context,
                jobKey,
                request,
                null,
                new RecordingSender(Collections.singletonList(1001L)),
                new RecordingCleaner()
        ));
        waitForCompletion(jobKey);

        assertNull(LocalMmsStore.failedMessageForRetry(context, failedId));
        assertNotNull(MmsImageSendCoordinator.consume(jobKey));
    }

    @Test
    public void failedRetry_keepsReplacedFailedMessageAvailable() throws Exception {
        String address = "15551234567";
        String failedId = "coordinator-still-failed-picture";
        saveFailedPicture(failedId, address);
        String jobKey = "retry-failure-test-" + System.nanoTime();
        RecordingSender sender = new RecordingSender(Collections.singletonList(1002L));
        sender.failAtIndex = 0;
        MmsImageSendCoordinator.Request request = new MmsImageSendCoordinator.Request(
                address,
                null,
                "old caption",
                Collections.singletonList(first),
                failedId
        );

        assertTrue(MmsImageSendCoordinator.submit(
                context,
                jobKey,
                request,
                null,
                sender,
                new RecordingCleaner()
        ));
        waitForCompletion(jobKey);

        assertNotNull(LocalMmsStore.failedMessageForRetry(context, failedId));
        assertNotNull(MmsImageSendCoordinator.consume(jobKey));
    }

    private void saveFailedPicture(String failedId, String address) {
        assertTrue(LocalMmsStore.saveSentImage(
                context,
                failedId,
                address,
                "old caption",
                "file:///tmp/" + failedId + ".jpg",
                1000L
        ));
        assertTrue(LocalMmsStore.markSentMessageFailed(context, failedId, address));
        assertNotNull(LocalMmsStore.failedMessageForRetry(context, failedId));
    }

    private MmsImageSendCoordinator.Request request(Uri imageUri) {
        return new MmsImageSendCoordinator.Request(
                "15551234567",
                null,
                "",
                Collections.singletonList(imageUri)
        );
    }

    private void waitForCompletion(String jobKey) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (!MmsImageSendCoordinator.isCompleted(jobKey) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(MmsImageSendCoordinator.isCompleted(jobKey));
    }

    private void waitForResult(AtomicReference<MmsImageSendCoordinator.Result> result) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (result.get() == null && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10L);
        }
        assertNotNull(result.get());
    }

    private static final class BlockingSender implements MmsImageSendCoordinator.ImageSender {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final long sentAt;

        BlockingSender(long sentAt) {
            this.sentAt = sentAt;
        }

        @Override
        public long send(
                Context context,
                String address,
                List<String> explicitRecipients,
                String caption,
                Uri imageUri
        ) throws SmsSender.SendException {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new SmsSender.SendException("test sender timed out");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SmsSender.SendException("test sender was interrupted", ex);
            }
            return sentAt;
        }
    }

    private static final class RecordingSender implements MmsImageSendCoordinator.ImageSender {
        final List<Long> sentTimes;
        final List<Uri> imageUris = new ArrayList<>();
        final List<String> captions = new ArrayList<>();
        final List<List<String>> recipients = new ArrayList<>();
        int failAtIndex = -1;

        RecordingSender(List<Long> sentTimes) {
            this.sentTimes = sentTimes;
        }

        @Override
        public long send(
                Context context,
                String address,
                List<String> explicitRecipients,
                String caption,
                Uri imageUri
        ) throws SmsSender.SendException {
            int index = imageUris.size();
            imageUris.add(imageUri);
            captions.add(caption);
            recipients.add(explicitRecipients);
            if (index == failAtIndex) {
                throw new SmsSender.SendException("test failure " + index);
            }
            return sentTimes.get(index);
        }
    }

    private static final class RecordingCleaner implements MmsImageSendCoordinator.CameraCleaner {
        final List<Uri> cleaned = new ArrayList<>();

        @Override
        public void deleteIfOwned(Context context, Uri imageUri) {
            cleaned.add(imageUri);
        }
    }
}
