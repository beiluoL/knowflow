package com.knowflow.interview.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 服务端与生成的前端类型共用voice-v1.json，拒绝未知事件和越界帧。 */
@Component
public class VoiceProtocol {

  private final JsonNode contract;

  public VoiceProtocol(ObjectMapper json) throws java.io.IOException {
    try (
      var in = new ClassPathResource("interview/voice-v1.json").getInputStream()
    ) {
      contract = json.readTree(in);
    }
  }

  public record Event(
    String eventId,
    long sessionId,
    String turnId,
    String requestId,
    long sequence,
    long timestamp,
    String type,
    JsonNode payload
  ) {}

  public Event parse(JsonNode root, long session) {
    if (
      root == null ||
      !root.isObject() ||
      !root.path("protocolVersion").isIntegralNumber() ||
      !root.path("protocolVersion").canConvertToInt() ||
      root.path("protocolVersion").asInt(-1) != 1 ||
      !root.path("sessionId").isIntegralNumber() ||
      !root.path("sessionId").canConvertToLong() ||
      root.path("sessionId").asLong() != session ||
      !root.path("sequence").isIntegralNumber() ||
      !root.path("sequence").canConvertToLong() ||
      root.path("sequence").asLong() < 0 ||
      root.path("sequence").asLong() > Integer.MAX_VALUE ||
      !root.path("timestamp").isIntegralNumber() ||
      !root.path("timestamp").canConvertToLong() ||
      root.path("timestamp").asLong() < System.currentTimeMillis() - 300000 ||
        root.path("timestamp").asLong() > System.currentTimeMillis() + 300000
    ) throw new IllegalArgumentException("INVALID_ENVELOPE");
    String event = root.path("eventId").asText(),
      request = root.path("requestId").asText(),
      type = root.path("type").asText(),
      turn = root.path("turnId").asText();
    if (
      !root.path("eventId").isTextual() ||
      !root.path("requestId").isTextual() ||
      !root.path("turnId").isTextual() ||
      !event.matches("[A-Za-z0-9_-]{8,80}") ||
      !request.matches("[A-Za-z0-9_-]{8,80}") ||
      turn.length() > 160 ||
      !allowed("clientEvents", type) ||
      !root.path("payload").isObject()
    ) throw new IllegalArgumentException("INVALID_EVENT");
    return new Event(
      event,
      session,
      turn,
      request,
      root.path("sequence").asLong(),
      root.path("timestamp").asLong(),
      type,
      root.path("payload")
    );
  }

  public boolean allowed(String direction, String type) {
    for (JsonNode event : contract.path(direction))
      if (event.asText().equals(type)) return true;
    return false;
  }

  public record Frame(int streamId, long sequence, byte[] pcm) {}

  public Frame frame(byte[] bytes) {
    if (
      bytes.length != 652 && bytes.length != 1292
    ) throw new IllegalArgumentException("AUDIO_SIZE");
    var buffer = ByteBuffer.wrap(bytes);
    if (
      buffer.getInt() != contract.path("inputMagic").asInt()
    ) throw new IllegalArgumentException("AUDIO_VERSION");
    int stream = buffer.getInt();
    long sequence = Integer.toUnsignedLong(buffer.getInt());
    byte[] pcm = new byte[buffer.remaining()];
    buffer.get(pcm);
    return new Frame(stream, sequence, pcm);
  }

  public byte[] output(int streamId, long sequence, byte[] pcm) {
    if (
      sequence < 0 || sequence > 0xffffffffL || pcm.length % 2 != 0
    ) throw new IllegalArgumentException("AUDIO_SEQUENCE");
    return ByteBuffer.allocate(12 + pcm.length)
      .putInt(contract.path("outputMagic").asInt())
      .putInt(streamId)
      .putInt((int) sequence)
      .put(pcm)
      .array();
  }
}
