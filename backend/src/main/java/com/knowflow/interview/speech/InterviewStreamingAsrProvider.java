package com.knowflow.interview.speech;

/** 独立实时协议；批量转写不实现此接口。 */
public interface InterviewStreamingAsrProvider {
    SpeechStreamCapabilities capabilities();
    Stream open(String requestId, Listener listener);
    interface Stream extends AutoCloseable {
        void audio(byte[] pcm);
        void finish();
        void cancel();
        @Override default void close() { cancel(); }
    }
    interface Listener {
        void partial(String text);
        void finalText(String text);
        void speechStarted();
        void speechEnded();
        /** session.finish 后所有语句的最终结果均已到达。 */
        default void completed() { }
        void error(String code);
    }
}
