package com.knowflow.flashcards;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;

import java.time.*;
import java.util.Arrays;

/** Thin adapter to java-fsrs 1.0.0 (MIT), FSRS-6. No local memory formulas. */
public final class FlashcardScheduler {
    private FlashcardScheduler() {}

    public static final String VERSION = "FSRS-6/java-fsrs-1.0.0";

    public record Options(
            double desiredRetention,
            int maximumInterval,
            int[] learningMinutes,
            int[] relearningMinutes) {}

    public record Result(
            String cardJson,
            String schedulerJson,
            String reviewLogJson,
            Instant due,
            String state,
            double stability,
            double difficulty,
            long intervalSeconds) {}

    public static Options defaults() {
        return new Options(.9, 36500, new int[] {1, 10}, new int[] {10});
    }

    public static void validate(Options options) {
        if (options == null
                || !Double.isFinite(options.desiredRetention())
                || options.desiredRetention() < .7
                || options.desiredRetention() > .99
                || options.maximumInterval() < 1
                || options.maximumInterval() > 36500)
            throw new IllegalArgumentException("保持率需为0.70–0.99，最大间隔需为1–36500天");
        validateSteps(options.learningMinutes());
        validateSteps(options.relearningMinutes());
    }

    private static void validateSteps(int[] steps) {
        if (steps == null || steps.length > 6) throw new IllegalArgumentException("最多6个学习步长");
        int previous = 0;
        for (int step : steps) {
            if (step <= previous || step > 1440)
                throw new IllegalArgumentException("学习步长需递增且为1–1440分钟");
            previous = step;
        }
    }

    public static Result schedule(String previous, int rating, Instant now, Options options) {
        validate(options);
        if (rating < 1 || rating > 4 || now == null) throw new IllegalArgumentException("评分需为1–4");
        var scheduler =
                Scheduler.builder()
                        .desiredRetention(options.desiredRetention())
                        .maximumInterval(options.maximumInterval())
                        .learningSteps(
                                Arrays.stream(options.learningMinutes())
                                        .mapToObj(Duration::ofMinutes)
                                        .toArray(Duration[]::new))
                        .relearningSteps(
                                Arrays.stream(options.relearningMinutes())
                                        .mapToObj(Duration::ofMinutes)
                                        .toArray(Duration[]::new))
                        .enableFuzzing(false)
                        .build();
        // FSRS's internal int cardId is an opaque placeholder; the original database BIGINT id
        // remains authoritative.
        var card =
                previous == null
                        ? Card.builder().cardId(0).due(now).build()
                        : Card.fromJson(previous);
        var result = scheduler.reviewCard(card, Rating.values()[rating - 1], now);
        var next = result.card();
        return new Result(
                next.toJson(),
                scheduler.toJson(),
                result.reviewLog().toJson(),
                next.getDue(),
                next.getState().name(),
                next.getStability(),
                next.getDifficulty(),
                Duration.between(now, next.getDue()).getSeconds());
    }
}
