package com.knowflow.interview.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 离线契约检查，不使用凭据、DNS、套接字或付费供应方请求。 */
class RealtimeSpeechProviderTest {
    @Test void disabledByDefaultAndIndependentOfBatch() {
        var config = new RealtimeSpeechProperties();
        var json = new ObjectMapper();
        var asr = new QwenStreamingAsrProvider(config, json);
        var tts = new QwenTtsProvider(config, json);
        assertFalse(asr.capabilities().available());
        assertFalse(tts.capabilities().available());
        assertEquals(16000, asr.capabilities().sampleRate());
        assertEquals(24000, tts.capabilities().sampleRate());
        assertThrows(IllegalStateException.class, () -> asr.open("test", mock(InterviewStreamingAsrProvider.Listener.class)));
        assertThrows(IllegalStateException.class, () -> tts.synthesizeStream("hello", "test", mock(InterviewTtsProvider.Listener.class)));
    }

    @Test void configurationRequiresOptInAndStrictCloudUri() {
        var config = configured();
        assertTrue(QwenSpeechSocket.configured(config, config.getAsr()));
        for (String endpoint : List.of("ws://dashscope.aliyuncs.com/api-ws/v1/realtime",
                "wss://localhost/api-ws/v1/realtime", "wss://127.0.0.1/api-ws/v1/realtime",
                "wss://dashscope.aliyuncs.com.evil.test/api-ws/v1/realtime",
                "wss://key@dashscope.aliyuncs.com/api-ws/v1/realtime",
                "wss://dashscope.aliyuncs.com:8443/api-ws/v1/realtime",
                "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=x",
                "wss://dashscope.aliyuncs.com/api-ws/v1/realtime#x")) {
            config.getAsr().setEndpoint(endpoint);
            assertFalse(QwenSpeechSocket.configured(config, config.getAsr()), endpoint);
        }
        config = configured();
        config.getAsr().setEnabled(false);
        assertFalse(QwenSpeechSocket.configured(config, config.getAsr()));
    }

    @Test void fragmentedEventsAreBoundedAndSuppressedAfterCancel() {
        List<String> events = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        var socket = new QwenSpeechSocket(new ObjectMapper(), event -> events.add(event.path("type").asText()), errors::add, 1);
        var remote = mock(WebSocket.class);
        socket.onText(remote, "{\"type\":", false);
        assertTrue(events.isEmpty());
        socket.onText(remote, "\"partial\"}", true);
        assertEquals(List.of("partial"), events);
        socket.cancel();
        socket.onText(remote, "{\"type\":\"late\"}", true);
        assertEquals(List.of("partial"), events);
        assertTrue(errors.isEmpty());
    }

    @Test void malformedAndOversizedMessagesFailOnceWithoutExposingRemoteBody() {
        for (String payload : List.of("private invalid json", "x".repeat(262145),
                "{\"type\":\"error\",\"error\":{\"message\":\"private key\"}}")) {
            List<String> errors = new ArrayList<>();
            var socket = new QwenSpeechSocket(new ObjectMapper(), event -> fail("unexpected event"), errors::add, 1);
            var remote = mock(WebSocket.class);
            socket.onText(remote, payload, true);
            socket.onError(remote, new IllegalStateException("private upstream detail"));
            assertEquals(1, errors.size());
            assertFalse(errors.get(0).contains("private"));
        }
    }

    @Test void asrDeliversUtteranceFinalsBeforeSessionCompletionAndDropsLateEvents() {
        var provider = new QwenStreamingAsrProvider(new RealtimeSpeechProperties(), new ObjectMapper());
        var listener = mock(InterviewStreamingAsrProvider.Listener.class);
        var stream = provider.new AsrStream(listener);
        var remote = mock(WebSocket.class);
        stream.connection.onText(remote, "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"one\",\"transcript\":\"first\"}", true);
        stream.connection.onText(remote, "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"two\",\"transcript\":\"second\"}", true);
        stream.connection.onText(remote, "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"one\",\"transcript\":\"first\"}", true);
        verify(listener, never()).completed();
        stream.connection.onText(remote, "{\"type\":\"session.finished\"}", true);
        var order = inOrder(listener);
        order.verify(listener).finalText("first");
        order.verify(listener).finalText("second");
        order.verify(listener).completed();
        stream.connection.onText(remote, "{\"type\":\"session.finished\"}", true);
        verifyNoMoreInteractions(listener);

        var cancelled = provider.new AsrStream(listener);
        cancelled.cancel();
        cancelled.connection.onText(remote, "{\"type\":\"session.finished\"}", true);
        verifyNoMoreInteractions(listener);
    }

    @Test void asrKeepsRepeatedWordsForDistinctItemsAndBoundsDeduplication() {
        var provider = new QwenStreamingAsrProvider(new RealtimeSpeechProperties(), new ObjectMapper());
        var listener = mock(InterviewStreamingAsrProvider.Listener.class);
        var stream = provider.new AsrStream(listener);
        var remote = mock(WebSocket.class);
        for (int index = 0; index < 257; index++) {
            stream.connection.onText(remote, "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"item-" + index + "\",\"transcript\":\"yes\"}", true);
        }
        verify(listener, times(256)).finalText("yes");
        verify(listener).error("SPEECH_LIMIT");
        stream.connection.onText(remote, "{\"type\":\"session.finished\"}", true);
        verify(listener, never()).completed();
    }

    @Test void asrReadinessRejectsInvalidVadConfiguration() {
        var config = configured();
        var provider = new QwenStreamingAsrProvider(config, new ObjectMapper());
        assertEquals(800, config.getVadSilenceDurationMs());
        assertTrue(provider.capabilities().available());
        for (double threshold : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -0.01, 1.01}) {
            config.setVadThreshold(threshold);
            assertFalse(provider.capabilities().available());
        }
        config.setVadThreshold(0.5);
        for (int silence : new int[] {199, 5001}) {
            config.setVadSilenceDurationMs(silence);
            assertFalse(provider.capabilities().available());
        }
        config.setVadThreshold(0);
        config.setVadSilenceDurationMs(200);
        assertTrue(provider.capabilities().available());
        config.setVadThreshold(1);
        config.setVadSilenceDurationMs(5000);
        assertTrue(provider.capabilities().available());
    }

    private RealtimeSpeechProperties configured() {
        var config = new RealtimeSpeechProperties();
        config.setEnabled(true);
        config.getAsr().setEnabled(true);
        config.getAsr().setEndpoint("wss://dashscope.aliyuncs.com/api-ws/v1/realtime");
        config.getAsr().setModel("qwen3-asr-flash-realtime");
        config.getAsr().setApiKey("offline-test-placeholder");
        return config;
    }
}
