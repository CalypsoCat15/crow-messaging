package com.crowmessenger.messages;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;

final class MmsFiles {
    private static final long STALE_PDU_AGE_MILLIS = 24L * 60L * 60L * 1000L;
    private static final long STALE_CAMERA_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    static final String CAMERA_AUTHORITY = BuildConfig.APPLICATION_ID + ".camera";
    static final String DOWNLOADS_DIR = "mms-downloads";
    static final String IMAGES_DIR = "mms-images";
    static final String CAMERA_DIR = "mms-camera";
    static final String NOTICES_DIR = "mms-notices";
    static final String OUTGOING_DIR = "mms-outgoing";
    static final String RAW_DOWNLOADS_DIR = "mms-raw-downloads";
    static final String UNREADABLE_DIR = "mms-unreadable";

    private MmsFiles() {
    }

    static File appFileDirPath(Context context, String name) {
        return new File(context.getFilesDir(), name);
    }

    static File appFileDir(Context context, String name) throws IOException {
        File outputDir = appFileDirPath(context, name);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Could not create " + name);
        }
        return outputDir;
    }

    static void deleteAppFile(Context context, String dirName, String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return;
        }
        try {
            File file = new File(filePath);
            if (isAppFile(context, dirName, file)) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }

    static boolean isAppFile(Context context, String dirName, File file) throws IOException {
        if (file == null) {
            return false;
        }
        File rootDir = appFileDirPath(context, dirName);
        String fileCanonicalPath = file.getCanonicalPath();
        String rootCanonicalPath = rootDir.getCanonicalPath() + File.separator;
        return fileCanonicalPath.startsWith(rootCanonicalPath);
    }

    static void deleteAppFileUri(Context context, String dirName, String fileUri) {
        if (TextUtils.isEmpty(fileUri)) {
            return;
        }
        Uri uri = Uri.parse(fileUri);
        if (!"file".equals(uri.getScheme()) || TextUtils.isEmpty(uri.getPath())) {
            return;
        }
        deleteAppFile(context, dirName, uri.getPath());
    }

    static int cleanupStaleTemporaryFiles(Context context) {
        long now = System.currentTimeMillis();
        return deleteFilesOlderThan(context, OUTGOING_DIR, ".pdu", now - STALE_PDU_AGE_MILLIS)
                + deleteFilesOlderThan(context, DOWNLOADS_DIR, ".pdu", now - STALE_PDU_AGE_MILLIS)
                + deleteFilesOlderThan(context, CAMERA_DIR, "", now - STALE_CAMERA_AGE_MILLIS);
    }

    private static int deleteFilesOlderThan(Context context, String dirName, String suffix, long cutoffMillis) {
        File directory = appFileDirPath(context, dirName);
        File[] files = directory.listFiles(file -> file.isFile()
                && (TextUtils.isEmpty(suffix) || file.getName().endsWith(suffix)));
        if (files == null) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            long modifiedAt = file.lastModified();
            if (modifiedAt <= 0 || modifiedAt >= cutoffMillis) {
                continue;
            }
            if (isAppFileUnchecked(context, dirName, file) && file.delete()) {
                deleted++;
            }
        }
        return deleted;
    }

    private static boolean isAppFileUnchecked(Context context, String dirName, File file) {
        try {
            return isAppFile(context, dirName, file);
        } catch (IOException ignored) {
            return false;
        }
    }
}
