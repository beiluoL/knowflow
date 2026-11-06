package com.knowflow.voice.audio;

/** 16kHz 单声道 PCM16 WAV 的最小解析/封装；只接受一种真实格式，拒绝猜测。 */
public final class PcmWav {

    public static final int SAMPLE_RATE = 16000;
    private static final int HEADER_BYTES = 44;

    private PcmWav() {
    }

    public record Audio(byte[] pcm, int sampleRate, int durationMs) { }

    public static Audio parse(byte[] wav, int maxSeconds) {
        if (wav == null || wav.length <= HEADER_BYTES) throw new IllegalArgumentException("音频数据为空");
        if (!ascii(wav, 0, "RIFF") || !ascii(wav, 8, "WAVE")) {
            throw new IllegalArgumentException("仅支持 16kHz 单声道 PCM16 WAV");
        }
        int encoding = -1;
        int byteRate = -1;
        int blockAlign = -1;
        int channels = -1;
        int rate = -1;
        int bits = -1;
        int dataOffset = -1;
        int dataBytes = -1;
        // 真实 WAV 常带 LIST/fact 等附加块，按 RIFF 结构遍历而不是假设 data 固定在 36 字节。
        int offset = 12;
        while (offset + 8 <= wav.length) {
            String id = new String(wav, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = readInt(wav, offset + 4);
            int body = offset + 8;
            if (size < 0 || body + (long) size > wav.length) {
                throw new IllegalArgumentException("音频数据不完整");
            }
            if ("fmt ".equals(id) && size >= 16) {
                encoding = readShort(wav, body);
                byteRate = readInt(wav, body + 8);
                blockAlign = readShort(wav, body + 12);
                channels = readShort(wav, body + 2);
                rate = readInt(wav, body + 4);
                bits = readShort(wav, body + 14);
            } else if ("data".equals(id)) {
                dataOffset = body;
                dataBytes = size;
                break;
            }
            offset = body + size + (size % 2);
        }
        if (encoding != 1 || channels != 1 || rate != SAMPLE_RATE || bits != 16
                || byteRate != SAMPLE_RATE * 2 || blockAlign != 2 || dataOffset < 0) {
            throw new IllegalArgumentException("仅支持 16kHz 单声道 PCM16 WAV");
        }
        if (dataBytes <= 0 || dataBytes % 2 != 0) throw new IllegalArgumentException("音频数据不完整");
        long maxBytes = Math.max(1, maxSeconds) * (long) SAMPLE_RATE * 2;
        if (dataBytes > maxBytes) throw new IllegalArgumentException("音频超过时长限制");
        byte[] pcm = new byte[dataBytes];
        System.arraycopy(wav, dataOffset, pcm, 0, dataBytes);
        return new Audio(pcm, rate, dataBytes / 32);
    }

    public static byte[] wrap(byte[] pcm) {
        byte[] wav = new byte[HEADER_BYTES + pcm.length];
        writeAscii(wav, 0, "RIFF");
        writeInt(wav, 4, 36 + pcm.length);
        writeAscii(wav, 8, "WAVE");
        writeAscii(wav, 12, "fmt ");
        writeInt(wav, 16, 16);
        writeShort(wav, 20, 1);
        writeShort(wav, 22, 1);
        writeInt(wav, 24, SAMPLE_RATE);
        writeInt(wav, 28, SAMPLE_RATE * 2);
        writeShort(wav, 32, 2);
        writeShort(wav, 34, 16);
        writeAscii(wav, 36, "data");
        writeInt(wav, 40, pcm.length);
        System.arraycopy(pcm, 0, wav, HEADER_BYTES, pcm.length);
        return wav;
    }

    private static boolean ascii(byte[] source, int offset, String expected) {
        if (source.length < offset + expected.length()) return false;
        for (int index = 0; index < expected.length(); index++) {
            if (source[offset + index] != (byte) expected.charAt(index)) return false;
        }
        return true;
    }

    private static int readInt(byte[] source, int offset) {
        return (source[offset] & 0xff) | ((source[offset + 1] & 0xff) << 8)
                | ((source[offset + 2] & 0xff) << 16) | ((source[offset + 3] & 0xff) << 24);
    }

    private static int readShort(byte[] source, int offset) {
        return (source[offset] & 0xff) | ((source[offset + 1] & 0xff) << 8);
    }

    private static void writeAscii(byte[] target, int offset, String value) {
        for (int index = 0; index < value.length(); index++) target[offset + index] = (byte) value.charAt(index);
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

    private static void writeShort(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
    }
}
