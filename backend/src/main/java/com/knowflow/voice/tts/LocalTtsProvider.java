package com.knowflow.voice.tts;

import com.knowflow.interview.speech.InterviewTtsProvider;
import com.knowflow.interview.speech.SpeechStreamCapabilities;
import com.knowflow.voice.VoiceRuntimeProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 本机 TTS 合成：按引擎实际输出分块下发 PCM16，可取消。
 * <p>取消通过中断读取循环实现，已入队但未播放的音频由前端 {@code playback.interrupt()} 清空。
 */
@Component
public class LocalTtsProvider implements InterviewTtsProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalTtsProvider.class);
    private static final int MAX_TEXT_CHARS = 6000;

    private final LocalTtsClient client;
    private final VoiceRuntimeProperties properties;
    /** 项目规范：不允许裸 new Thread/Executors，统一用 Spring 管理的线程池。 */
    private final ThreadPoolTaskExecutor workers;

    private volatile SpeechStreamCapabilities cached;
    private volatile long cachedAt;

    public LocalTtsProvider(LocalTtsClient client, VoiceRuntimeProperties properties) {
        this.client = client;
        this.properties = properties;
        this.workers = new ThreadPoolTaskExecutor();
        this.workers.setCorePoolSize(1);
        this.workers.setMaxPoolSize(2);
        this.workers.setQueueCapacity(8);
        this.workers.setThreadNamePrefix("voice-local-tts-");
        this.workers.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        this.workers.initialize();
    }

    @Override
    public SpeechStreamCapabilities capabilities() {
        int ttl = Math.max(1, properties.getCapabilityCacheSeconds());
        long now = System.nanoTime();
        SpeechStreamCapabilities snapshot = cached;
        if (snapshot != null && TimeUnit.NANOSECONDS.toSeconds(now - cachedAt) < ttl) return snapshot;
        boolean ready = false;
        int rate = 24000;
        String engine = "";
        if (client.configured()) {
            var health = client.health();
            ready = health.ok() && health.enabled();
            if (health.sampleRate() > 0) rate = health.sampleRate();
            engine = health.engine();
        }
        String provider = engine.isBlank()
                ? "LOCAL_TTS"
                : "LOCAL_" + engine.toUpperCase(java.util.Locale.ROOT);
        snapshot = new SpeechStreamCapabilities(ready, provider, "PCM16", rate, true);
        cached = snapshot;
        cachedAt = now;
        return snapshot;
    }

    @Override
    public Stream synthesizeStream(String text, String requestId, Listener listener) {
        if (!client.configured()) throw new IllegalStateException("本地语音合成未启用");
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("合成文本无效");
        }
        LocalStream stream = new LocalStream(requestId, text, listener);
        stream.submit();
        return stream;
    }

    private final class LocalStream implements Stream {

        private final String requestId;
        private final String text;
        private final Listener listener;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicLong sequence = new AtomicLong();

        LocalStream(String requestId, String text, Listener listener) {
            this.requestId = requestId;
            this.text = text;
            this.listener = listener;
        }

        void submit() {
            try {
                workers.execute(this::run);
            } catch (RuntimeException busy) {
                listener.error("TTS_BUSY");
            }
        }

        private void run() {
            if (cancelled.get()) return;
            long started = System.nanoTime();
            AtomicLong firstAudioAt = new AtomicLong();
            try {
                int rate = client.stream(text, chunk -> {
                    if (cancelled.get()) throw new Cancelled();
                    if (chunk.pcm().length > 0) firstAudioAt.compareAndSet(0, System.nanoTime());
                    listener.audio(chunk.pcm(), "PCM16", chunk.sampleRate(), sequence.getAndIncrement(), chunk.last());
                });
                long first = firstAudioAt.get();
                log.info("VOICE_LOCAL_TTS_DONE requestId={} chars={} sampleRate={} firstAudioMs={} totalMs={}",
                        requestId, text.length(), rate,
                        first == 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(first - started),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            } catch (Cancelled expected) {
                // 用户打断，静默结束。
            } catch (Exception failure) {
                if (!cancelled.get()) listener.error("TTS_UNAVAILABLE");
            }
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        private final class Cancelled extends RuntimeException {
            Cancelled() {
                super("tts cancelled", null, false, false);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        workers.shutdown();
    }
}
