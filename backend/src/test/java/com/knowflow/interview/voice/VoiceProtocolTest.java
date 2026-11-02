package com.knowflow.interview.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoiceProtocolTest {
    private static final int INPUT_MAGIC = 1262900785;
    private static final int OUTPUT_MAGIC = 1262900273;
    private final ObjectMapper json = new ObjectMapper();
    private VoiceProtocol protocol;

    @BeforeEach
    void setup() throws Exception {
        protocol = new VoiceProtocol(json);
    }

    private ObjectNode event() {
        var node = json.createObjectNode();
        node.put("protocolVersion", 1);
        node.put("eventId", "event_001");
        node.put("requestId", "request_001");
        node.put("sessionId", 11L);
        node.put("turnId", "turn_001");
        node.put("sequence", 0);
        node.put("timestamp", System.currentTimeMillis());
        node.put("type", "audio.start");
        node.putObject("payload").put("streamId", 7);
        return node;
    }

    @Test
    void validEnvelopePreservesServerBoundSessionAndIdentifiers() {
        var result = protocol.parse(event(), 11L);
        assertEquals(11L, result.sessionId());
        assertEquals("event_001", result.eventId());
        assertEquals("request_001", result.requestId());
        assertEquals("turn_001", result.turnId());
        assertEquals("audio.start", result.type());
        assertEquals(7, result.payload().path("streamId").asInt());
        assertTrue(protocol.allowed("serverEvents", "asr.final"));
        assertFalse(protocol.allowed("clientEvents", "asr.final"));
    }

    @Test
    void rejectsCrossSessionMissingMetadataAndUnknownOrServerEvent() {
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event(), 12L));
        for (String field : new String[]{"protocolVersion", "eventId", "requestId", "sessionId",
                "turnId", "sequence", "timestamp", "type", "payload"}) {
            var node = event();
            node.remove(field);
            assertThrows(IllegalArgumentException.class, () -> protocol.parse(node, 11L), field);
        }
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("type", "unknown"), 11L));
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("type", "asr.final"), 11L));
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(json.createArrayNode(), 11L));
    }

    @Test
    void rejectsInvalidIdentifierLengthsTypesAndPayloadShape() {
        for (String field : new String[]{"eventId", "requestId"}) {
            for (String invalid : new String[]{"short", "x".repeat(81), "invalid/request"}) {
                assertThrows(IllegalArgumentException.class,
                        () -> protocol.parse(event().put(field, invalid), 11L));
            }
            assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put(field, 12345678), 11L));
        }
        assertThrows(IllegalArgumentException.class,
                () -> protocol.parse(event().put("turnId", "x".repeat(161)), 11L));
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("payload", "text"), 11L));
    }

    @Test
    void rejectsSequenceAndTimestampBounds() {
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("sequence", -1), 11L));
        assertThrows(IllegalArgumentException.class,
                () -> protocol.parse(event().put("sequence", (long) Integer.MAX_VALUE + 1L), 11L));
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("sequence", "0"), 11L));
        assertThrows(IllegalArgumentException.class,
                () -> protocol.parse(event().put("timestamp", System.currentTimeMillis() - 301000L), 11L));
        assertThrows(IllegalArgumentException.class,
                () -> protocol.parse(event().put("timestamp", System.currentTimeMillis() + 301000L), 11L));
    }

    @Test
    void rejectsCoercedVersionAndIntegerOverflowInsteadOfWrapping() {
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("protocolVersion", "1"), 11L));
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("protocolVersion", 1.5), 11L));
        BigInteger wrap = BigInteger.ONE.shiftLeft(64);
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("sequence", wrap), 11L));
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("timestamp",
                wrap.add(BigInteger.valueOf(System.currentTimeMillis()))), 11L));
        assertThrows(IllegalArgumentException.class, () -> protocol.parse(event().put("sessionId",
                wrap.add(BigInteger.valueOf(11L))), 11L));
    }

    @Test
    void parsesOnlySupportedPcmFrameSizesAndKeepsUnsignedSequence() {
        for (int length : new int[]{640, 1280}) {
            byte[] bytes = ByteBuffer.allocate(12 + length).putInt(INPUT_MAGIC).putInt(7)
                    .putInt(-1).array();
            bytes[bytes.length - 1] = 42;
            var frame = protocol.frame(bytes);
            assertEquals(7, frame.streamId());
            assertEquals(0xffffffffL, frame.sequence());
            assertEquals(length, frame.pcm().length);
            assertEquals(42, frame.pcm()[length - 1]);
        }
        for (int invalid : new int[]{0, 12, 651, 653, 1291, 1293}) {
            assertThrows(IllegalArgumentException.class, () -> protocol.frame(new byte[invalid]));
        }
        assertThrows(IllegalArgumentException.class, () -> protocol.frame(new byte[652]));
    }

    @Test
    void outputUsesDistinctMagicAndRejectsOutOfRangeSequenceOrOddPcm() {
        byte[] pcm = {1, 2, 3, 4};
        var bytes = protocol.output(8, 0xffffffffL, pcm);
        var buffer = ByteBuffer.wrap(bytes);
        assertEquals(OUTPUT_MAGIC, buffer.getInt());
        assertEquals(8, buffer.getInt());
        assertEquals(-1, buffer.getInt());
        byte[] restored = new byte[4];
        buffer.get(restored);
        assertArrayEquals(pcm, restored);
        assertThrows(IllegalArgumentException.class, () -> protocol.output(1, -1L, pcm));
        assertThrows(IllegalArgumentException.class, () -> protocol.output(1, 0x100000000L, pcm));
        assertThrows(IllegalArgumentException.class, () -> protocol.output(1, 0L, new byte[3]));
    }
}
