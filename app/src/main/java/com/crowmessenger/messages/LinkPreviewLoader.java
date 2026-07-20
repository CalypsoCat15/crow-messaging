package com.crowmessenger.messages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LinkPreviewLoader {
    private static final int CONNECT_TIMEOUT_MILLIS = 4_000;
    private static final int READ_TIMEOUT_MILLIS = 5_000;
    private static final int MAX_REDIRECTS = 4;
    private static final int MAX_HTML_BYTES = 384 * 1_024;
    private static final int MAX_IMAGE_BYTES = 2 * 1_024 * 1_024;
    private static final int MAX_PENDING_REQUESTS = 32;
    private static final int MAX_BITMAP_WIDTH = 720;
    private static final int MAX_BITMAP_HEIGHT = 480;
    private static final String USER_AGENT = "Crow Messenger/0.1 Link Preview";
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "crow-link-preview");
        thread.setDaemon(true);
        return thread;
    });
    private static final LruCache<String, Result> CACHE = new LruCache<String, Result>(16 * 1_024) {
        @Override
        protected int sizeOf(String key, Result value) {
            if (value == null || value.image == null) {
                return 1;
            }
            return Math.max(1, value.image.getAllocationByteCount() / 1_024);
        }
    };
    private static final LruCache<String, Boolean> FAILURES = new LruCache<>(64);
    private static final Map<String, List<Callback>> PENDING = new HashMap<>();

    interface Callback {
        void onLoaded(Result result);
    }

    static final class Result {
        final LinkPreview preview;
        final Bitmap image;

        Result(LinkPreview preview, Bitmap image) {
            this.preview = preview;
            this.image = image;
        }
    }

    private LinkPreviewLoader() {
    }

    static void load(String rawUrl, Callback callback) {
        String fetchUrl = LinkPreviewUrlPolicy.normalizedFetchUrl(rawUrl);
        if (fetchUrl.isEmpty()) {
            MAIN.post(() -> callback.onLoaded(null));
            return;
        }
        Result cached;
        boolean startRequest = false;
        synchronized (LOCK) {
            cached = CACHE.get(fetchUrl);
            if (cached == null && FAILURES.get(fetchUrl) == null) {
                List<Callback> callbacks = PENDING.get(fetchUrl);
                if (callbacks == null) {
                    if (PENDING.size() >= MAX_PENDING_REQUESTS) {
                        MAIN.post(() -> callback.onLoaded(null));
                        return;
                    }
                    callbacks = new ArrayList<>();
                    PENDING.put(fetchUrl, callbacks);
                    startRequest = true;
                }
                callbacks.add(callback);
            }
        }
        if (cached != null) {
            Result result = cached;
            MAIN.post(() -> callback.onLoaded(result));
            return;
        }
        synchronized (LOCK) {
            if (FAILURES.get(fetchUrl) != null) {
                MAIN.post(() -> callback.onLoaded(null));
                return;
            }
        }
        if (startRequest) {
            EXECUTOR.submit(() -> complete(fetchUrl, fetch(fetchUrl)));
        }
    }

    private static void complete(String fetchUrl, Result result) {
        List<Callback> callbacks;
        synchronized (LOCK) {
            if (result == null) {
                FAILURES.put(fetchUrl, Boolean.TRUE);
            } else {
                CACHE.put(fetchUrl, result);
            }
            callbacks = PENDING.remove(fetchUrl);
        }
        if (callbacks == null) {
            return;
        }
        MAIN.post(() -> {
            for (Callback callback : callbacks) {
                callback.onLoaded(result);
            }
        });
    }

    private static Result fetch(String fetchUrl) {
        try {
            FetchResponse response = fetchBytes(fetchUrl, MAX_HTML_BYTES, "text/html", "application/xhtml+xml");
            if (response == null) {
                return null;
            }
            Document document = Jsoup.parse(
                    new ByteArrayInputStream(response.body),
                    null,
                    response.finalUrl
            );
            LinkPreview preview = LinkPreviewParser.parse(document, response.finalUrl);
            if (!preview.hasPreviewContent()) {
                return null;
            }
            Bitmap image = fetchImage(preview.imageUrl);
            return new Result(preview, image);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static Bitmap fetchImage(String rawImageUrl) {
        String imageUrl = LinkPreviewUrlPolicy.normalizedFetchUrl(rawImageUrl);
        if (imageUrl.isEmpty()) {
            return null;
        }
        try {
            FetchResponse response = fetchBytes(imageUrl, MAX_IMAGE_BYTES, "image/");
            if (response == null) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(response.body, 0, response.body.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sampleSize = 1;
            while (bounds.outWidth / sampleSize > MAX_BITMAP_WIDTH
                    || bounds.outHeight / sampleSize > MAX_BITMAP_HEIGHT) {
                sampleSize *= 2;
            }
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sampleSize;
            return BitmapFactory.decodeByteArray(response.body, 0, response.body.length, decode);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static FetchResponse fetchBytes(
            String startUrl,
            int maximumBytes,
            String... acceptedContentTypes
    ) throws IOException {
        URI current = URI.create(startUrl);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (!"https".equalsIgnoreCase(current.getScheme())
                    || !LinkPreviewUrlPolicy.isPublicHost(current.getHost())) {
                return null;
            }
            HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
            try {
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(true);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", acceptedHeader(acceptedContentTypes));
                int status = connection.getResponseCode();
                if (isRedirect(status)) {
                    String location = connection.getHeaderField("Location");
                    String redirected = location == null
                            ? ""
                            : LinkPreviewUrlPolicy.normalizedFetchUrl(current.resolve(location).toString());
                    if (redirected.isEmpty()) {
                        return null;
                    }
                    current = URI.create(redirected);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    return null;
                }
                String contentType = connection.getContentType();
                if (!isAcceptedContentType(contentType, acceptedContentTypes)) {
                    return null;
                }
                long contentLength = connection.getContentLengthLong();
                if (contentLength > maximumBytes) {
                    return null;
                }
                try (InputStream input = connection.getInputStream()) {
                    byte[] body = readLimited(input, maximumBytes);
                    return body == null
                            ? null
                            : new FetchResponse(current.toString(), body);
                }
            } finally {
                connection.disconnect();
            }
        }
        return null;
    }

    private static byte[] readLimited(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 32 * 1_024));
        byte[] buffer = new byte[8 * 1_024];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximumBytes) {
                return null;
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    private static String acceptedHeader(String... acceptedContentTypes) {
        return acceptedContentTypes.length == 1 && "image/".equals(acceptedContentTypes[0])
                ? "image/*"
                : "text/html,application/xhtml+xml";
    }

    private static boolean isAcceptedContentType(String contentType, String... accepted) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return accepted.length > 0 && "text/html".equals(accepted[0]);
        }
        String normalized = contentType.toLowerCase(java.util.Locale.ROOT);
        for (String prefix : accepted) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static final class FetchResponse {
        final String finalUrl;
        final byte[] body;

        FetchResponse(String finalUrl, byte[] body) {
            this.finalUrl = finalUrl;
            this.body = body;
        }
    }
}
