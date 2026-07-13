package com.crowmessenger.messages;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsManager;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

final class MmsTextSender {
    private MmsTextSender() {
    }

    static long sendAndRecord(Context context, String conversationAddress, String body) throws SmsSender.SendException {
        List<String> recipients = MmsImageSender.recipientsForAddress(
                conversationAddress,
                GroupMmsRecipients.knownOwnNumbers(context)
        );
        return sendAndRecord(context, conversationAddress, recipients, body);
    }

    static long sendAndRecord(
            Context context,
            String conversationAddress,
            List<String> recipients,
            String body
    ) throws SmsSender.SendException {
        return sendAndRecord(context, conversationAddress, recipients, body, null);
    }

    static long sendAndRecord(
            Context context,
            String conversationAddress,
            List<String> recipients,
            String body,
            MmsCarrierGateway gateway
    ) throws SmsSender.SendException {
        if (!LocalMmsStore.isGroupAddress(conversationAddress)) {
            throw new SmsSender.SendException("Group text needs a group conversation.");
        }
        List<String> normalizedRecipients = normalizedRecipients(recipients);
        if (!GroupMmsRecipients.hasEnoughRecipientsForGroupMms(normalizedRecipients)) {
            throw new SmsSender.SendException("Crow Messenger could not find enough people in this group.");
        }
        String cleanBody = TextUtils.isEmpty(body) ? "" : body.trim();
        if (TextUtils.isEmpty(cleanBody)) {
            throw new SmsSender.SendException("Message is empty.");
        }
        MmsCarrierGateway carrierGateway = gateway;
        if (carrierGateway == null) {
            SmsManager smsManager = context.getSystemService(SmsManager.class);
            if (smsManager == null) {
                throw new SmsSender.SendException("Group messaging is not available on this phone right now.");
            }
            carrierGateway = (sendContext, pduUri, sentIntent) -> smsManager.sendMultimediaMessage(
                    sendContext,
                    pduUri,
                    null,
                    null,
                    sentIntent
            );
        }

        String id = UUID.randomUUID().toString();
        File outgoingPdu = writePdu(context, id, normalizedRecipients, cleanBody);
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
                            .putExtra(MmsSentReceiver.EXTRA_LOCAL_MESSAGE_ID, id),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } catch (RuntimeException ex) {
            MmsFiles.deleteAppFile(context, MmsFiles.OUTGOING_DIR, outgoingPdu.getAbsolutePath());
            String detail = ex.getMessage();
            throw new SmsSender.SendException(TextUtils.isEmpty(detail) ? "Android could not send the group text." : detail, ex);
        }

        long sentAt = System.currentTimeMillis();
        if (!LocalMmsStore.saveSentText(context, id, conversationAddress, cleanBody, sentAt)) {
            rollbackPreparedMessage(context, id, conversationAddress, outgoingPdu);
            throw new SmsSender.SendException("Group text could not be saved before sending.");
        }
        try {
            carrierGateway.send(context, pduUri, sentIntent);
        } catch (RuntimeException ex) {
            rollbackPreparedMessage(context, id, conversationAddress, outgoingPdu);
            String detail = ex.getMessage();
            throw new SmsSender.SendException(TextUtils.isEmpty(detail) ? "Android could not send the group text." : detail, ex);
        }
        return sentAt;
    }

    private static void rollbackPreparedMessage(Context context, String id, String address, File outgoingPdu) {
        if (!LocalMmsStore.rollbackSentMessage(context, id, address)) {
            LocalMmsStore.markSentMessageFailed(context, id, address);
        }
        MmsFiles.deleteAppFile(context, MmsFiles.OUTGOING_DIR, outgoingPdu.getAbsolutePath());
    }

    static List<String> normalizedRecipients(List<String> recipients) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (recipients != null) {
            for (String recipient : recipients) {
                GroupMmsRecipients.addUniqueRecipient(
                        normalized,
                        GroupMmsRecipients.normalizedRecipient(recipient)
                );
            }
        }
        return new ArrayList<>(normalized);
    }

    private static File writePdu(
            Context context,
            String id,
            List<String> recipients,
            String body
    ) throws SmsSender.SendException {
        File directory;
        try {
            directory = MmsFiles.appFileDir(context, MmsFiles.OUTGOING_DIR);
        } catch (Exception ex) {
            throw new SmsSender.SendException("Group message storage could not be prepared.", ex);
        }
        File file = new File(directory, id + ".pdu");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(MmsTextPduComposer.compose("tx-" + id, recipients, body));
            return file;
        } catch (Exception ex) {
            file.delete();
            throw new SmsSender.SendException("Group text could not be prepared.", ex);
        }
    }
}
