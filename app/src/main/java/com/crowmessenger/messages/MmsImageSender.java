package com.crowmessenger.messages;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.telephony.SmsManager;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import androidx.exifinterface.media.ExifInterface;

final class MmsImageSender {
    private static final int MAX_IMAGE_BYTES = 900 * 1024;
    private static final int MAX_IMAGE_EDGE = 1280;

    private MmsImageSender() {
    }

    static long sendAndRecord(Context context, String address, String caption, Uri sourceImageUri) throws SmsSender.SendException {
        if (TextUtils.isEmpty(address)) {
            throw new SmsSender.SendException("No recipient was selected.");
        }
        List<String> recipients = recipientsForAddress(address, GroupMmsRecipients.knownOwnNumbers(context));
        return sendAndRecord(context, address, recipients, caption, sourceImageUri);
    }

    static long sendAndRecord(Context context, String conversationAddress, List<String> recipients, String caption, Uri sourceImageUri) throws SmsSender.SendException {
        return sendAndRecord(context, conversationAddress, recipients, caption, sourceImageUri, null);
    }

    static long sendAndRecord(
            Context context,
            String conversationAddress,
            List<String> recipients,
            String caption,
            Uri sourceImageUri,
            MmsCarrierGateway gateway
    ) throws SmsSender.SendException {
        if (TextUtils.isEmpty(conversationAddress)) {
            throw new SmsSender.SendException("No recipient was selected.");
        }
        List<String> normalizedRecipients = normalizedRecipients(recipients);
        if (normalizedRecipients.isEmpty()) {
            throw new SmsSender.SendException("No recipient was selected.");
        }
        if (sourceImageUri == null) {
            throw new SmsSender.SendException("No picture was selected.");
        }
        MmsCarrierGateway carrierGateway = gateway;
        if (carrierGateway == null) {
            SmsManager smsManager = context.getSystemService(SmsManager.class);
            if (smsManager == null) {
                throw new SmsSender.SendException("Picture messaging is not available on this phone right now.");
            }
            carrierGateway = (sendContext, pduUri, sentIntent) -> smsManager.sendMultimediaMessage(
                    sendContext,
                    pduUri,
                    null,
                    null,
                    sentIntent
            );
        }

        byte[] imageBytes = preparedJpeg(context, sourceImageUri);
        String id = UUID.randomUUID().toString();
        File localImage = writeFile(appFileDir(context, MmsFiles.IMAGES_DIR), id + ".jpg", imageBytes);
        File outgoingPdu;
        try {
            outgoingPdu = writeFile(
                    appFileDir(context, MmsFiles.OUTGOING_DIR),
                    id + ".pdu",
                    MmsTextPduComposer.composeImage("tx-" + id, normalizedRecipients, caption, imageBytes)
            );
        } catch (SmsSender.SendException ex) {
            MmsFiles.deleteAppFile(context, MmsFiles.IMAGES_DIR, localImage.getAbsolutePath());
            throw ex;
        } catch (RuntimeException ex) {
            MmsFiles.deleteAppFile(context, MmsFiles.IMAGES_DIR, localImage.getAbsolutePath());
            throw new SmsSender.SendException("Picture message could not be prepared.", ex);
        }

        String localImageUri = Uri.fromFile(localImage).toString();
        Uri pduUri = Uri.parse("content://" + MmsFileProvider.AUTHORITY + "/" + outgoingPdu.getName());
        PendingIntent sentIntent;
        try {
            sentIntent = PendingIntent.getBroadcast(
                    context,
                    AddressUtil.stableId(id),
                    new Intent(context, MmsSentReceiver.class)
                            .setAction(MmsSentReceiver.ACTION_MMS_SENT)
                            .putExtra(MmsSentReceiver.EXTRA_PDU_NAME, outgoingPdu.getName())
                            .putExtra(MmsSentReceiver.EXTRA_ADDRESS, conversationAddress)
                            .putExtra(MmsSentReceiver.EXTRA_IMAGE_URI, localImageUri)
                            .putExtra(MmsSentReceiver.EXTRA_LOCAL_MESSAGE_ID, id),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } catch (RuntimeException ex) {
            MmsFiles.deleteAppFile(context, MmsFiles.OUTGOING_DIR, outgoingPdu.getAbsolutePath());
            MmsFiles.deleteAppFile(context, MmsFiles.IMAGES_DIR, localImage.getAbsolutePath());
            String detail = ex.getMessage();
            throw new SmsSender.SendException(TextUtils.isEmpty(detail) ? "Android could not send the picture." : detail, ex);
        }

        long sentAt = System.currentTimeMillis();
        if (!LocalMmsStore.saveSentImage(context, id, conversationAddress, caption, localImageUri, sentAt)) {
            rollbackPreparedMessage(context, id, conversationAddress, outgoingPdu, localImage);
            throw new SmsSender.SendException("Picture message could not be saved before sending.");
        }
        try {
            carrierGateway.send(context, pduUri, sentIntent);
        } catch (RuntimeException ex) {
            rollbackPreparedMessage(context, id, conversationAddress, outgoingPdu, localImage);
            String detail = ex.getMessage();
            throw new SmsSender.SendException(TextUtils.isEmpty(detail) ? "Android could not send the picture." : detail, ex);
        }
        return sentAt;
    }

    private static void rollbackPreparedMessage(Context context, String id, String address, File outgoingPdu, File localImage) {
        boolean removed = LocalMmsStore.rollbackSentMessage(context, id, address);
        MmsFiles.deleteAppFile(context, MmsFiles.OUTGOING_DIR, outgoingPdu.getAbsolutePath());
        if (!removed) {
            LocalMmsStore.markSentMessageFailed(context, id, address);
            if (LocalMmsStore.failedMessageForRetry(context, id) == null) {
                MmsFiles.deleteAppFile(context, MmsFiles.IMAGES_DIR, localImage.getAbsolutePath());
            }
        }
    }

    static List<String> recipientsForAddress(String address, java.util.Set<String> ownNumbers) throws SmsSender.SendException {
        if (TextUtils.isEmpty(address)) {
            throw new SmsSender.SendException("No recipient was selected.");
        }
        if (!LocalMmsStore.isGroupAddress(address)) {
            return Collections.singletonList(address);
        }
        List<String> recipients = GroupMmsRecipients.remoteRecipients(address, ownNumbers);
        if (!GroupMmsRecipients.hasEnoughRecipientsForGroupMms(recipients)) {
            throw new SmsSender.SendException("Crow Messenger could not find enough people in this group to send the picture.");
        }
        return recipients;
    }

    private static List<String> normalizedRecipients(List<String> recipients) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (recipients != null) {
            for (String recipient : recipients) {
                GroupMmsRecipients.addUniqueRecipient(normalized, GroupMmsRecipients.normalizedRecipient(recipient));
            }
        }
        return new ArrayList<>(normalized);
    }

    private static File appFileDir(Context context, String name) throws SmsSender.SendException {
        try {
            return MmsFiles.appFileDir(context, name);
        } catch (IOException ex) {
            throw new SmsSender.SendException("Picture message storage could not be prepared.", ex);
        }
    }

    private static byte[] preparedJpeg(Context context, Uri sourceImageUri) throws SmsSender.SendException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = context.getContentResolver().openInputStream(sourceImageUri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        } catch (Exception ex) {
            throw new SmsSender.SendException("Picture could not be opened.", ex);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new SmsSender.SendException("Picture could not be read.");
        }

        int exifOrientation = readExifOrientation(context, sourceImageUri);
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap;
        try (InputStream stream = context.getContentResolver().openInputStream(sourceImageUri)) {
            bitmap = BitmapFactory.decodeStream(stream, null, decode);
        } catch (Exception ex) {
            throw new SmsSender.SendException("Picture could not be prepared.", ex);
        }
        if (bitmap == null) {
            throw new SmsSender.SendException("Picture could not be prepared.");
        }

        Bitmap prepared;
        try {
            prepared = transformBitmapForExif(bitmap, exifOrientation);
        } catch (RuntimeException ex) {
            bitmap.recycle();
            throw new SmsSender.SendException("Picture could not be rotated.", ex);
        }
        try {
            return compressJpeg(prepared);
        } finally {
            prepared.recycle();
            if (prepared != bitmap) {
                bitmap.recycle();
            }
        }
    }

    private static int readExifOrientation(Context context, Uri sourceImageUri) {
        try (InputStream stream = context.getContentResolver().openInputStream(sourceImageUri)) {
            if (stream == null) {
                return 0;
            }
            ExifInterface exif = new ExifInterface(stream);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap transformBitmapForExif(Bitmap bitmap, int orientation) {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static int sampleSize(int width, int height) {
        int sampleSize = 1;
        while (Math.max(width / sampleSize, height / sampleSize) > MAX_IMAGE_EDGE) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static byte[] compressJpeg(Bitmap bitmap) throws SmsSender.SendException {
        for (int quality = 88; quality >= 48; quality -= 8) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                continue;
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length <= MAX_IMAGE_BYTES || quality == 48) {
                return bytes;
            }
        }
        throw new SmsSender.SendException("Picture could not be compressed.");
    }

    private static File writeFile(File directory, String name, byte[] bytes) throws SmsSender.SendException {
        File file = new File(directory, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            return file;
        } catch (Exception ex) {
            file.delete();
            throw new SmsSender.SendException("Picture message could not be prepared.", ex);
        }
    }
}
