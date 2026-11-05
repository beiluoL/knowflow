package com.knowflow.interview.speech;

/** 可取消的增量合成；结束标记携带空音频载荷。 */
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
