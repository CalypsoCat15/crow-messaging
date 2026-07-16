package com.crowmessenger.messages;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MmsPduUtilTest {
    private static final int CONTENT_TYPE_MULTIPART_MIXED = 0xA3;

    @Test
    public void extractFirstImage_handlesEmptyInput() {
        assertArrayEquals(new byte[0], MmsPduUtil.extractFirstImage(null));
        assertArrayEquals(new byte[0], MmsPduUtil.extractFirstImage(new byte[0]));
    }

    @Test
    public void parseNotification_handlesEmptyInput() {
        MmsPduUtil.NotificationInfo nullInfo = MmsPduUtil.parseNotification(null);
        assertEquals("", nullInfo.downloadUrl);
        assertEquals("", nullInfo.sender);
        assertEquals("", nullInfo.transactionId);
        assertEquals(false, nullInfo.parsedHeaders);

        MmsPduUtil.NotificationInfo emptyInfo = MmsPduUtil.parseNotification(new byte[0]);
        assertEquals("", emptyInfo.downloadUrl);
        assertEquals("", emptyInfo.sender);
        assertEquals("", emptyInfo.transactionId);
        assertEquals(false, emptyInfo.parsedHeaders);
    }

    @Test
    public void findHeaderValues_handleEmptyInput() {
        assertEquals("", MmsPduUtil.findDownloadUrl(null));
        assertEquals("", MmsPduUtil.findDownloadUrl(new byte[0]));
        assertEquals("", MmsPduUtil.findSender(null));
        assertEquals("", MmsPduUtil.findSender(new byte[0]));
    }

    @Test
    public void cleanDisplayText_keepsNormalCaptions() {
        assertEquals("See you soon!", MmsPduUtil.cleanDisplayText("  See you soon!  "));
        assertEquals("I'm here.", MmsPduUtil.cleanDisplayText("I'm here."));
        assertEquals("zzz", MmsPduUtil.cleanDisplayText("zzz"));
        assertEquals("The image width looks wrong.", MmsPduUtil.cleanDisplayText("The image width looks wrong."));
        assertEquals("Text width matters here.", MmsPduUtil.cleanDisplayText("Text width matters here."));
        assertEquals("Width is 240.", MmsPduUtil.cleanDisplayText("Width is 240."));
        assertEquals("Height looks fine.", MmsPduUtil.cleanDisplayText("Height looks fine."));
        assertEquals("100% width matters here.", MmsPduUtil.cleanDisplayText("100% width matters here."));
        assertEquals("This region looks good.", MmsPduUtil.cleanDisplayText("This region looks good."));
        assertEquals("Region looks good.", MmsPduUtil.cleanDisplayText("Region looks good."));
        assertEquals("The region ID looks wrong.", MmsPduUtil.cleanDisplayText("The region ID looks wrong."));
        assertEquals("These fit perfectly.", MmsPduUtil.cleanDisplayText("These fit perfectly."));
        assertEquals("Fit looks right.", MmsPduUtil.cleanDisplayText("Fit looks right."));
        assertEquals("End: soon.", MmsPduUtil.cleanDisplayText("End: soon."));
        assertEquals("Begin again soon.", MmsPduUtil.cleanDisplayText("Begin again soon."));
        assertEquals("See you", MmsPduUtil.cleanDisplayText("See=20you"));
        assertEquals("café tomorrow", MmsPduUtil.cleanDisplayText("café tomorrow"));
        assertEquals("Pokémon photo", MmsPduUtil.cleanDisplayText("Pokémon photo"));
    }

    @Test
    public void cleanDisplayText_dropsKnownMmsJunk() {
        assertEquals("", MmsPduUtil.cleanDisplayText("insert-address-token"));
        assertEquals("", MmsPduUtil.cleanDisplayText("type=plmn"));
        assertEquals("", MmsPduUtil.cleanDisplayText("ext123456."));
        assertEquals("", MmsPduUtil.cleanDisplayText("application"));
        assertEquals("", MmsPduUtil.cleanDisplayText("application."));
        assertEquals("", MmsPduUtil.cleanDisplayText("smil"));
        assertEquals("", MmsPduUtil.cleanDisplayText("par"));
        assertEquals("", MmsPduUtil.cleanDisplayText("Image"));
        assertEquals("", MmsPduUtil.cleanDisplayText("Text"));
        assertEquals("", MmsPduUtil.cleanDisplayText("par dur"));
        assertEquals("", MmsPduUtil.cleanDisplayText("region"));
        assertEquals("", MmsPduUtil.cleanDisplayText("ref"));
        assertEquals("", MmsPduUtil.cleanDisplayText("root-layout width"));
        assertEquals("", MmsPduUtil.cleanDisplayText("layout height"));
        assertEquals("", MmsPduUtil.cleanDisplayText("left=0"));
        assertEquals("", MmsPduUtil.cleanDisplayText("top=\"0\""));
        assertEquals("", MmsPduUtil.cleanDisplayText("fit=meet"));
        assertEquals("", MmsPduUtil.cleanDisplayText("dur=5000ms"));
        assertEquals("", MmsPduUtil.cleanDisplayText("begin=0s"));
        assertEquals("", MmsPduUtil.cleanDisplayText("end=5s"));
        assertEquals("", MmsPduUtil.cleanDisplayText("src=image0001.jpg"));
        assertEquals("", MmsPduUtil.cleanDisplayText("img src"));
        assertEquals("", MmsPduUtil.cleanDisplayText("img src=image000000.jpg"));
        assertEquals("", MmsPduUtil.cleanDisplayText("region ID"));
        assertEquals("", MmsPduUtil.cleanDisplayText("region-id"));
        assertEquals("", MmsPduUtil.cleanDisplayText("region_id"));
        assertEquals("", MmsPduUtil.cleanDisplayText("region Image"));
        assertEquals("", MmsPduUtil.cleanDisplayText("regionid=Text"));
        assertEquals("", MmsPduUtil.cleanDisplayText("text region"));
        assertEquals("", MmsPduUtil.cleanDisplayText("text src"));
        assertEquals("", MmsPduUtil.cleanDisplayText("Image\" width"));
        assertEquals("", MmsPduUtil.cleanDisplayText("image height"));
        assertEquals("", MmsPduUtil.cleanDisplayText("text\" width"));
        assertEquals("", MmsPduUtil.cleanDisplayText("text height"));
        assertEquals("", MmsPduUtil.cleanDisplayText("text\" width=\"100%\""));
        assertEquals("", MmsPduUtil.cleanDisplayText("image width=640"));
        assertEquals("", MmsPduUtil.cleanDisplayText("root-layout height=\"100%\""));
        assertEquals("", MmsPduUtil.cleanDisplayText("width=100%"));
        assertEquals("", MmsPduUtil.cleanDisplayText("width"));
        assertEquals("", MmsPduUtil.cleanDisplayText("height"));
        assertEquals("", MmsPduUtil.cleanDisplayText("height=\"240\""));
        assertEquals("", MmsPduUtil.cleanDisplayText("240\" height"));
        assertEquals("", MmsPduUtil.cleanDisplayText("640 width"));
        assertEquals("", MmsPduUtil.cleanDisplayText("320px height"));
        assertEquals("", MmsPduUtil.cleanDisplayText("100% width"));
        assertEquals("", MmsPduUtil.cleanDisplayText("video width"));
        assertEquals("", MmsPduUtil.cleanDisplayText("audio height"));
    }

    @Test
    public void extractFirstImage_findsJpegPayload() {
        byte[] pdu = new byte[] { 1, 2, 3, (byte) 0xFF, (byte) 0xD8, 10, 11, (byte) 0xFF, (byte) 0xD9, 4 };
        byte[] image = new byte[] { (byte) 0xFF, (byte) 0xD8, 10, 11, (byte) 0xFF, (byte) 0xD9 };

        assertArrayEquals(image, MmsPduUtil.extractFirstImage(pdu));
    }

    @Test
    public void extractFirstImage_usesMultipartImagePartInsteadOfEmbeddedThumbnail() {
        byte[] image = new byte[] {
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xE0, 0x00, 0x08, 'J', 'F', 'X', 'X', 0x00, 0x10,
                (byte) 0xFF, (byte) 0xD8, 1, (byte) 0xFF, (byte) 0xD9,
                2, 3, 4, (byte) 0xFF, (byte) 0xD9
        };
        byte[] pdu = multipartPdu(
                part(smilHeaderWithTextPlainDecoy(), bytes("text region")),
                part(asciiHeader("image/jpeg"), image)
        );

        assertArrayEquals(image, MmsPduUtil.extractFirstImage(pdu));
    }

    @Test
    public void extractFirstImage_prefersLargestMultipartImage() {
        byte[] thumbnail = new byte[] { (byte) 0xFF, (byte) 0xD8, 1, (byte) 0xFF, (byte) 0xD9 };
        byte[] fullImage = new byte[] {
                (byte) 0xFF, (byte) 0xD8, 10, 11, 12, 13, 14, 15, (byte) 0xFF, (byte) 0xD9
        };
        byte[] pdu = multipartPdu(
                part(smilHeaderWithTextPlainDecoy(), bytes("text region")),
                part(asciiHeader("image/jpeg"), thumbnail),
                part(asciiHeader("image/jpeg"), fullImage)
        );

        assertArrayEquals(fullImage, MmsPduUtil.extractFirstImage(pdu));
    }

    @Test
    public void extractText_ignoresSmilLayoutPartWhenMultipartHasNoCaption() {
        byte[] pdu = multipartPdu(
                part(smilHeaderWithTextPlainDecoy(), bytes("region ID")),
                part(asciiHeader("image/jpeg"), new byte[] { (byte) 0xFF, (byte) 0xD8, 1, (byte) 0xFF, (byte) 0xD9 })
        );

        assertEquals("", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void extractText_prefersTextPlainCaptionOverSmilLayoutPart() {
        byte[] pdu = multipartPdu(
                part(smilHeaderWithTextPlainDecoy(), bytes("region ID")),
                part(new byte[] { (byte) 0x83 }, bytes("zzz")),
                part(asciiHeader("image/jpeg"), new byte[] { (byte) 0xFF, (byte) 0xD8, 1, (byte) 0xFF, (byte) 0xD9 })
        );

        assertEquals("zzz", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void extractText_usesFallbackWhenMultipartGuessHasNoTextPart() {
        byte[] pdu = rawMultipartGuessWithTrailingCaption(
                part(asciiHeader("image/jpeg"), new byte[] { (byte) 0xFF, (byte) 0xD8, 1, (byte) 0xFF, (byte) 0xD9 }),
                "text/plain\0zzz"
        );

        assertEquals("zzz", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void extractText_checksAfterImageWhenLayoutAppearsBeforeImage() {
        byte[] pdu = join(
                bytes("<par dur=\"8000ms\"><text src=\"text000002.txt\" region=\"Text\"/>"),
                new byte[] { (byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9 },
                bytes("text000002.txt\0Test")
        );

        assertEquals("Test", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void extractText_trustsBestEffortSmilAndImageMultipartAsNoCaption() {
        byte[] pdu = rawMultipartGuess(
                part(smilHeaderWithTextPlainDecoy(), bytes("region ID")),
                part(asciiHeader("image/jpeg"), new byte[] { (byte) 0xFF, (byte) 0xD8, 1, (byte) 0xFF, (byte) 0xD9 })
        );

        assertEquals("", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void extractText_doesNotTreatRandomContentTypeByteAsConfirmedMultipart() {
        byte[] pdu = rawAfterUnrelatedContentTypeByte(
                'x',
                part(asciiHeader("image/jpeg"), new byte[] { (byte) 0xFF, (byte) 0xD8, 1, (byte) 0xFF, (byte) 0xD9 }),
                "text/plain\0zzz"
        );

        assertEquals("zzz", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void extractText_doesNotTreatHeaderCodeAsMultipartContentType() {
        byte[] pdu = rawAfterUnrelatedContentTypeByte(
                0x84,
                part(asciiHeader("image/jpeg"), new byte[] { (byte) 0xFF, (byte) 0xD8, 1, (byte) 0xFF, (byte) 0xD9 }),
                "text/plain\0zzz"
        );

        assertEquals("zzz", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void extractText_usesBetterMultipartParseWhenConfirmedParseOnlyFindsSmil() {
        byte[] pdu = confirmedSmilOnlyParseBeforeRawMultipart(
                part(smilHeaderWithTextPlainDecoy(), bytes("<text src=\"text000002.txt\" region=\"Text\"/>")),
                part(asciiHeader("image/jpeg"), new byte[] { (byte) 0xFF, (byte) 0xD8, 1, (byte) 0xFF, (byte) 0xD9 }),
                part(new byte[] { 0x13, (byte) 0x83, (byte) 0x85, 't', 'e', 'x', 't', '0', '0', '0', '0', '0', '2',
                        '.', 't', 'x', 't', 0, (byte) 0x81, (byte) 0xEA, (byte) 0x8E, 't', 'e', 'x', 't', '0',
                        '0', '0', '0', '0', '2', '.', 't', 'x', 't', 0, (byte) 0xC0, 0x22, '<', 't', 'e',
                        'x', 't', '0', '0', '0', '0', '0', '2', '>', 0 }, bytes("Test"))
        );

        assertEquals("Test", MmsPduUtil.extractText(pdu));
    }

    @Test
    public void extractText_readsUtf8CarrierTextPartWithNameAndCharsetHeaders() {
        byte[] pdu = java.util.Base64.getDecoder().decode(
                "jISYMDE3Q0VGN0Y5QzhBMDAwMEI3RDAwMTAyMDEAjZCLMDE3Q0VGN0Y5QzhBMDAwMEI3RDAwMTAy"
                        + "AIkWgDU1NTAxMDEwMDAvVFlQRT1QTE1OAIUEalj3yIaBj4GXNTU1MDEwMjAwMC9UWVBFPVBMTU4A"
                        + "lzU1NTAxMDMwMDAvVFlQRT1QTE1OAIqAhBuziWFwcGxpY2F0aW9uL3NtaWwAijxzbWlsPgACL4MR"
                        + "G2FwcGxpY2F0aW9uL3NtaWwAhXNtaWwueG1sAI5zbWlsLnhtbADAIjxzbWlsPgA8c21pbD4NCiAg"
                        + "ICA8aGVhZD4NCiAgICAgICAgPGxheW91dD4NCiAgICAgICAgICAgIDxyb290LWxheW91dCB3aWR0"
                        + "aD0iMjQwIiBoZWlnaHQ9IjE2MCIvPg0KICAgICAgICAgICAgPHJlZ2lvbiBpZD0iSW1hZ2UiIHdp"
                        + "ZHRoPSIxMDAlIiBoZWlnaHQ9IjY3JSIgbGVmdD0iMCUiIHRvcD0iMCUiIGZpdD0ibWVldCIvPg0K"
                        + "ICAgICAgICAgICAgPHJlZ2lvbiBpZD0iVGV4dCIgd2lkdGg9IjEwMCUiIGhlaWdodD0iMzMlIiBs"
                        + "ZWZ0PSIwJSIgdG9wPSI2NyUiIGZpdD0ibWVldCIvPg0KICAgICAgICA8L2xheW91dD4NCiAgICA8"
                        + "L2hlYWQ+DQogICAgPGJvZHk+DQogICAgPHBhciBkdXI9IjgwMDBtcyI+PHRleHQgc3JjPSJ0ZXh0"
                        + "MDAwMDAxLnR4dCIgcmVnaW9uPSJUZXh0Ii8+PC9wYXI+PC9ib2R5Pg0KPC9zbWlsPjMYE4OFdGV4"
                        + "dDAwMDAwMS50eHQAgeqOdGV4dDAwMDAwMS50eHQAwCI8dGV4dDAwMDAwMT4A5L2g5Zyo6Lef5oiR"
                        + "6K+05LuA5LmI77yf"
        );

        assertEquals("\u4f60\u5728\u8ddf\u6211\u8bf4\u4ec0\u4e48\uff1f", MmsPduUtil.extractText(pdu));
        assertEquals(0, MmsPduUtil.extractFirstImage(pdu).length);
    }

    private static Part part(byte[] header, byte[] data) {
        return new Part(header, data);
    }

    private static byte[] multipartPdu(Part... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x84);
        output.write(0x01);
        output.write(CONTENT_TYPE_MULTIPART_MIXED);
        output.write(parts.length);
        for (Part part : parts) {
            output.write(part.header.length);
            output.write(part.data.length);
            output.write(part.header, 0, part.header.length);
            output.write(part.data, 0, part.data.length);
        }
        return output.toByteArray();
    }

    private static byte[] rawAfterUnrelatedContentTypeByte(int contentTypeValue, Part part, String trailingText) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x84);
        output.write(0x01);
        output.write(contentTypeValue);
        output.write(1);
        output.write(part.header.length);
        output.write(part.data.length);
        output.write(part.header, 0, part.header.length);
        output.write(part.data, 0, part.data.length);
        byte[] text = bytes(trailingText);
        output.write(text, 0, text.length);
        return output.toByteArray();
    }

    private static byte[] confirmedSmilOnlyParseBeforeRawMultipart(Part... rawParts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] badHeader = asciiHeader("application/smil");
        byte[] badData = bytes("<text src=\"text000002.txt\" region=\"Text\"/>");
        output.write(0x84);
        output.write(0x01);
        output.write(CONTENT_TYPE_MULTIPART_MIXED);
        output.write(1);
        output.write(badHeader.length);
        output.write(badData.length);
        output.write(badHeader, 0, badHeader.length);
        output.write(badData, 0, badData.length);
        ByteArrayOutputStream raw = rawMultipartGuessBuilder(rawParts);
        byte[] rawBytes = raw.toByteArray();
        output.write(rawBytes, 0, rawBytes.length);
        return output.toByteArray();
    }

    private static byte[] rawMultipartGuessWithTrailingCaption(Part part, String trailingText) {
        ByteArrayOutputStream output = rawMultipartGuessBuilder(part);
        byte[] text = bytes(trailingText);
        output.write(text, 0, text.length);
        return output.toByteArray();
    }

    private static byte[] rawMultipartGuess(Part... parts) {
        return rawMultipartGuessBuilder(parts).toByteArray();
    }

    private static ByteArrayOutputStream rawMultipartGuessBuilder(Part... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(parts.length);
        for (Part part : parts) {
            output.write(part.header.length);
            output.write(part.data.length);
            output.write(part.header, 0, part.header.length);
            output.write(part.data, 0, part.data.length);
        }
        return output;
    }

    private static byte[] smilHeaderWithTextPlainDecoy() {
        byte[] smil = asciiHeader("application/smil");
        byte[] header = new byte[smil.length + 1];
        System.arraycopy(smil, 0, header, 0, smil.length);
        header[header.length - 1] = (byte) 0x83;
        return header;
    }

    private static byte[] asciiHeader(String value) {
        byte[] raw = bytes(value);
        byte[] header = new byte[raw.length + 1];
        System.arraycopy(raw, 0, header, 0, raw.length);
        return header;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] join(byte[]... chunks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            output.write(chunk, 0, chunk.length);
        }
        return output.toByteArray();
    }

    private static final class Part {
        final byte[] header;
        final byte[] data;

        Part(byte[] header, byte[] data) {
            this.header = header;
            this.data = data;
        }
    }
}
