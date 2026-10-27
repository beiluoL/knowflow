package com.knowflow.flashcards;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

/** Reference fixture from open-spaced-repetition/java-fsrs v1.0.0 FSRSTest.testReviewCard (MIT). */
class FlashcardSchedulerTest {
    @Test
    void upstreamReviewCardReference() {
        var now = Instant.parse("2022-11-29T12:30:00Z");
        String state = null;
        int[] ratings = {3, 3, 3, 3, 3, 3, 1, 1, 3, 3, 3, 3, 3};
        var intervals = new ArrayList<Long>();
        for (int rating : ratings) {
            var result =
                    FlashcardScheduler.schedule(state, rating, now, FlashcardScheduler.defaults());
            intervals.add(result.intervalSeconds() / 86400);
            state = result.cardJson();
            now = result.due();
        }
        assertEquals(
                List.of(0L, 4L, 14L, 45L, 135L, 372L, 0L, 0L, 2L, 5L, 10L, 20L, 40L), intervals);
    }

    @Test
    void fourRatingsAndSerializationAreDeterministic() {
        var now = Instant.parse("2026-10-14T10:00:00Z");
        var options = FlashcardScheduler.defaults();
        for (int r = 1; r <= 4; r++) {
            var a = FlashcardScheduler.schedule(null, r, now, options);
            var b = FlashcardScheduler.schedule(null, r, now, options);
            assertEquals(a, b);
            assertTrue(a.due().isAfter(now));
            assertTrue(a.stability() > 0);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> FlashcardScheduler.schedule(null, 0, now, options));
    }
}
