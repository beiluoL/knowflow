package com.knowflow.flashcards;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/my/flashcard-notes")
public class FlashcardNoteController {
    private final FlashcardNoteService notes;

    @PostMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestBody FlashcardNoteService.Input input) {
        SecurityUtils.getCurrentUserId();
        return Result.success(notes.preview(input));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody FlashcardNoteService.Input input) {
        return Result.success(notes.create(SecurityUtils.getCurrentUserId(), input));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable long id) {
        return Result.success(notes.get(id, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/by-card/{id}")
    public Result<Map<String, Object>> byCard(@PathVariable long id) {
        return Result.success(notes.byCard(id, SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(
            @PathVariable long id, @RequestBody FlashcardNoteService.Input input) {
        return Result.success(notes.update(id, SecurityUtils.getCurrentUserId(), input));
    }
}
