package com.knowflow.interview.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.speech.*;
import com.knowflow.interview.v2.InterviewV2Service;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** 有界串行会话队列；WebSocket回调只校验大小/入队，网络与JDBC全部在受控工作池。 */
@Component
public class InterviewVoiceSocket extends BinaryWebSocketHandler {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
    InterviewVoiceSocket.class
  );

  private final ObjectMapper json;
  private final VoiceProtocol protocol;
  private final VoiceAccess access;
  private final VoiceTicketStore tickets;
  private final InterviewV2Service interviews;
  private final InterviewStreamingAsrProvider asr;
  private final InterviewTtsProvider tts;
  private final InterviewStreamingTranscriptService transcripts;
  private final ConcurrentMap<String, Connection> connections =
    new ConcurrentHashMap<>();
  private final Semaphore slots = new Semaphore(64);
  private final ThreadPoolTaskExecutor workers;

  public InterviewVoiceSocket(
    ObjectMapper json,
    VoiceProtocol protocol,
    VoiceAccess access,
    VoiceTicketStore tickets,
    InterviewV2Service interviews,
    InterviewStreamingAsrProvider asr,
    InterviewTtsProvider tts,
    InterviewStreamingTranscriptService transcripts
  ) {
    this.json = json;
    this.protocol = protocol;
    this.access = access;
    this.tickets = tickets;
    this.interviews = interviews;
    this.asr = asr;
    this.tts = tts;
    this.transcripts = transcripts;
    workers = new ThreadPoolTaskExecutor();
    workers.setCorePoolSize(4);
    workers.setMaxPoolSize(4);
    workers.setQueueCapacity(128);
    workers.setThreadNamePrefix("interview-voice-");
    workers.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    workers.initialize();
  }

  private static final class Connection {

    final WebSocketSession socket;
    final VoiceTicketStore.Identity identity;
    final ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(64);
    final AtomicBoolean draining = new AtomicBoolean(),
      closed = new AtomicBoolean();
    final LinkedHashMap<String, VoiceProtocol.Event> seen =
      new LinkedHashMap<>();
    volatile long receivedAt = System.currentTimeMillis();
    long clientSequence = -1,
      serverSequence;
    int inputId, outputId;
    boolean voiceOutput;
    long rateWindow = System.currentTimeMillis();
    int textRate, audioRate;
    Map<String, Object> state;
    Capture capture;
    InterviewTtsProvider.Stream output;
    long outputSequence, providerSequence;
    long thinkingAt, outputStartedAt, duplicateAudioFrames;
    String lastQuestion = "";
    long lastVersion = -1;
    AtomicBoolean generationCancelled = new AtomicBoolean();

    Connection(WebSocketSession socket, VoiceTicketStore.Identity identity) {
      this.socket = new ConcurrentWebSocketSessionDecorator(
        socket,
        5000,
        65536
      );
      this.identity = identity;
    }
  }

  private static final class Capture {

    final String request, question, provider;
    final long version;
    final int id;
    final MessageDigest digest;
    final StringBuilder finals = new StringBuilder();
    InterviewStreamingAsrProvider.Stream stream;
    InterviewSpeechService.SpeechTranscript transcript;
    long sequence, bytes, firstAudioAt, firstPartialAt, stoppedAt;
    boolean finishing, allowBargeIn;

    Capture(
      String request,
      String question,
      String provider,
      long version,
      int id
    ) throws Exception {
      this.request = request;
      this.question = question;
      this.provider = provider;
      this.version = version;
      this.id = id;
      digest = MessageDigest.getInstance("SHA-256");
    }
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession socket)
    throws Exception {
    Object identity = socket.getAttributes().get("voiceIdentity");
    if (
      !(identity instanceof VoiceTicketStore.Identity owner) ||
      !slots.tryAcquire()
    ) {
      socket.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    socket.setTextMessageSizeLimit(32768);
    socket.setBinaryMessageSizeLimit(1292);
    var connection = new Connection(socket, owner);
    connections.put(socket.getId(), connection);
    log.info("VOICE_CONNECTED sessionId={}", owner.session());
    offer(connection, () -> {
      connection.state = access.state(owner.user(), owner.session());
      send(
        connection,
        "session.ready",
        "",
        Map.of(
          "state",
          connection.state,
          "capabilities",
          access.capability(owner.user(), owner.session()),
          "serverTime",
          System.currentTimeMillis(),
          "replayAvailable",
          false
        )
      );
      restore(connection);
    });
  }

  private void offer(Connection connection, Runnable action) {
    if (connection.closed.get()) return;
    if (!connection.queue.offer(action)) {
      disconnect(connection);
      return;
    }
    if (connection.draining.compareAndSet(false, true)) {
      try {
        workers.execute(() -> drain(connection));
      } catch (RuntimeException full) {
        connection.draining.set(false);
        disconnect(connection);
      }
    }
  }

  private void drain(Connection connection) {
    try {
      Runnable action;
      while (
        !connection.closed.get() && (action = connection.queue.poll()) != null
      ) {
        try {
          action.run();
        } catch (BusinessException failure) {
          error(
            connection,
            "BUSINESS_" + failure.getCode(),
            failure.getMessage()
          );
        } catch (Exception failure) {
          error(
            connection,
            "VOICE_PROTOCOL_ERROR",
            "语音操作未完成，请改用文字或重新连接"
          );
        }
      }
    } finally {
      connection.draining.set(false);
      if (!connection.closed.get() && !connection.queue.isEmpty()) offer(
        connection,
        () -> {}
      );
    }
  }

  @Override
  protected void handleTextMessage(
    WebSocketSession socket,
    TextMessage message
  ) {
    Connection connection = connections.get(socket.getId());
    if (connection == null) return;
    if (message.getPayload().length() > 12000) {
      disconnect(connection);
      return;
    }
    String text = message.getPayload();
    offer(connection, () -> text(connection, text));
  }

  @Override
  protected void handleBinaryMessage(
    WebSocketSession socket,
    BinaryMessage message
  ) {
    Connection connection = connections.get(socket.getId());
    if (connection == null) return;
    if (message.getPayloadLength() > 1292) {
      disconnect(connection);
      return;
    }
    var buffer = message.getPayload().asReadOnlyBuffer();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    offer(connection, () -> audio(connection, bytes));
  }

  private void rate(Connection connection, boolean audio) {
    long now = System.currentTimeMillis();
    if (now - connection.rateWindow >= 1000) {
      connection.rateWindow = now;
      connection.textRate = 0;
      connection.audioRate = 0;
    }
    if (
      audio ? ++connection.audioRate > 75 : ++connection.textRate > 20
    ) throw new IllegalArgumentException("VOICE_RATE_LIMIT");
    connection.receivedAt = now;
  }

  private void text(Connection connection, String text) {
    try {
      rate(connection, false);
      VoiceProtocol.Event event = protocol.parse(
        json.readTree(text),
        connection.identity.session()
      );
      VoiceProtocol.Event previous = connection.seen.get(event.eventId());
      if (previous != null) {
        if (
          !previous.type().equals(event.type()) ||
          !previous.requestId().equals(event.requestId()) ||
          !previous.turnId().equals(event.turnId()) ||
          !previous.payload().equals(event.payload())
        ) throw new IllegalArgumentException("EVENT_REPLAY_CONFLICT");
        send(
          connection,
          "control.ack",
          event.requestId(),
          Map.of("duplicate", true, "eventId", event.eventId())
        );
        return;
      }
      if (
        event.sequence() != connection.clientSequence + 1
      ) throw new IllegalArgumentException("EVENT_SEQUENCE");
      connection.clientSequence = event.sequence();
      connection.seen.put(event.eventId(), event);
      if (connection.seen.size() > 256) connection.seen.remove(
        connection.seen.keySet().iterator().next()
      );
      switch (event.type()) {
        case "session.start" -> {
          boolean voiceOutput = event
            .payload()
            .path("voiceOutput")
            .asBoolean(false);
          if (voiceOutput && !connection.voiceOutput) connection.lastQuestion =
            "";
          if (!voiceOutput) cancelOutput(connection);
          connection.voiceOutput = voiceOutput;
          publishState(connection, true);
        }
        case "heartbeat" -> {
          if (!tickets.renew(connection.identity)) {
            disconnect(connection);
            return;
          }
          send(
            connection,
            "heartbeat.ack",
            event.requestId(),
            Map.of("serverTime", System.currentTimeMillis())
          );
          publishState(connection, false);
        }
        case "client.ack" -> {
          if (
            !event.payload().path("sequence").isIntegralNumber() ||
            !event.payload().path("sequence").canConvertToLong() ||
            event.payload().path("sequence").asLong(-1) < 0 ||
            event.payload().path("sequence").asLong(-1) >=
              connection.serverSequence
          ) throw new IllegalArgumentException("ACK_SEQUENCE");
        }
        case "audio.start" -> startAudio(connection, event);
        case "audio.stop" -> {
          Capture capture = requireCapture(connection, event);
          if (capture.transcript == null && !capture.finishing) {
            capture.finishing = true;
            capture.stoppedAt = System.nanoTime();
            capture.stream.finish();
          }
        }
        case "audio.commit" -> commit(connection, event);
        case "control.interrupt" -> {
          connection.generationCancelled.set(true);
          cancelOutput(connection);
          if (
            !event.payload().path("inputContinues").asBoolean(false)
          ) cancelCapture(connection);
          send(
            connection,
            "control.ack",
            event.requestId(),
            Map.of(
              "interrupted",
              true,
              "evaluationContinues",
              "EVALUATING".equals(connection.state.get("state")),
              "generationCancelled",
              true
            )
          );
        }
        case "control.pause", "control.resume", "control.end" -> {
          connection.generationCancelled.set(true);
          cancelOutput(connection);
          cancelCapture(connection);
          String operation = switch (event.type()) {
            case "control.pause" -> "pause";
            case "control.resume" -> "resume";
            default -> "finish";
          };
          connection.state = interviews.command(
            connection.identity.user(),
            connection.identity.session(),
            operation,
            new InterviewV2Service.Command(
              requiredVersion(event),
              event.requestId()
            )
          );
          send(
            connection,
            event.type().equals("control.pause")
              ? "session.paused"
              : event.type().equals("control.resume")
                ? "session.resumed"
                : "session.completed",
            event.requestId(),
            Map.of("state", connection.state)
          );
          if (!"finish".equals(operation)) publishState(connection, true);
        }
        default -> throw new IllegalArgumentException("UNSUPPORTED_EVENT");
      }
    } catch (BusinessException business) {
      throw business;
    } catch (Exception invalid) {
      cancelCapture(connection);
      error(
        connection,
        "INVALID_VOICE_MESSAGE",
        "语音消息顺序、格式或限额无效，请重新录入或使用文字"
      );
    }
  }

  private void publishState(Connection connection, boolean announce) {
    var state = access.state(
      connection.identity.user(),
      connection.identity.session()
    );
    connection.state = state;
    long version = ((Number) state.get("version")).longValue();
    if (
      connection.capture != null && connection.capture.version != version
    ) cancelCapture(connection);
    if ("PAUSED".equals(state.get("state"))) cancelOutput(connection);
    boolean changed = version != connection.lastVersion;
    connection.lastVersion = version;
    if (changed || announce) send(
      connection,
      "session.state",
      "",
      Map.of("state", state)
    );
    if ("COMPLETED".equals(state.get("state"))) {
      cancelCapture(connection);
      cancelOutput(connection);
      if (changed) send(
        connection,
        "session.completed",
        "",
        Map.of("state", state)
      );
      return;
    }
    if (
      ("ASKING".equals(state.get("state")) ||
        "FOLLOW_UP".equals(state.get("state"))) &&
      state.get("currentQuestion") instanceof Map<?, ?> question
    ) {
      String title = String.valueOf(question.get("title"));
      String key = version + "/" + question.get("id");
      if (!key.equals(connection.lastQuestion)) {
        connection.lastQuestion = key;
        send(
          connection,
          "llm.completed",
          "",
          Map.of(
            "text",
            title,
            "source",
            "V2_VALIDATED",
            "streamed",
            false,
            "turnLatencyMs",
            connection.thinkingAt == 0
              ? 0
              : TimeUnit.NANOSECONDS.toMillis(
                  System.nanoTime() - connection.thinkingAt
                )
          )
        );
        if (connection.voiceOutput) speak(connection, title);
      }
    }
  }

  /** 只恢复服务端仍未被原V2消费的最终候选，不使用localStorage冒充持久化。 */
  private void restore(Connection connection) {
    if (
      !Set.of("ASKING", "FOLLOW_UP").contains(
        String.valueOf(connection.state.get("state"))
      )
    ) return;
    var question = (Map<?, ?>) connection.state.get("currentQuestion");
    long version = ((Number) connection.state.get("version")).longValue();
    String id = String.valueOf(question.get("id"));
    var saved = transcripts.findPendingFinal(
      connection.identity.user(),
      connection.identity.session(),
      id,
      version
    );
    if (saved == null) return;
    try {
      var capture = new Capture(
        saved.requestId(),
        id,
        asr.capabilities().provider(),
        version,
        ++connection.inputId
      );
      capture.transcript = saved.transcript();
      connection.capture = capture;
      send(
        connection,
        "asr.final",
        capture.request,
        Map.of(
          "text",
          capture.transcript.rawText(),
          "speechId",
          capture.transcript.id(),
          "requestId",
          capture.request,
          "restored",
          true
        )
      );
    } catch (Exception invalid) {
      error(
        connection,
        "DRAFT_RESTORE_FAILED",
        "语音草稿恢复失败，可使用文字或重新录入"
      );
    }
  }

  private Capture requireCapture(
    Connection connection,
    VoiceProtocol.Event event
  ) {
    Capture capture = connection.capture;
    if (
      capture == null ||
      !capture.request.equals(event.requestId()) ||
      !event.turnId().equals(capture.version + "/" + capture.question)
    ) throw new BusinessException(409, "语音轮次已变化，请重新录入");
    return capture;
  }

  private long requiredVersion(VoiceProtocol.Event event) {
    var value = event.payload().path("version");
    if (
      !value.isIntegralNumber() ||
      !value.canConvertToLong() ||
      value.asLong() < 0
    ) throw new BusinessException(400, "版本必须是非负整数");
    return value.asLong();
  }

  private void startAudio(Connection connection, VoiceProtocol.Event event)
    throws Exception {
    publishState(connection, false);
    if (
      !Set.of("ASKING", "FOLLOW_UP").contains(
        String.valueOf(connection.state.get("state"))
      )
    ) throw new BusinessException(409, "当前面试状态不能录入语音");
    long version = ((Number) connection.state.get("version")).longValue();
    var question = (Map<?, ?>) connection.state.get("currentQuestion");
    String questionId = String.valueOf(question.get("id"));
    if (
      !event.turnId().equals(version + "/" + questionId) ||
      requiredVersion(event) != version
    ) throw new BusinessException(409, "当前题目已变化");
    cancelCapture(connection);
    boolean allowBargeIn = event
      .payload()
      .path("allowBargeIn")
      .asBoolean(false);
    if (!allowBargeIn) cancelOutput(connection);
    var reservation = transcripts.begin(
      connection.identity.user(),
      connection.identity.session(),
      questionId,
      event.requestId(),
      version,
      asr.capabilities().provider()
    );
    var capture = new Capture(
      event.requestId(),
      questionId,
      asr.capabilities().provider(),
      version,
      ++connection.inputId
    );
    capture.allowBargeIn = allowBargeIn;
    connection.capture = capture;
    if (!reservation.created()) {
      if ("TRANSCRIBED".equals(reservation.transcript().status())) {
        capture.transcript = reservation.transcript();
        send(
          connection,
          "asr.final",
          capture.request,
          Map.of(
            "text",
            capture.transcript.rawText(),
            "speechId",
            capture.transcript.id(),
            "replayed",
            true
          )
        );
        return;
      }
      connection.capture = null;
      throw new BusinessException(409, "录音请求已处理或中断，请新建录音请求");
    }
    capture.stream = asr.open(
      event.requestId(),
      new InterviewStreamingAsrProvider.Listener() {
        public void partial(String text) {
          offer(connection, () -> {
            if (connection.capture != capture) return;
            if (
              text == null || text.length() + capture.finals.length() > 8000
            ) {
              failCapture(connection, "ASR_TEXT_LIMIT");
              return;
            }
            if (capture.firstPartialAt == 0) {
              capture.firstPartialAt = System.nanoTime();
              log.info(
                "VOICE_ASR_FIRST_PARTIAL sessionId={} requestId={} latencyMs={}",
                connection.identity.session(),
                capture.request,
                capture.firstAudioAt == 0
                  ? 0
                  : TimeUnit.NANOSECONDS.toMillis(
                      capture.firstPartialAt - capture.firstAudioAt
                    )
              );
            }
            send(
              connection,
              "asr.partial",
              capture.request,
              Map.of(
                "text",
                capture.finals + text,
                "latencyMs",
                capture.firstAudioAt == 0
                  ? 0
                  : TimeUnit.NANOSECONDS.toMillis(
                      capture.firstPartialAt - capture.firstAudioAt
                    )
              )
            );
          });
        }

        public void finalText(String text) {
          offer(connection, () -> {
            if (connection.capture != capture) return;
            if (
              text == null || capture.finals.length() + text.length() > 8000
            ) {
              failCapture(connection, "ASR_TEXT_LIMIT");
              return;
            }
            if (!text.isBlank()) {
              if (!capture.finals.isEmpty()) capture.finals.append('\n');
              capture.finals.append(text);
              send(
                connection,
                "asr.partial",
                capture.request,
                Map.of("text", capture.finals.toString(), "segmentFinal", true)
              );
            }
          });
        }

        public void speechStarted() {
          offer(connection, () -> {
            if (connection.capture != capture) return;
            send(connection, "speech.started", capture.request, Map.of());
            // 原生VAD仅在明确允许收音打断且朗读冷却期后生效；不自动授权麦克风。
            if (
              capture.allowBargeIn &&
              connection.output != null &&
              System.nanoTime() - connection.outputStartedAt >=
                TimeUnit.MILLISECONDS.toNanos(300)
            ) {
              connection.generationCancelled.set(true);
              cancelOutput(connection);
              send(
                connection,
                "control.ack",
                capture.request,
                Map.of("bargeIn", true)
              );
            }
          });
        }

        public void speechEnded() {
          offer(connection, () -> {
            if (connection.capture == capture) send(
              connection,
              "speech.ended",
              capture.request,
              Map.of("manualCommitRequired", true)
            );
          });
        }

        public void completed() {
          offer(connection, () -> {
            if (connection.capture != capture) return;
            try {
              if (
                "COMPLETED".equals(
                  access
                    .state(
                      connection.identity.user(),
                      connection.identity.session()
                    )
                    .get("state")
                )
              ) throw new BusinessException(409, "面试已结束");
              long duration = (capture.bytes * 1000) / 32000;
              capture.transcript = transcripts.saveFinal(
                connection.identity.user(),
                connection.identity.session(),
                capture.question,
                capture.request,
                capture.version,
                capture.finals.toString(),
                HexFormat.of().formatHex(capture.digest.digest()),
                duration,
                capture.provider
              );
              send(
                connection,
                "asr.final",
                capture.request,
                Map.of(
                  "text",
                  capture.transcript.rawText(),
                  "speechId",
                  capture.transcript.id(),
                  "latencyMs",
                  capture.stoppedAt == 0
                    ? 0
                    : TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - capture.stoppedAt
                      )
                )
              );
              capture.stream.cancel();
            } catch (RuntimeException invalid) {
              failCapture(connection, "ASR_FINAL_REJECTED");
            }
          });
        }

        public void error(String code) {
          offer(connection, () -> {
            if (connection.capture == capture) failCapture(connection, code);
          });
        }
      }
    );
    if (connection.closed.get() || connection.capture != capture) {
      capture.stream.cancel();
      return;
    }
    send(
      connection,
      "audio.accepted",
      capture.request,
      Map.of(
        "streamId",
        capture.id,
        "sampleRate",
        16000,
        "format",
        "PCM16",
        "channels",
        1,
        "maxDurationSeconds",
        60
      )
    );
  }

  private void audio(Connection connection, byte[] bytes) {
    try {
      rate(connection, true);
      var frame = protocol.frame(bytes);
      var capture = connection.capture;
      if (
        capture == null ||
        capture.finishing ||
        capture.transcript != null ||
        frame.streamId() != capture.id
      ) throw new IllegalArgumentException("AUDIO_CONTEXT");
      if (frame.sequence() < capture.sequence) {
        connection.duplicateAudioFrames++;
        return;
      }
      if (
        frame.sequence() != capture.sequence
      ) throw new IllegalArgumentException("AUDIO_SEQUENCE");
      if (
        capture.bytes + frame.pcm().length > 1920000
      ) throw new IllegalArgumentException("AUDIO_LIMIT");
      capture.sequence++;
      capture.bytes += frame.pcm().length;
      if (capture.firstAudioAt == 0) capture.firstAudioAt = System.nanoTime();
      capture.digest.update(frame.pcm());
      capture.stream.audio(frame.pcm());
      Arrays.fill(frame.pcm(), (byte) 0);
    } catch (Exception invalid) {
      failCapture(connection, "AUDIO_FRAME_REJECTED");
    } finally {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  private void commit(Connection connection, VoiceProtocol.Event event) {
    Capture capture = requireCapture(connection, event);
    if (capture.transcript == null) throw new BusinessException(
      409,
      "最终转写尚未就绪"
    );
    String answer = event.payload().path("text").asText();
    if (answer.isBlank() || answer.length() > 8000) throw new BusinessException(
      400,
      "请先校对回答文字"
    );
    connection.generationCancelled.set(true);
    var cancelled = new AtomicBoolean();
    connection.generationCancelled = cancelled;
    var request = new InterviewV2Service.Answer(
      capture.version,
      event.requestId(),
      answer,
      null,
      capture.transcript.id()
    );
    if (interviews.streamingAvailable(connection.identity.user())) {
      connection.state = interviews.answerStreaming(
        connection.identity.user(),
        connection.identity.session(),
        request,
        delta ->
          offer(connection, () -> {
            if (
              connection.generationCancelled == cancelled && !cancelled.get()
            ) send(
              connection,
              "llm.delta",
              capture.request,
              Map.of("text", delta, "provisional", true)
            );
          }),
        cancelled::get
      );
    } else connection.state = interviews.answer(
      connection.identity.user(),
      connection.identity.session(),
      request
    );
    connection.thinkingAt = System.nanoTime();
    send(
      connection,
      "turn.accepted",
      capture.request,
      Map.of("state", connection.state, "speechId", capture.transcript.id())
    );
    connection.capture = null;
    cancelOutput(connection);
  }

  private void speak(Connection connection, String text) {
    cancelOutput(connection);
    if (!tts.capabilities().available()) {
      send(
        connection,
        "tts.completed",
        "",
        Map.of("available", false, "reason", "语音输出未配置，文字完整保留")
      );
      return;
    }
    int generation = ++connection.outputId;
    connection.outputSequence = 0;
    connection.providerSequence = 0;
    long started = System.nanoTime();
    connection.outputStartedAt = started;
    send(
      connection,
      "tts.started",
      "",
      Map.of(
        "streamId",
        generation,
        "format",
        tts.capabilities().format(),
        "sampleRate",
        tts.capabilities().sampleRate(),
        "channels",
        1
      )
    );
    var output = tts.synthesizeStream(
      text,
      UUID.randomUUID().toString(),
      new InterviewTtsProvider.Listener() {
        public void audio(
          byte[] pcm,
          String format,
          int sampleRate,
          long sequence,
          boolean last
        ) {
          if (pcm == null || pcm.length > 32768 || pcm.length % 2 != 0) {
            offer(connection, () -> {
              if (generation == connection.outputId) {
                cancelOutput(connection);
                InterviewVoiceSocket.this.error(
                  connection,
                  "TTS_AUDIO_LIMIT",
                  "语音输出格式超限，文字保留"
                );
              }
            });
            return;
          }
          byte[] owned = pcm.clone();
          offer(connection, () -> {
            try {
              if (generation != connection.outputId) return;
              if (
                !"PCM16".equals(format) ||
                sampleRate != tts.capabilities().sampleRate() ||
                sequence != connection.providerSequence++
              ) {
                cancelOutput(connection);
                InterviewVoiceSocket.this.error(
                  connection,
                  "TTS_AUDIO_SEQUENCE",
                  "语音输出乱序，文字保留"
                );
                return;
              }
              if (connection.outputSequence == 0 && owned.length > 0) log.info(
                "VOICE_TTS_FIRST_AUDIO sessionId={} latencyMs={}",
                connection.identity.session(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
              );
              for (int offset = 0; offset < owned.length; offset += 8192) {
                byte[] part = Arrays.copyOfRange(
                  owned,
                  offset,
                  Math.min(owned.length, offset + 8192)
                );
                connection.socket.sendMessage(
                  new BinaryMessage(
                    protocol.output(
                      generation,
                      connection.outputSequence++,
                      part
                    )
                  )
                );
              }
              if (last) {
                send(
                  connection,
                  "tts.completed",
                  "",
                  Map.of(
                    "streamId",
                    generation,
                    "elapsedMs",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                  )
                );
                connection.output = null;
              }
            } catch (Exception invalid) {
              cancelOutput(connection);
              InterviewVoiceSocket.this.error(
                connection,
                "TTS_OUTPUT_FAILED",
                "语音播放传输失败，文字保留"
              );
            } finally {
              Arrays.fill(owned, (byte) 0);
            }
          });
        }

        public void error(String code) {
          offer(connection, () -> {
            if (generation == connection.outputId) {
              cancelOutput(connection);
              InterviewVoiceSocket.this.error(
                connection,
                code,
                "语音合成失败，文字保留"
              );
            }
          });
        }
      }
    );
    if (
      connection.closed.get() || generation != connection.outputId
    ) output.cancel();
    else connection.output = output;
  }

  private void cancelOutput(Connection connection) {
    connection.outputId++;
    if (connection.output != null) {
      connection.output.cancel();
      connection.output = null;
    }
  }

  private void cancelCapture(Connection connection) {
    Capture capture = connection.capture;
    connection.capture = null;
    if (capture != null) {
      if (capture.stream != null) capture.stream.cancel();
      if (capture.transcript == null) transcripts.fail(
        connection.identity.user(),
        connection.identity.session(),
        capture.request,
        "CANCELLED"
      );
    }
  }

  private void failCapture(Connection connection, String code) {
    cancelCapture(connection);
    error(connection, code, "语音识别未完成，请重新录入或使用文字作答");
  }

  private void send(
    Connection connection,
    String type,
    String request,
    Map<String, ?> payload
  ) {
    if (connection.closed.get()) return;
    if (
      !protocol.allowed("serverEvents", type)
    ) throw new IllegalArgumentException("SERVER_EVENT");
    try {
      connection.socket.sendMessage(
        new TextMessage(
          json.writeValueAsString(
            Map.of(
              "protocolVersion",
              1,
              "eventId",
              UUID.randomUUID().toString(),
              "sessionId",
              connection.identity.session(),
              "turnId",
              connection.state == null
                ? ""
                : connection.state.get("currentQuestion") instanceof Map<?, ?> q
                  ? connection.state.get("version") + "/" + q.get("id")
                  : String.valueOf(connection.state.get("version")),
              "requestId",
              request.isBlank() ? UUID.randomUUID().toString() : request,
              "sequence",
              connection.serverSequence++,
              "timestamp",
              System.currentTimeMillis(),
              "type",
              type,
              "payload",
              payload
            )
          )
        )
      );
    } catch (Exception failed) {
      disconnect(connection);
    }
  }

  private void error(Connection connection, String code, String message) {
    send(
      connection,
      "error",
      "",
      Map.of(
        "code",
        code == null || !code.matches("[A-Z0-9_]{1,64}") ? "VOICE_ERROR" : code,
        "message",
        message
      )
    );
  }

  @Scheduled(fixedDelay = 1000)
  public void scan() {
    for (var connection : connections.values()) {
      if (System.currentTimeMillis() - connection.receivedAt > 45000) {
        disconnect(connection);
        continue;
      }
      offer(connection, () -> {
        if (
          connection.state != null &&
          "EVALUATING".equals(connection.state.get("state"))
        ) publishState(connection, false);
      });
    }
  }

  @Override
  public void afterConnectionClosed(
    WebSocketSession socket,
    CloseStatus status
  ) {
    Connection connection = connections.get(socket.getId());
    if (connection != null) disconnect(connection);
  }

  @Override
  public void handleTransportError(WebSocketSession socket, Throwable failure) {
    Connection connection = connections.get(socket.getId());
    if (connection != null) disconnect(connection);
  }

  private void disconnect(Connection connection) {
    if (!connection.closed.compareAndSet(false, true)) return;
    connection.generationCancelled.set(true);
    connections.remove(connection.socket.getId());
    log.info(
      "VOICE_CLOSED sessionId={} duplicateAudioFrames={}",
      connection.identity.session(),
      connection.duplicateAudioFrames
    );
    slots.release();
    connection.queue.clear();
    try {
      connection.socket.close(CloseStatus.NORMAL);
    } catch (Exception ignored) {
      /* 连接已断开，不记录音频或鉴权资料。 */
    }
    Runnable cleanup = () -> {
      cancelOutput(connection);
      cancelCapture(connection);
      tickets.close(connection.identity);
    };
    try {
      workers.execute(cleanup);
    } catch (RuntimeException full) {
      if (connection.output != null) connection.output.cancel();
      if (
        connection.capture != null && connection.capture.stream != null
      ) connection.capture.stream.cancel();
    }
  }

  @PreDestroy
  public void close() {
    for (var connection : connections.values()) disconnect(connection);
    workers.shutdown();
  }
}
