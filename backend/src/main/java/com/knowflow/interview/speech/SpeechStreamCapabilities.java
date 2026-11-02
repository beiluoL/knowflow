package com.knowflow.interview.speech;

/** Configuration readiness only; never a paid health probe. PCM is signed little-endian mono. */
public record SpeechStreamCapabilities(boolean available, String provider, String format, int sampleRate) { }
