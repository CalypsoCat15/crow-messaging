package com.crowmessenger.messages;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs picture preparation and carrier handoff serially without tying the work to an Activity.
 * In-flight work intentionally lives only for the lifetime of the app process and is never
 * resumed automatically after process death.
 */
final class MmsImageSendCoordinator {
    private static final ExecutorService SEND_EXECUTOR = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "crow-mms-image-send")
    );
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, Job> JOBS = new ConcurrentHashMap<>();

    private static final ImageSender DEFAULT_IMAGE_SENDER = (
            context,
            address,
            explicitRecipients,
            caption,
            imageUri
    ) -> explicitRecipients == null
            ? MmsImageSender.sendAndRecord(context, address, caption, imageUri)
            : MmsImageSender.sendAndRecord(context, address, explicitRecipients, caption, imageUri);

    private static final CameraCleaner DEFAULT_CAMERA_CLEANER =
            MmsImageSendCoordinator::deleteCameraImageIfOwned;

    private MmsImageSendCoordinator() {
    }

    static boolean submit(Context context, String jobKey, Request request, Listener listener) {
        return submit(context, jobKey, request, listener, DEFAULT_IMAGE_SENDER, DEFAULT_CAMERA_CLEANER);
    }

    static boolean submit(
            Context context,
            String jobKey,
            Request request,
            Listener listener,
            ImageSender imageSender,
            CameraCleaner cameraCleaner
    ) {
        if (context == null || TextUtils.isEmpty(jobKey) || request == null
                || imageSender == null || cameraCleaner == null) {
            throw new IllegalArgumentException("Picture send request is incomplete.");
        }
        if (TextUtils.isEmpty(request.address) || request.imageUris.isEmpty()) {
            throw new IllegalArgumentException("Picture send request has no destination or pictures.");
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            throw new IllegalArgumentException("Application context is unavailable.");
        }
        Job job = new Job(request, listener);
        if (JOBS.putIfAbsent(jobKey, job) != null) {
            return false;
        }

        try {
            SEND_EXECUTOR.execute(() -> {
                Result result;
                try {
                    result = sendBatch(appContext, request, imageSender, cameraCleaner);
                } catch (RuntimeException ex) {
                    result = Result.failure(
                            0,
                            0L,
                            request.imageUris,
                            safeError(ex),
                            false
                    );
                }
                if (result.succeeded() && !TextUtils.isEmpty(request.replacedFailedMessageId)) {
                    try {
                        LocalMmsStore.deleteFailedMessageById(appContext, request.replacedFailedMessageId);
                    } catch (RuntimeException ignored) {
                        // The accepted replacement remains authoritative; the live UI retries cleanup.
                    }
                }
                complete(jobKey, job, result);
            });
            return true;
        } catch (RuntimeException ex) {
            JOBS.remove(jobKey, job);
            throw ex;
        }
    }

    static boolean attach(String jobKey, Listener listener) {
        if (TextUtils.isEmpty(jobKey) || listener == null) {
            throw new IllegalArgumentException("Picture send listener is incomplete.");
        }
        Job job = JOBS.get(jobKey);
        if (job == null) {
            return false;
        }
        long listenerVersion;
        boolean completed;
        synchronized (job) {
            job.listener = new WeakReference<>(listener);
            listenerVersion = ++job.listenerVersion;
            completed = job.completedResult != null;
        }
        if (completed) {
            postCompletion(jobKey, job, listenerVersion);
        }
        return JOBS.get(jobKey) == job;
    }

    static void detach(String jobKey, Listener listener) {
        if (TextUtils.isEmpty(jobKey) || listener == null) {
            return;
        }
        Job job = JOBS.get(jobKey);
        if (job == null) {
            return;
        }
        synchronized (job) {
            if (job.listener.get() != listener) {
                return;
            }
            job.listener = new WeakReference<>(null);
            job.listenerVersion++;
        }
    }

    static Result consume(String jobKey) {
        if (TextUtils.isEmpty(jobKey)) {
            return null;
        }
        Job job = JOBS.get(jobKey);
        if (job == null) {
            return null;
        }
        Result completedResult;
        synchronized (job) {
            completedResult = job.completedResult;
            if (completedResult == null || !JOBS.remove(jobKey, job)) {
                return null;
            }
            job.listener = new WeakReference<>(null);
            job.listenerVersion++;
        }
        return completedResult;
    }

    static Request requestForJob(String jobKey) {
        Job job = TextUtils.isEmpty(jobKey) ? null : JOBS.get(jobKey);
        return job == null ? null : job.request;
    }

    static boolean isCompleted(String jobKey) {
        Job job = TextUtils.isEmpty(jobKey) ? null : JOBS.get(jobKey);
        if (job == null) {
            return false;
        }
        synchronized (job) {
            return job.completedResult != null;
        }
    }

    private static void complete(String jobKey, Job job, Result result) {
        long listenerVersion;
        synchronized (job) {
            if (job.completedResult != null || JOBS.get(jobKey) != job) {
                return;
            }
            job.completedResult = result;
            listenerVersion = job.listenerVersion;
        }
        postCompletion(jobKey, job, listenerVersion);
    }

    private static void postCompletion(String jobKey, Job job, long listenerVersion) {
        MAIN_HANDLER.post(() -> {
            Listener listener;
            Result result;
            synchronized (job) {
                if (JOBS.get(jobKey) != job
                        || job.listenerVersion != listenerVersion
                        || job.completedResult == null) {
                    return;
                }
                listener = job.listener.get();
                result = job.completedResult;
            }
            if (listener != null) {
                listener.onCompleted(jobKey, job.request, result);
            }
        });
    }

    /** Package-private synchronous seam used by the serial executor and unit tests. */
    static Result sendBatch(
            Context context,
            Request request,
            ImageSender imageSender,
            CameraCleaner cameraCleaner
    ) {
        if (context == null || request == null || imageSender == null || cameraCleaner == null) {
            throw new IllegalArgumentException("Picture send batch is incomplete.");
        }
        int sentCount = 0;
        long lastSentAt = 0L;
        for (int index = 0; index < request.imageUris.size(); index++) {
            Uri imageUri = request.imageUris.get(index);
            String imageCaption = index == 0 ? request.caption : "";
            try {
                lastSentAt = imageSender.send(
                        context,
                        request.address,
                        request.explicitRecipients,
                        imageCaption,
                        imageUri
                );
                sentCount++;
            } catch (SmsSender.SendException ex) {
                return Result.failure(
                        sentCount,
                        lastSentAt,
                        request.imageUris.subList(index, request.imageUris.size()),
                        safeError(ex),
                        sentCount > 0
                );
            } catch (RuntimeException ex) {
                return Result.failure(
                        sentCount,
                        lastSentAt,
                        request.imageUris.subList(index, request.imageUris.size()),
                        safeError(ex),
                        sentCount > 0
                );
            }

            try {
                cameraCleaner.deleteIfOwned(context, imageUri);
            } catch (RuntimeException ignored) {
                // Cleanup must never turn an accepted carrier handoff into a retryable send failure.
            }
        }
        return Result.success(sentCount, lastSentAt);
    }

    private static String safeError(Exception exception) {
        String detail = exception == null ? "" : exception.getMessage();
        return TextUtils.isEmpty(detail) ? "Picture message could not be sent." : detail;
    }

    static void deleteCameraImageIfOwned(Context context, Uri uri) {
        MmsFiles.deleteCameraFileUri(context, uri);
    }

    static boolean isActive(String jobKey) {
        return !TextUtils.isEmpty(jobKey) && JOBS.containsKey(jobKey);
    }

    interface Listener {
        void onCompleted(String jobKey, Request request, Result result);
    }

    interface ImageSender {
        long send(
                Context context,
                String address,
                List<String> explicitRecipients,
                String caption,
                Uri imageUri
        ) throws SmsSender.SendException;
    }

    interface CameraCleaner {
        void deleteIfOwned(Context context, Uri imageUri);
    }

    private static final class Job {
        final Request request;
        Result completedResult;
        WeakReference<Listener> listener;
        long listenerVersion;

        Job(Request request, Listener listener) {
            this.request = request;
            this.listener = new WeakReference<>(listener);
        }
    }

    static final class Request {
        final String address;
        final List<String> explicitRecipients;
        final String caption;
        final List<Uri> imageUris;
        final String replacedFailedMessageId;

        Request(String address, List<String> explicitRecipients, String caption, List<Uri> imageUris) {
            this(address, explicitRecipients, caption, imageUris, "");
        }

        Request(
                String address,
                List<String> explicitRecipients,
                String caption,
                List<Uri> imageUris,
                String replacedFailedMessageId
        ) {
            this.address = address == null ? "" : address;
            this.explicitRecipients = explicitRecipients == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(explicitRecipients));
            this.caption = caption == null ? "" : caption;
            this.imageUris = imageUris == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(imageUris));
            this.replacedFailedMessageId = replacedFailedMessageId == null
                    ? ""
                    : replacedFailedMessageId;
        }
    }

    static final class Result {
        final int sentCount;
        final long lastSentAt;
        final List<Uri> remainingUris;
        final String error;
        final boolean captionConsumed;

        private Result(
                int sentCount,
                long lastSentAt,
                List<Uri> remainingUris,
                String error,
                boolean captionConsumed
        ) {
            this.sentCount = Math.max(0, sentCount);
            this.lastSentAt = lastSentAt;
            this.remainingUris = remainingUris == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(remainingUris));
            this.error = error == null ? "" : error;
            this.captionConsumed = captionConsumed;
        }

        static Result success(int sentCount, long lastSentAt) {
            return new Result(sentCount, lastSentAt, Collections.emptyList(), "", sentCount > 0);
        }

        static Result failure(
                int sentCount,
                long lastSentAt,
                List<Uri> remainingUris,
                String error,
                boolean captionConsumed
        ) {
            return new Result(sentCount, lastSentAt, remainingUris, error, captionConsumed);
        }

        boolean succeeded() {
            return TextUtils.isEmpty(error) && remainingUris.isEmpty();
        }
    }
}
