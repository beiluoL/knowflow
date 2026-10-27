package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.QuizSubmitDTO;
import com.knowflow.service.QuizService;
import com.knowflow.vo.QuizExplainVO;
import com.knowflow.vo.QuizMistakeVO;
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
    private final com.knowflow.learning.product.QuizLearningReviewService learningReview;

    @GetMapping("/questions/{id}/learning-review")
    public Result<java.util.Map<String,Object>> learningReview(@PathVariable long id){return Result.success(learningReview.report(SecurityUtils.getCurrentUserId(),id));}

    @GetMapping("/answers/{id}/learning-review")
    public Result<java.util.Map<String,Object>> answerReview(@PathVariable long id){long uid=SecurityUtils.getCurrentUserId();return Result.success(learningReview.report(uid,learningReview.owned(id,uid).getQuestionId()));}

    @PostMapping("/answers/{id}/flashcard")
    public Result<java.util.Map<String,Object>> reviewCard(@PathVariable long id,@RequestBody java.util.Map<String,String> input){return Result.success(learningReview.createCard(SecurityUtils.getCurrentUserId(),id,input.get("front"),input.get("back")));}


    @Operation(summary = "拉取在线练习题目（仅已发布）")
    @GetMapping("/questions")
    public Result<List<QuizPracticeVO>> questions(
            @RequestParam(required = false) Long questionId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String questionType,
            @RequestParam(defaultValue = "10") Integer count) {
        if(questionId!=null)return Result.success(List.of(quizService.practiceQuestion(questionId,SecurityUtils.getCurrentUserId())));
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

    @Operation(summary = "错题重练：获取未掌握的错题")
    @GetMapping("/mistake-practice")
    public Result<List<QuizMistakeVO>> mistakePractice(
            @RequestParam(defaultValue = "10") Integer count) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(quizService.listMistakePractice(userId, count));
    }

    @Operation(summary = "复习错题：提交复习结果并自动更新掌握状态")
    @PostMapping("/mistake-review")
    public Result<Boolean> reviewMistake(
            @RequestParam Long mistakeId,
            @RequestParam Boolean correct) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(quizService.reviewMistake(mistakeId, correct, userId));
    }

    @Operation(summary = "Phase 3H：题目讲解（AI 不可用时降级为已有答案与解析，无解析显式暂无解析）")
    @GetMapping("/explain")
    public Result<QuizExplainVO> explain(
            @RequestParam Long questionId,
            @RequestParam(required = false) String userAnswer) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(quizService.explainQuestion(questionId, userAnswer, userId));
    }

    @Operation(summary = "Phase 3H：确定性智能练习推荐（规则引擎，无 AI；到期错题>高频错题>薄弱点>目标知识点>未做新题）")
    @GetMapping("/smart-recommend")
    public Result<com.knowflow.vo.SmartPracticeVO> smartRecommend(
            @RequestParam(required = false) List<String> topics,
            @RequestParam(required = false) List<Integer> difficulties,
            @RequestParam(required = false, defaultValue = "10") Integer count,
            @RequestParam(required = false) Long seed) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(quizService.recommendPractice(userId, topics, difficulties, count, seed));
    }
}
