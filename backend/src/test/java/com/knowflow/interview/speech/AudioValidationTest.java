package com.knowflow.interview.speech;
import org.junit.jupiter.api.Test;
import java.nio.*;
import static org.junit.jupiter.api.Assertions.*;
class AudioValidationTest {
 byte[] wav(int samples){var b=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes()).putInt(b.capacity()-8).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(samples*2);return b.array();}
 @Test void derivesDurationFromPcmRatherThanClientClaim(){assertEquals(1000,AudioValidation.durationMs(wav(16000),"audio/wav",60));}
 @Test void rejectsActualOverLimit(){assertThrows(RuntimeException.class,()->AudioValidation.durationMs(wav(32000),"audio/wav",1));}
 @Test void rejectsSpoofedMimeAndTruncation(){assertThrows(RuntimeException.class,()->AudioValidation.durationMs(wav(16000),"audio/webm",60));assertThrows(RuntimeException.class,()->AudioValidation.durationMs(new byte[10],"audio/wav",60));}
}
