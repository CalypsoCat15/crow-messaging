package com.crowmessenger.messages;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class GifFrameReducer {
    private static final int GRAPHICS_CONTROL_EXTENSION = 0xF9;

    private GifFrameReducer() {
    }

    static byte[] reduce(byte[] source, int maxBytes) {
        ParsedGif parsed = parse(source);
        if (parsed == null || parsed.frames.size() < 3 || !parsed.canDropFrames()) {
            return null;
        }
        int[] selected = null;
        for (int keep = parsed.frames.size() - 1; keep >= 2; keep--) {
            int[] candidate = evenlySpacedFrameIndexes(parsed.frames.size(), keep);
            if (parsed.estimatedSize(candidate) <= maxBytes) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) {
            return null;
        }
        return parsed.write(selected);
    }

    private static ParsedGif parse(byte[] source) {
        if (source == null || source.length < 14 || !hasGifSignature(source)) {
            return null;
        }
        int width = littleEndianShort(source, 6);
        int height = littleEndianShort(source, 8);
        int packed = source[10] & 0xFF;
        int cursor = 13;
        if ((packed & 0x80) != 0) {
            cursor += 3 * (1 << ((packed & 0x07) + 1));
        }
        if (cursor > source.length) {
            return null;
        }

        byte[] header = Arrays.copyOfRange(source, 0, cursor);
        List<byte[]> extensions = new ArrayList<>();
        List<Frame> frames = new ArrayList<>();
        byte[] pendingControl = null;
        while (cursor < source.length) {
            int marker = source[cursor] & 0xFF;
            if (marker == 0x3B) {
                return new ParsedGif(header, extensions, frames, width, height);
            }
            if (marker == 0x21) {
                if (cursor + 2 >= source.length) {
                    return null;
                }
                int label = source[cursor + 1] & 0xFF;
                if (label == GRAPHICS_CONTROL_EXTENSION) {
                    if (cursor + 8 > source.length
                            || (source[cursor + 2] & 0xFF) != 4
                            || source[cursor + 7] != 0) {
                        return null;
                    }
                    pendingControl = Arrays.copyOfRange(source, cursor, cursor + 8);
                    cursor += 8;
                } else {
                    if (label != 0xFF && label != 0xFE) {
                        return null;
                    }
                    int end = subBlocksEnd(source, cursor + 2);
                    if (end < 0) {
                        return null;
                    }
                    extensions.add(Arrays.copyOfRange(source, cursor, end));
                    cursor = end;
                }
                continue;
            }
            if (marker != 0x2C || cursor + 10 > source.length) {
                return null;
            }
            int imageStart = cursor;
            int left = littleEndianShort(source, cursor + 1);
            int top = littleEndianShort(source, cursor + 3);
            int frameWidth = littleEndianShort(source, cursor + 5);
            int frameHeight = littleEndianShort(source, cursor + 7);
            int imagePacked = source[cursor + 9] & 0xFF;
            cursor += 10;
            if ((imagePacked & 0x80) != 0) {
                cursor += 3 * (1 << ((imagePacked & 0x07) + 1));
            }
            if (cursor >= source.length) {
                return null;
            }
            cursor++;
            int imageEnd = subBlocksEnd(source, cursor);
            if (imageEnd < 0) {
                return null;
            }
            frames.add(new Frame(
                    pendingControl,
                    Arrays.copyOfRange(source, imageStart, imageEnd),
                    left == 0 && top == 0 && frameWidth == width && frameHeight == height,
                    (long) frameWidth * frameHeight <= 4
            ));
            pendingControl = null;
            cursor = imageEnd;
        }
        return null;
    }

    private static int subBlocksEnd(byte[] source, int cursor) {
        while (cursor < source.length) {
            int length = source[cursor] & 0xFF;
            cursor++;
            if (length == 0) {
                return cursor;
            }
            cursor += length;
            if (cursor > source.length) {
                return -1;
            }
        }
        return -1;
    }

    private static int[] evenlySpacedFrameIndexes(int frameCount, int keep) {
        int[] indexes = new int[keep];
        for (int index = 0; index < keep; index++) {
            indexes[index] = Math.round(index * (frameCount - 1f) / (keep - 1f));
        }
        return indexes;
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static boolean hasGifSignature(byte[] bytes) {
        return bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9')
                && bytes[5] == 'a';
    }

    private static final class ParsedGif {
        final byte[] header;
        final List<byte[]> extensions;
        final List<Frame> frames;
        final int width;
        final int height;

        ParsedGif(byte[] header, List<byte[]> extensions, List<Frame> frames, int width, int height) {
            this.header = header;
            this.extensions = extensions;
            this.frames = frames;
            this.width = width;
            this.height = height;
        }

        boolean canDropFrames() {
            if (width <= 0 || height <= 0) {
                return false;
            }
            for (Frame frame : frames) {
                if (!frame.fullCanvas && !frame.tinyPlaceholder) {
                    return false;
                }
            }
            return true;
        }

        int estimatedSize(int[] selected) {
            int size = header.length + 1;
            for (byte[] extension : extensions) {
                size += extension.length;
            }
            for (int index : selected) {
                size += frames.get(index).byteCount();
            }
            return size;
        }

        byte[] write(int[] selected) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(estimatedSize(selected));
            output.write(header, 0, header.length);
            for (byte[] extension : extensions) {
                output.write(extension, 0, extension.length);
            }
            for (int selectedIndex = 0; selectedIndex < selected.length; selectedIndex++) {
                int frameIndex = selected[selectedIndex];
                int nextFrameIndex = selectedIndex + 1 < selected.length
                        ? selected[selectedIndex + 1]
                        : frames.size();
                int delay = 0;
                for (int index = frameIndex; index < nextFrameIndex; index++) {
                    delay = Math.min(0xFFFF, delay + frames.get(index).delayCentiseconds());
                }
                frames.get(frameIndex).write(output, delay);
            }
            output.write(0x3B);
            return output.toByteArray();
        }
    }

    private static final class Frame {
        final byte[] control;
        final byte[] image;
        final boolean fullCanvas;
        final boolean tinyPlaceholder;

        Frame(byte[] control, byte[] image, boolean fullCanvas, boolean tinyPlaceholder) {
            this.control = control;
            this.image = image;
            this.fullCanvas = fullCanvas;
            this.tinyPlaceholder = tinyPlaceholder;
        }

        int byteCount() {
            return image.length + (control == null ? 0 : control.length);
        }

        int delayCentiseconds() {
            return control == null ? 0 : littleEndianShort(control, 4);
        }

        void write(ByteArrayOutputStream output, int delayCentiseconds) {
            if (control != null) {
                byte[] adjustedControl = control.clone();
                adjustedControl[4] = (byte) (delayCentiseconds & 0xFF);
                adjustedControl[5] = (byte) ((delayCentiseconds >>> 8) & 0xFF);
                output.write(adjustedControl, 0, adjustedControl.length);
            }
            output.write(image, 0, image.length);
        }
    }
}
