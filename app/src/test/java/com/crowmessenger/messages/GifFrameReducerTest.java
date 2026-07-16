package com.crowmessenger.messages;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Base64;

import org.junit.Test;

public class GifFrameReducerTest {
    private static final byte[] SIX_FRAME_GIF = Base64.getDecoder().decode(
            "R0lGODlhCAAGAIEAAAD/KAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQECAAAACwAAAAACAAGAAAIDgABCBxIsKDBgwgTDgwIACH5BAUIAAEALAAAAAAIAAYAgSPhPAAAAAAAAAAAAAgOAAEIHEiwoMGDCBMODAgAIfkEBQgAAQAsAAAAAAgABgCBRsNQAAAAAAAAAAAACA4AAQgcSLCgwYMIEw4MCAAh+QQFCAABACwAAAAACAAGAIFppWQAAAAAAAAAAAAIDgABCBxIsKDBgwgTDgwIACH5BAUIAAEALAAAAAAIAAYAgYyHeAAAAAAAAAAAAAgOAAEIHEiwoMGDCBMODAgAIfkEBQgAAQAsAAAAAAgABgCBr2mMAAAAAAAAAAAACA4AAQgcSLCgwYMIEw4MCAA7"
    );

    @Test
    public void reduce_keepsGifAnimatedAndWithinLimit() throws Exception {
        byte[] reduced = GifFrameReducer.reduce(SIX_FRAME_GIF, 220);

        assertNotNull(reduced);
        assertTrue(reduced.length <= 220);
        assertArrayEquals(new byte[] { 'G', 'I', 'F', '8', '9', 'a' }, java.util.Arrays.copyOf(reduced, 6));
        assertTrue(frameCount(reduced) >= 2);
        assertTrue(frameCount(reduced) < frameCount(SIX_FRAME_GIF));
    }

    @Test
    public void reduce_returnsNullWhenLimitCannotKeepAnimation() {
        assertNull(GifFrameReducer.reduce(SIX_FRAME_GIF, 80));
    }

    @Test
    public void compress_leavesCarrierSafeGifUntouched() throws Exception {
        assertArrayEquals(SIX_FRAME_GIF, GifMmsCompressor.compress(SIX_FRAME_GIF, SIX_FRAME_GIF.length));
    }

    private static int frameCount(byte[] gif) {
        int packed = gif[10] & 0xFF;
        int cursor = 13 + (((packed & 0x80) == 0) ? 0 : 3 * (1 << ((packed & 7) + 1)));
        int frames = 0;
        while (cursor < gif.length) {
            int marker = gif[cursor] & 0xFF;
            if (marker == 0x3B) {
                return frames;
            }
            if (marker == 0x21) {
                cursor += 2;
                do {
                    int blockSize = gif[cursor] & 0xFF;
                    cursor += blockSize + 1;
                    if (blockSize == 0) {
                        break;
                    }
                } while (cursor < gif.length);
                continue;
            }
            assertTrue(marker == 0x2C);
            frames++;
            int imagePacked = gif[cursor + 9] & 0xFF;
            cursor += 10 + (((imagePacked & 0x80) == 0) ? 0 : 3 * (1 << ((imagePacked & 7) + 1)));
            cursor++;
            do {
                int blockSize = gif[cursor] & 0xFF;
                cursor += blockSize + 1;
                if (blockSize == 0) {
                    break;
                }
            } while (cursor < gif.length);
        }
        throw new AssertionError("GIF trailer was missing");
    }
}
