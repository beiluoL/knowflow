package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.QuizSubmitDTO;
import com.knowflow.service.QuizService;
import com.knowflow.vo.QuizPracticeVO;
import com.knowflow.vo.QuizStatsVO;
import com.knowflow.vo.QuizSubmitResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 在线答题 REST 接口：拉取练习题、提交判分与答题统计。 */
@Tag(name = "在线答题接口")
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @Operation(summary = "拉取在线练习题目（仅已发布）")
    @GetMapping("/questions")
    public Result<List<QuizPracticeVO>> questions(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String questionType,
            @RequestParam(defaultValue = "10") Integer count) {
        return Result.success(quizService.listPracticeQuestions(categoryId, difficulty, questionType, count));
    }

    @Operation(summary = "提交作答并自动判分（答错自动同步错题本）")
    @PostMapping("/submit")
    public Result<QuizSubmitResultVO> submit(@RequestBody QuizSubmitDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(quizService.submit(dto, userId));
    }

    @Operation(summary = "我的答题统计")
    @GetMapping("/stats")
    public Result<QuizStatsVO> stats() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(quizService.getStats(userId));
    }
}
