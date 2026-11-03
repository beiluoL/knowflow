package com.knowflow.voice.asr;

import com.knowflow.interview.speech.InterviewStreamingAsrProvider;
import com.knowflow.interview.speech.SpeechStreamCapabilities;
import com.knowflow.voice.VoiceRuntimeProperties;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 本地 SenseVoice 的「流式接口适配器」：接口是流式的，能力不是。
 * <p>真实行为：把整段 PCM 缓冲到 {@code finish()}，再一次性调用本机转写服务，
 * 只产出 final，不产出 partial。因此 {@code partialSupported=false}，
 * 前端必须显示「按句转写/需校对」，不得宣称实时逐字。
 */
@Component
public class LocalBatchStreamingAsrProvider implements InterviewStreamingAsrProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalBatchStreamingAsrProvider.class);
    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_BYTES = 60 * 32000;

    private final LocalAsrClient client;
    private final VoiceRuntimeProperties properties;
    /** 项目规范：不允许裸 new Thread/Executors，统一用 Spring 管理的线程池。 */
    private final ThreadPoolTaskExecutor workers;

    private volatile SpeechStreamCapabilities cached;
    private volatile long cachedAt;

    public LocalBatchStreamingAsrProvider(LocalAsrClient client, VoiceRuntimeProperties properties) {
        this.client = client;
        this.properties = properties;
        this.workers = new ThreadPoolTaskExecutor();
        this.workers.setCorePoolSize(1);
        this.workers.setMaxPoolSize(2);
        this.workers.setQueueCapacity(8);
        this.workers.setThreadNamePrefix("voice-local-asr-");
        this.workers.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        this.workers.initialize();
    }

    @Override
    public SpeechStreamCapabilities capabilities() {
        int ttl = Math.max(1, properties.getCapabilityCacheSeconds());
        long now = System.nanoTime();
        SpeechStreamCapabilities snapshot = cached;
        if (snapshot != null && TimeUnit.NANOSECONDS.toSeconds(now - cachedAt) < ttl) return snapshot;
        boolean ready;
        if (!client.configured()) {
            ready = false;
        } else {
            var health = client.health();
            ready = health.ok() && health.enabled();
            if (!health.ok()) log.debug("VOICE_LOCAL_ASR_DOWN detail={}", health.detail());
        }
        snapshot = new SpeechStreamCapabilities(ready, "LOCAL_SENSEVOICE", "PCM16", SAMPLE_RATE, false);
        cached = snapshot;
        cachedAt = now;
        return snapshot;
    }

    @Override
    public Stream open(String requestId, Listener listener) {
        if (!client.configured()) throw new IllegalStateException("本地语音识别未启用");
        return new LocalStream(requestId, listener);
    }

    private final class LocalStream implements Stream {

        private final String requestId;
        private final Listener listener;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024);
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private int bytes;

        LocalStream(String requestId, Listener listener) {
            this.requestId = requestId;
            this.listener = listener;
        }

        @Override
        public synchronized void audio(byte[] pcm) {
            if (finished.get() || cancelled.get()) return;
            if (pcm == null || pcm.length == 0 || pcm.length % 2 != 0) {
                fail("SPEECH_AUDIO_INVALID");
                return;
            }
            if (bytes + pcm.length > MAX_BYTES) {
                fail("SPEECH_AUDIO_LIMIT");
                return;
            }
            bytes += pcm.length;
            buffer.write(pcm, 0, pcm.length);
        }

        @Override
        public void finish() {
            if (!finished.compareAndSet(false, true)) return;
            byte[] pcm;
            synchronized (this) {
                pcm = buffer.toByteArray();
                buffer.reset();
            }
            if (pcm.length < 6400) {
                fail("ASR_TOO_SHORT");
                return;
            }
            try {
                workers.execute(() -> transcribe(pcm));
            } catch (RuntimeException busy) {
                fail("ASR_BUSY");
            }
        }

        private void transcribe(byte[] pcm) {
            if (cancelled.get()) return;
            long started = System.nanoTime();
            try {
                String text = client.transcribe(pcm);
                if (cancelled.get()) return;
                if (text == null || text.isBlank()) {
                    fail("ASR_EMPTY");
                    return;
                }
                log.info("VOICE_LOCAL_ASR_DONE requestId={} audioMs={} latencyMs={}",
                        requestId, pcm.length / 32, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                listener.finalText(text);
                listener.completed();
            } catch (RuntimeException failure) {
                if (!cancelled.get()) fail("ASR_UNAVAILABLE");
            } finally {
                Arrays.fill(pcm, (byte) 0);
            }
        }

        private void fail(String code) {
            if (!cancelled.get()) listener.error(code);
        }

        @Override
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            synchronized (this) {
                buffer.reset();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        workers.shutdown();
    }
}
