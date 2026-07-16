package com.crowmessenger.messages;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;

import com.bumptech.glide.gifencoder.AnimatedGifEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class GifMmsCompressor {
    private static final double[] SCALE_FACTORS = { 1.0, 0.80, 0.64 };
    private static final int[] FRAME_INTERVAL_ATTEMPTS_MS = { 180, 240, 320 };
    private static final int MAX_FRAMES = 8;

    private GifMmsCompressor() {
    }

    static byte[] compress(byte[] source, int maxBytes) throws IOException {
        if (source == null || source.length == 0 || maxBytes <= 0) {
            throw new IOException("GIF data is empty");
        }
        if (source.length <= maxBytes) {
            return source;
        }
        byte[] frameReduced = GifFrameReducer.reduce(source, maxBytes);
        if (frameReduced != null) {
            return frameReduced;
        }
        Movie movie = Movie.decodeByteArray(source, 0, source.length);
        if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
            throw new IOException("GIF could not be decoded");
        }
        double estimatedScale = Math.min(
                0.38,
                Math.sqrt(maxBytes / (double) source.length) * 0.55
        );
        byte[] smallest = new byte[0];
        for (int attempt = 0; attempt < SCALE_FACTORS.length; attempt++) {
            byte[] encoded = encode(
                    movie,
                    Math.max(0.30, estimatedScale * SCALE_FACTORS[attempt]),
                    FRAME_INTERVAL_ATTEMPTS_MS[attempt]
            );
            if (smallest.length == 0 || encoded.length < smallest.length) {
                smallest = encoded;
            }
            if (encoded.length <= maxBytes) {
                return encoded;
            }
        }
        throw new IOException("GIF could not be reduced below " + maxBytes + " bytes; smallest=" + smallest.length);
    }

    private static byte[] encode(Movie movie, double scale, int minimumFrameIntervalMillis) throws IOException {
        int width = Math.max(1, (int) Math.round(movie.width() * scale));
        int height = Math.max(1, (int) Math.round(movie.height() * scale));
        int duration = movie.duration() > 0 ? movie.duration() : 1000;
        int frameInterval = Math.max(
                minimumFrameIntervalMillis,
                (int) Math.ceil(duration / (double) MAX_FRAMES)
        );
        int frameCount = Math.max(2, (int) Math.ceil(duration / (double) frameInterval));
        frameInterval = Math.max(20, (int) Math.ceil(duration / (double) frameCount));

        Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(frame);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            AnimatedGifEncoder encoder = new AnimatedGifEncoder();
            encoder.setSize(width, height);
            encoder.setRepeat(0);
            encoder.setDispose(2);
            encoder.setQuality(20);
            if (!encoder.start(output)) {
                throw new IOException("GIF encoder could not start");
            }
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                int time = Math.min(duration - 1, frameIndex * frameInterval);
                frame.eraseColor(Color.TRANSPARENT);
                movie.setTime(Math.max(0, time));
                canvas.save();
                canvas.scale((float) width / movie.width(), (float) height / movie.height());
                movie.draw(canvas, 0f, 0f, paint);
                canvas.restore();
                encoder.setDelay(frameInterval);
                if (!encoder.addFrame(frame)) {
                    throw new IOException("GIF frame could not be encoded");
                }
            }
            if (!encoder.finish()) {
                throw new IOException("GIF encoder could not finish");
            }
            return output.toByteArray();
        } finally {
            frame.recycle();
        }
    }
}
