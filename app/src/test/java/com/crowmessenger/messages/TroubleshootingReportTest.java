package com.crowmessenger.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TroubleshootingReportTest {
    @Test
    public void privateMmsEvents_removesPhoneSuffixLinksAndFileIds() {
        String events = "sender=***4567 url=https://example.com/private file=secret-id.pdu";

        String privateEvents = TroubleshootingReport.privateMmsEvents(events);

        assertFalse(privateEvents.contains("4567"));
        assertFalse(privateEvents.contains("example.com"));
        assertFalse(privateEvents.contains("secret-id"));
        assertTrue(privateEvents.contains("***"));
    }

    @Test
    public void privateMmsEvents_redactsRawFormattedPhoneNumbers() {
        String events = "sender=+1 (555) 123-4567 imageBytes=1234567";

        String privateEvents = TroubleshootingReport.privateMmsEvents(events);

        assertFalse(privateEvents.contains("555"));
        assertFalse(privateEvents.contains("123-4567"));
        assertTrue(privateEvents.contains("sender=***"));
        assertTrue(privateEvents.contains("imageBytes=1234567"));
    }

    @Test
    public void privateMmsEvents_redactsLegacySenderIdsEmailsAndUrlParts() {
        String events = "sender=CROW ALERTS, path=/private/account-token, host=carrier.example\n"
                + "sender=private@example.com bytes=42\n"
                + "Starting MMS download for CROW ALERTS on subId=1";

        String privateEvents = TroubleshootingReport.privateMmsEvents(events);

        assertFalse(privateEvents.contains("CROW ALERTS"));
        assertFalse(privateEvents.contains("private@example.com"));
        assertFalse(privateEvents.contains("account-token"));
        assertFalse(privateEvents.contains("carrier.example"));
        assertTrue(privateEvents.contains("bytes=42"));
        assertTrue(privateEvents.contains("subId=1"));
    }

    @Test
    public void create_includesPrivacyStatementAndNoStoredMessageBody() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("local_mms", Context.MODE_PRIVATE).edit().clear().commit();
        LocalMmsStore.saveNotice(context, "15551234567", "PRIVATE MESSAGE BODY", 1000L);

        String report = TroubleshootingReport.create(context);

        assertTrue(report.contains("Privacy:"));
        assertTrue(report.contains("Local MMS records: total=1"));
        assertFalse(report.contains("15551234567"));
        assertFalse(report.contains("PRIVATE MESSAGE BODY"));
    }
}
