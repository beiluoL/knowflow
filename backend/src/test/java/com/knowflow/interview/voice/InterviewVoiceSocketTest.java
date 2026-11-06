package com.knowflow.interview.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowflow.interview.speech.*;
import com.knowflow.interview.service.v2.InterviewV2Service;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import com.knowflow.interview.service.speech.InterviewSpeechService;
import com.knowflow.interview.service.speech.InterviewStreamingTranscriptService;
import com.knowflow.interview.service.voice.VoiceAccess;
import com.knowflow.interview.speech.InterviewStreamingAsrProvider;
import com.knowflow.interview.speech.InterviewTtsProvider;
import com.knowflow.interview.speech.SpeechStreamCapabilities;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际协议与会话队列，Provider/V2 使用测试替身；不产生网络或计费调用。 */
class InterviewVoiceSocketTest {
    private final ObjectMapper json = new ObjectMapper();
    private final BlockingQueue<JsonNode> outgoing = new LinkedBlockingQueue<>();
    private final AtomicReference<InterviewStreamingAsrProvider.Listener> listener = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> state = new AtomicReference<>();
    private VoiceAccess access;
    private VoiceTicketStore tickets;
    private InterviewV2Service interviews;
    private InterviewStreamingAsrProvider asr;
    private InterviewStreamingAsrProvider.Stream stream;
    private InterviewTtsProvider tts;
    private InterviewStreamingTranscriptService transcripts;
    private InterviewVoiceSocket handler;
    private WebSocketSession socket;
    private final VoiceTicketStore.Identity identity = new VoiceTicketStore.Identity(7L, 11L, "digest");

    @BeforeEach
    void setup() throws Exception {
        access = mock(VoiceAccess.class);
        tickets = mock(VoiceTicketStore.class);
        interviews = mock(InterviewV2Service.class);
        asr = mock(InterviewStreamingAsrProvider.class);
        stream = mock(InterviewStreamingAsrProvider.Stream.class);
        tts = mock(InterviewTtsProvider.class);
        transcripts = mock(InterviewStreamingTranscriptService.class);
        socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("test-socket");
        when(socket.isOpen()).thenReturn(true);
        when(socket.getAttributes()).thenReturn(Map.of("voiceIdentity", identity));
        doAnswer(invocation -> {
            WebSocketMessage<?> message = invocation.getArgument(0);
            if (message instanceof TextMessage text) outgoing.add(json.readTree(text.getPayload()));
            return null;
        }).when(socket).sendMessage(any());
        state.set(Map.of("state", "ASKING", "version", 4L,
                "currentQuestion", Map.of("id", "q1", "title", "服务端已校验的问题")));
        when(access.state(7L, 11L)).thenAnswer(invocation -> state.get());
        when(access.capability(7L, 11L)).thenReturn(Map.of("enabled", true));
        when(tickets.renew(identity)).thenReturn(true);
        when(asr.capabilities()).thenReturn(new SpeechStreamCapabilities(true, "QWEN_REALTIME_ASR", "PCM16", 16000));
        when(tts.capabilities()).thenReturn(new SpeechStreamCapabilities(false, "QWEN_TTS", "PCM16", 24000));
        when(asr.open(anyString(), any())).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(1));
            return stream;
        });
        when(transcripts.begin(eq(7L), eq(11L), eq("q1"), anyString(), eq(4L), eq("QWEN_REALTIME_ASR")))
                .thenReturn(new InterviewStreamingTranscriptService.Reservation(
                        new InterviewSpeechService.SpeechTranscript(31L, "q1", "TRANSCRIBING", null, null), true));
        handler = new InterviewVoiceSocket(json, new VoiceProtocol(json), access, tickets,
                interviews, asr, tts, transcripts);
        handler.afterConnectionEstablished(socket);
        await("session.ready");
    }

    @AfterEach
    void cleanup() {
        handler.close();
    }

    private ObjectNode event(String type, long sequence, String eventId) {
        var node = json.createObjectNode();
        node.put("protocolVersion", 1).put("sessionId", 11L).put("eventId", eventId)
                .put("requestId", "request_001").put("turnId", "4/q1").put("sequence", sequence)
                .put("timestamp", System.currentTimeMillis()).put("type", type);
        node.putObject("payload").put("version", 4L);
        return node;
    }

    private void send(ObjectNode event) throws Exception {
        handler.handleMessage(socket, new TextMessage(json.writeValueAsString(event)));
    }

    private JsonNode await(String type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var message = outgoing.poll(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            assertNotNull(message, "Missing server event " + type);
            if (type.equals(message.path("type").asText())) return message;
        }
        fail("Missing server event " + type);
        return null;
    }

    private int beginAudio() throws Exception {
        send(event("audio.start", 0L, "event_001"));
        return await("audio.accepted").path("payload").path("streamId").asInt();
    }

    private void binary(int id, long sequence) throws Exception {
        handler.handleMessage(socket, new BinaryMessage(ByteBuffer.allocate(1292)
                .putInt(1262900785).putInt(id).putInt((int) sequence).array()));
    }

    @Test
    void sameEventIsAcknowledgedWithoutOpeningSecondProvider() throws Exception {
        beginAudio();
        send(event("audio.start", 0L, "event_001"));
        assertTrue(await("control.ack").path("payload").path("duplicate").asBoolean());
        verify(asr, times(1)).open(eq("request_001"), any());
        verify(transcripts, times(1)).begin(7L, 11L, "q1", "request_001", 4L, "QWEN_REALTIME_ASR");
    }

    @Test
    void foreignSessionAndSequenceGapCannotOpenProvider() throws Exception {
        send(event("audio.start", 0L, "event_001").put("sessionId", 12L));
        assertEquals("INVALID_VOICE_MESSAGE", await("error").path("payload").path("code").asText());
        send(event("audio.start", 2L, "event_002"));
        await("error");
        verify(asr, never()).open(anyString(), any());
        verify(transcripts, never()).begin(any(), anyLong(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void repeatedBinaryFrameIsIgnoredAndGapCancelsCapture() throws Exception {
        int id = beginAudio();
        binary(id, 0L);
        binary(id, 0L);
        send(event("heartbeat", 1L, "event_002"));
        await("heartbeat.ack"); // 同一串行队列上的屏障，证明之前的帧均已处理。
        verify(stream, times(1)).audio(any());
        binary(id, 2L);
        assertEquals("AUDIO_FRAME_REJECTED", await("error").path("payload").path("code").asText());
        verify(stream).cancel();
        verify(transcripts).fail(7L, 11L, "request_001", "CANCELLED");
    }

    @Test
    void finalIsPersistedBeforeExplicitCommitAndOnlyReviewedTextReachesV2() throws Exception {
        int id = beginAudio();
        for (int index = 0; index < 10; index++) binary(id, index);
        send(event("audio.stop", 1L, "event_002"));
        verify(stream, timeout(5000)).finish();
        var transcript = new InterviewSpeechService.SpeechTranscript(31L, "q1", "TRANSCRIBED", "真实转写", null);
        when(transcripts.saveFinal(eq(7L), eq(11L), eq("q1"), eq("request_001"), eq(4L),
                eq("真实转写"), matches("[a-f0-9]{64}"), eq(400L), eq("QWEN_REALTIME_ASR"))).thenReturn(transcript);
        listener.get().finalText("真实转写");
        listener.get().completed();
        var finalEvent = await("asr.final");
        assertEquals(31L, finalEvent.path("payload").path("speechId").asLong());
        verify(transcripts).saveFinal(eq(7L), eq(11L), eq("q1"), eq("request_001"), eq(4L),
                eq("真实转写"), matches("[a-f0-9]{64}"), eq(400L), eq("QWEN_REALTIME_ASR"));
        verify(interviews, never()).answer(any(), anyLong(), any());
        when(interviews.answer(eq(7L), eq(11L), any())).thenReturn(Map.of("state", "EVALUATING", "version", 5L));
        var commit = event("audio.commit", 2L, "event_003");
        ((ObjectNode) commit.path("payload")).put("text", "用户校对后的回答");
        send(commit);
        await("turn.accepted");
        verify(interviews).answer(7L, 11L,
                new InterviewV2Service.Answer(4L, "request_001", "用户校对后的回答", null, 31L));
    }

    @Test
    void endCancelsCaptureAndConnectionCloseReleasesTicket() throws Exception {
        beginAudio();
        when(interviews.command(eq(7L), eq(11L), eq("finish"), any()))
                .thenReturn(Map.of("state", "COMPLETED", "version", 5L));
        send(event("control.end", 1L, "event_002"));
        await("session.completed");
        verify(stream).cancel();
        verify(transcripts).fail(7L, 11L, "request_001", "CANCELLED");
        handler.afterConnectionClosed(socket, CloseStatus.NORMAL);
        verify(tickets, timeout(5000)).close(identity);
    }

    @Test
    void ttsFailureLeavesValidatedQuestionTextAvailable() throws Exception {
        when(tts.capabilities()).thenReturn(new SpeechStreamCapabilities(true, "QWEN_TTS", "PCM16", 24000));
        when(tts.synthesizeStream(anyString(), anyString(), any())).thenAnswer(invocation -> {
            InterviewTtsProvider.Listener callback = invocation.getArgument(2);
            callback.error("TTS_PROVIDER_ERROR");
            return mock(InterviewTtsProvider.Stream.class);
        });
        var start = event("session.start", 0L, "event_001");
        ((ObjectNode) start.path("payload")).put("voiceOutput", true);
        send(start);
        assertEquals("服务端已校验的问题", await("llm.completed").path("payload").path("text").asText());
        var error = await("error");
        assertEquals("TTS_PROVIDER_ERROR", error.path("payload").path("code").asText());
        assertTrue(error.path("payload").path("message").asText().contains("文字保留"));
        verify(interviews, never()).answer(any(), anyLong(), any());
    }
    @Test
    void nativeVadBargeInRequiresOptInAndOutputCooldown() throws Exception {
        var output = mock(InterviewTtsProvider.Stream.class);
        when(tts.capabilities()).thenReturn(new SpeechStreamCapabilities(true, "QWEN_TTS", "PCM16", 24000));
        when(tts.synthesizeStream(anyString(), anyString(), any())).thenReturn(output);
        var start = event("session.start", 0L, "event_001");
        ((ObjectNode) start.path("payload")).put("voiceOutput", true);
        send(start);
        await("tts.started");
        var input = event("audio.start", 1L, "event_002");
        ((ObjectNode) input.path("payload")).put("allowBargeIn", true);
        send(input);
        await("audio.accepted");
        verify(output, never()).cancel();
        setOutputStartedAt(System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
        listener.get().speechStarted();
        await("speech.started");
        send(event("heartbeat", 2L, "event_003"));
        await("heartbeat.ack");
        verify(output, never()).cancel();
        setOutputStartedAt(System.nanoTime() - TimeUnit.SECONDS.toNanos(1));
        listener.get().speechStarted();
        var ack = await("control.ack");
        assertTrue(ack.path("payload").path("bargeIn").asBoolean());
        verify(output).cancel();
        verify(stream, never()).cancel();
    }

    @Test
    void recordingWithoutBargeInOptInStopsTtsImmediately() throws Exception {
        var output = mock(InterviewTtsProvider.Stream.class);
        when(tts.capabilities()).thenReturn(new SpeechStreamCapabilities(true, "QWEN_TTS", "PCM16", 24000));
        when(tts.synthesizeStream(anyString(), anyString(), any())).thenReturn(output);
        var start = event("session.start", 0L, "event_001");
        ((ObjectNode) start.path("payload")).put("voiceOutput", true);
        send(start);
        await("tts.started");
        send(event("audio.start", 1L, "event_002"));
        await("audio.accepted");
        verify(output).cancel();
        verify(stream, never()).cancel();
    }

    private void setOutputStartedAt(long time) throws Exception {
        // 仅测试控制冷却时间，避免依赖真实等待；通过队列屏障后操作私有连接状态。
        var field = InterviewVoiceSocket.class.getDeclaredField("connections");
        field.setAccessible(true);
        Object connection = ((Map<?, ?>) field.get(handler)).get("test-socket");
        var started = connection.getClass().getDeclaredField("outputStartedAt");
        started.setAccessible(true);
        started.setLong(connection, time);
    }

    @Test
    void streamingGenerationInterruptionFlipsCancellationAndSuppressesLateDelta() throws Exception {
        int id = beginAudio();
        for (int index = 0; index < 5; index++) binary(id, index);
        send(event("audio.stop", 1L, "event_002"));
        verify(stream, timeout(5000)).finish();
        when(transcripts.saveFinal(eq(7L), eq(11L), eq("q1"), eq("request_001"), eq(4L),
                eq("真实转写"), anyString(), eq(200L), eq("QWEN_REALTIME_ASR")))
                .thenReturn(new InterviewSpeechService.SpeechTranscript(31L, "q1", "TRANSCRIBED", "真实转写", null));
        listener.get().finalText("真实转写");
        listener.get().completed();
        await("asr.final");
        var delta = new AtomicReference<java.util.function.Consumer<String>>();
        var cancelled = new AtomicReference<java.util.function.BooleanSupplier>();
        when(interviews.streamingAvailable(7L)).thenReturn(true);
        when(interviews.answerStreaming(eq(7L), eq(11L), any(), any(), any())).thenAnswer(invocation -> {
            delta.set(invocation.getArgument(3));
            cancelled.set(invocation.getArgument(4));
            state.set(Map.of("state", "EVALUATING", "version", 5L));
            return state.get();
        });
        var commit = event("audio.commit", 2L, "event_003");
        ((ObjectNode) commit.path("payload")).put("text", "人工确认回答");
        send(commit);
        await("turn.accepted");
        assertFalse(cancelled.get().getAsBoolean());
        delta.get().accept("真实流回调测试片段");
        assertEquals("真实流回调测试片段", await("llm.delta").path("payload").path("text").asText());
        send(event("control.interrupt", 3L, "event_004"));
        assertTrue(await("control.ack").path("payload").path("generationCancelled").asBoolean());
        assertTrue(cancelled.get().getAsBoolean());
        delta.get().accept("中断后的迟到片段");
        send(event("heartbeat", 4L, "event_005"));
        // 收集到 heartbeat 前的所有事件，证明迟到回调已在串行队列中被丢弃。
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean barrier = false;
        while (!barrier && System.nanoTime() < deadline) {
            var message = outgoing.poll(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            assertNotNull(message);
            assertNotEquals("llm.delta", message.path("type").asText());
            barrier = "heartbeat.ack".equals(message.path("type").asText());
        }
        assertTrue(barrier);
        verify(interviews, never()).answer(any(), anyLong(), any());
    }

}
