package com.knowflow.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 只接受一种真实格式：16kHz 单声道 PCM16；其余一律拒绝，不做格式猜测。 */
class PcmWavTest {

    private static byte[] pcm(int samples) {
        byte[] data = new byte[samples * 2];
        for (int index = 0; index < samples; index++) {
            data[index * 2] = (byte) (index % 251);
            data[index * 2 + 1] = (byte) ((index / 251) & 0x7f);
        }
        return data;
    }

    @Test
    void wrapThenParseRoundTrips() {
        byte[] source = pcm(1600); // 100ms
        var parsed = PcmWav.parse(PcmWav.wrap(source), 60);
        assertEquals(16000, parsed.sampleRate());
        assertEquals(100, parsed.durationMs());
        org.junit.jupiter.api.Assertions.assertArrayEquals(source, parsed.pcm());
    }

    @Test
    void rejectsMp3AndStereo() {
        assertThrows(IllegalArgumentException.class, () -> PcmWav.parse("ID3fake-audio".getBytes(StandardCharsets.US_ASCII), 60));
        byte[] stereo = PcmWav.wrap(pcm(100));
        stereo[22] = 2; // channels = 2
        assertThrows(IllegalArgumentException.class, () -> PcmWav.parse(stereo, 60));
    }

    @Test
    void rejectsOverlongAudio() {
        var wrapped = PcmWav.wrap(pcm(16000 * 3));
        assertThrows(IllegalArgumentException.class, () -> PcmWav.parse(wrapped, 2));
    }

    @Test
    void rejectsTruncatedData() {
        byte[] wrapped = PcmWav.wrap(pcm(800));
        byte[] truncated = new byte[wrapped.length - 100];
        System.arraycopy(wrapped, 0, truncated, 0, truncated.length);
        assertThrows(IllegalArgumentException.class, () -> PcmWav.parse(truncated, 60));
    }

    @Test
    void parsesWavWithExtraChunks() {
        byte[] base = PcmWav.wrap(pcm(320));
        // 在 fmt 与 data 之间插入一个 0 长度的 LIST 头（8 字节），模拟真实录音软件的附加块
        byte[] list = new byte[8];
        System.arraycopy("LIST".getBytes(StandardCharsets.US_ASCII), 0, list, 0, 4);
        byte[] merged = new byte[base.length + list.length];
        System.arraycopy(base, 0, merged, 0, 36);
        System.arraycopy(list, 0, merged, 36, list.length);
        System.arraycopy(base, 36, merged, 36 + list.length, base.length - 36);
        var parsed = PcmWav.parse(merged, 60);
        assertEquals(20, parsed.durationMs());
    }
}
