package com.knowflow.flashcards;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning")
public class FlashcardFsrsController {
    private final FlashcardFsrsService scheduling;

    @GetMapping("/flashcards/{id}/schedule")
    public Result<Map<String, Object>> preview(@PathVariable long id) {
        return Result.success(scheduling.preview(id, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/flashcards/{id}/fsrs-review")
    public Result<Map<String, Object>> review(
            @PathVariable long id, @RequestBody FlashcardFsrsService.ReviewInput input) {
        return Result.success(scheduling.review(id, SecurityUtils.getCurrentUserId(), input));
    }

    @GetMapping("/flashcards/sessions/{id}")
    public Result<Map<String, Object>> session(@PathVariable String id) {
        return Result.success(scheduling.session(SecurityUtils.getCurrentUserId(), id));
    }

    @GetMapping("/my/flashcards/settings")
    public Result<FlashcardFsrsService.Settings> settings() {
        return Result.success(scheduling.settings(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/my/flashcards/settings")
    public Result<FlashcardFsrsService.Settings> settings(
            @RequestBody FlashcardFsrsService.Settings input) {
        return Result.success(scheduling.saveSettings(SecurityUtils.getCurrentUserId(), input));
    }

    @GetMapping("/my/flashcards/statistics")
    public Result<Map<String, Object>> stats() {
        return Result.success(scheduling.statistics(SecurityUtils.getCurrentUserId()));
    }
}
