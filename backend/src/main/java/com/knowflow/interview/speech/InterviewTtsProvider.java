package com.knowflow.interview.speech;

/** Cancellable incremental synthesis; final marker contains an empty audio payload. */
public interface InterviewTtsProvider {
    SpeechStreamCapabilities capabilities();
    Stream synthesizeStream(String text, String requestId, Listener listener);
    interface Stream extends AutoCloseable {
        void cancel();
        @Override default void close() { cancel(); }
    }
    interface Listener {
        void audio(byte[] pcm, String format, int sampleRate, long sequence, boolean last);
        void error(String code);
    }
}
