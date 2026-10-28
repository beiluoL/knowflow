package com.knowflow.coding.controller;

import com.knowflow.coding.service.CodeProblemService;
import com.knowflow.coding.vo.CodingVos;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统一题库中心 · 算法题（CODE）用户侧接口。隐藏用例/参考解答不下发。 */
@Tag(name = "算法题库（用户）")
@RestController
@RequestMapping("/api/code-bank")
@RequiredArgsConstructor
public class CodeBankController {

    private final CodeProblemService codeProblemService;
    @org.springframework.beans.factory.annotation.Value("${knowflow.product.java-practice.enabled:false}")
    private boolean javaPracticeEnabled;

    @Operation(summary = "知识点分类树")
    @GetMapping("/topics")
    public Result<?> topics() {
        return Result.success(codeProblemService.listTopics());
    }

    @Operation(summary = "分页检索已发布算法题（服务端过滤+分页）")
    @GetMapping("/problems")
    public Result<PageResult<CodingVos.ProblemListItem>> problems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String topicCode,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(codeProblemService.pagePublished(keyword, topicCode, difficulty,
                page, pageSize, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "算法题详情（仅公开用例；题解仅本人通过后返回）")
    @GetMapping("/problems/{id}")
    public Result<CodingVos.ProblemDetail> detail(@PathVariable Long id) {
        return Result.success(codeProblemService.detailForUser(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "保存/更新代码草稿（与正式提交分离）")
    @PostMapping("/problems/{id}/draft")
    public Result<?> saveDraft(@PathVariable Long id, @RequestBody CodingVos.DraftRequest req) {
        // 新工作台只允许带修订号的保存，旧入口不得绕过CAS或制造无历史写入。
        if (javaPracticeEnabled) throw new com.knowflow.exception.BusinessException(409,"请使用Java工作台的版本化保存接口");
        codeProblemService.saveDraft(SecurityUtils.getCurrentUserId(), id, req);
        return Result.success(java.util.Map.of("saved", true));
    }

    @Operation(summary = "运行/提交判题；判题服务未就绪时返回 JUDGE_UNAVAILABLE，不计分不记错")
    @PostMapping("/problems/{id}/submissions")
    public Result<CodingVos.SubmitResult> submit(@PathVariable Long id, @RequestBody CodingVos.SubmitRequest req) {
        return Result.success(codeProblemService.submitPractice(SecurityUtils.getCurrentUserId(), id, req));
    }

    @Operation(summary = "我的提交历史")
    @GetMapping("/submissions")
    public Result<PageResult<CodingVos.SubmissionVO>> mySubmissions(
            @RequestParam(required = false) Long questionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(codeProblemService.mySubmissions(SecurityUtils.getCurrentUserId(),
                questionId, page, pageSize));
    }

    @Operation(summary = "单条提交详情（仅本人）")
    @GetMapping("/submissions/{id}")
    public Result<CodingVos.SubmissionVO> submission(@PathVariable Long id) {
        return Result.success(codeProblemService.mySubmission(SecurityUtils.getCurrentUserId(), id));
    }

    @Operation(summary = "Phase 3H：对比本题两次正式提交（确定性行级 diff，无 AI）")
    @GetMapping("/problems/{id}/submission-diff")
    public Result<CodingVos.SubmissionDiffVO> submissionDiff(
            @PathVariable Long id,
            @RequestParam Long fromId,
            @RequestParam Long toId) {
        return Result.success(codeProblemService.diffSubmissions(
                SecurityUtils.getCurrentUserId(), id, fromId, toId));
    }

    @Operation(summary = "Phase 3H：代码 AI 辅助（点评/解释/分级提示）；AI 不可用时返回预置降级内容")
    @PostMapping("/problems/{id}/ai-assist")
    public Result<CodingVos.AiAssistVO> aiAssist(@PathVariable Long id,
                                                 @RequestBody CodingVos.AiAssistRequest req) {
        return Result.success(codeProblemService.aiAssist(SecurityUtils.getCurrentUserId(), id, req));
    }
}
