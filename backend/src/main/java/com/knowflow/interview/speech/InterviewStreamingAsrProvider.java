package com.knowflow.interview.speech;

/** Independent realtime contract; batch transcription does not implement this interface. */
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
        /** All utterance finals have arrived after session.finish. */
        default void completed() { }
        void error(String code);
    }
}
