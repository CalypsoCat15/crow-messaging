package com.crowmessenger.messages;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MmsFileProvider extends ContentProvider {
    static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".mmsfile";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode == null) {
            throw new FileNotFoundException("MMS file mode is missing");
        }
        if (!mode.contains("w")) {
            return ParcelFileDescriptor.open(outgoingFileForUri(uri), ParcelFileDescriptor.MODE_READ_ONLY);
        }
        File file = fileForUri(uri);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new FileNotFoundException("MMS download directory could not be created");
        }
        int fileMode = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE;
        fileMode |= ParcelFileDescriptor.MODE_TRUNCATE;
        return ParcelFileDescriptor.open(file, fileMode);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.wap.mms-message";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File fileForUri(Uri uri) throws FileNotFoundException {
        if (getContext() == null) {
            throw new FileNotFoundException("No context");
        }
        String name = uri.getLastPathSegment();
        if (TextUtils.isEmpty(name) || !name.endsWith(".pdu") || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new FileNotFoundException("Invalid MMS file name");
        }
        String id = name.substring(0, name.length() - ".pdu".length());
        File downloadDir = MmsFiles.appFileDirPath(getContext(), MmsFiles.DOWNLOADS_DIR);
        File file = new File(downloadDir, name);
        try {
            if (!MmsFiles.isAppFile(getContext(), MmsFiles.DOWNLOADS_DIR, file)) {
                throw new FileNotFoundException("Invalid MMS file path");
            }
            String filePath = file.getCanonicalPath();
            LocalMmsStore.Pending pending = LocalMmsStore.pending(getContext(), id);
            if (TextUtils.isEmpty(pending.pduPath) || !filePath.equals(new File(pending.pduPath).getCanonicalPath())) {
                throw new FileNotFoundException("MMS file is not pending");
            }
        } catch (IOException ignored) {
            throw new FileNotFoundException("Invalid MMS file path");
        }
        return file;
    }

    private File outgoingFileForUri(Uri uri) throws FileNotFoundException {
        if (getContext() == null) {
            throw new FileNotFoundException("No context");
        }
        String name = uri.getLastPathSegment();
        if (TextUtils.isEmpty(name) || !name.endsWith(".pdu") || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new FileNotFoundException("Invalid outgoing MMS file name");
        }
        File outputDir = MmsFiles.appFileDirPath(getContext(), MmsFiles.OUTGOING_DIR);
        File file = new File(outputDir, name);
        try {
            if (!file.exists() || !MmsFiles.isAppFile(getContext(), MmsFiles.OUTGOING_DIR, file)) {
                throw new FileNotFoundException("Outgoing MMS file does not exist");
            }
        } catch (IOException ignored) {
            throw new FileNotFoundException("Invalid outgoing MMS file path");
        }
        return file;
    }

}
