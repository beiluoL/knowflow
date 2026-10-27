package com.knowflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.exam.service.ExamService;
import com.knowflow.exam.vo.ExamVos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Java + AI 智能考试系统接口。
 * 所有接口需要登录；用户身份取自认证上下文，试卷与尝试按用户隔离。
 */
@Tag(name = "智能考试系统")
@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final com.knowflow.exam.diagnosis.ExamDiagnosisService examDiagnosis;

    // ---------- 考试中心 ----------

    @Operation(summary = "考试中心首页（真实数据）")
    @GetMapping("/dashboard")
    public Result<ExamVos.DashboardVO> dashboard() {
        return Result.success(examService.dashboard(SecurityUtils.getCurrentUserId()));
    }

    // ---------- 模板与组卷 ----------

    @Operation(summary = "默认试卷模板列表（含题库可用性）")
    @GetMapping("/templates")
    public Result<List<ExamVos.TemplateVO>> templates() {
        return Result.success(examService.listTemplates(true, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "按模板生成试卷（保存题目快照）")
    @PostMapping("/templates/{key}/papers")
    public Result<ExamVos.PaperVO> createFromTemplate(@PathVariable String key,
                                                       @RequestBody(required = false) ExamRequests.TemplateInstantiateRequest req) {
        return Result.success(examService.createFromTemplate(key, req, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "题库可用性预览（按题型/知识点/难度）")
    @PostMapping("/bank/availability")
    public Result<ExamVos.BankAvailabilityVO> availability(@RequestBody ExamRequests.ComposeRequest req) {
        return Result.success(examService.availability(req, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "确定性规则智能组卷")
    @PostMapping("/papers/compose")
    public Result<ExamVos.PaperVO> compose(@RequestBody ExamRequests.ComposeRequest req) {
        return Result.success(examService.compose(req, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "AI 智能出卷（个人临时卷，待人工审核，不进共享题库）")
    @PostMapping("/papers/ai")
    public Result<ExamVos.PaperVO> composeAi(@RequestBody ExamRequests.AiComposeRequest req) {
        return Result.success(examService.composeAi(req, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "试卷列表（公共模板卷 + 我的 AI 卷）")
    @GetMapping("/papers")
    public Result<List<ExamVos.PaperVO>> papers(@RequestParam(defaultValue = "false") boolean mineOnly) {
        return Result.success(examService.listPapers(mineOnly, SecurityUtils.getCurrentUserId()));
    }

    // ---------- 作答 ----------

    @Operation(summary = "开始/继续考试（服务端生成可信截止时间）")
    @PostMapping("/papers/{paperId}/attempts")
    public Result<ExamVos.AttemptVO> start(@PathVariable Long paperId) {
        return Result.success(examService.startAttempt(paperId, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "获取进行中考试（含草稿，不含答案）")
    @GetMapping("/attempts/{attemptId}")
    public Result<ExamVos.AttemptVO> attempt(@PathVariable Long attemptId) {
        return Result.success(examService.getAttempt(attemptId, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "自动保存草稿")
    @PostMapping("/attempts/{attemptId}/draft")
    public Result<ExamVos.AttemptVO> saveDraft(@PathVariable Long attemptId,
                                               @RequestBody ExamRequests.SaveDraftRequest req) {
        return Result.success(examService.saveDraft(attemptId, req, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "交卷（规则判分客观题，主观题待评，幂等）")
    @PostMapping("/attempts/{attemptId}/submit")
    public Result<ExamVos.ResultVO> submit(@PathVariable Long attemptId,
                                           @RequestBody(required = false) ExamRequests.SubmitRequest req) {
        return Result.success(examService.submit(attemptId, req, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "成绩、解析与知识点分析")
    @GetMapping("/attempts/{attemptId}/diagnosis")
    public Result<com.knowflow.exam.diagnosis.ExamDiagnosis> diagnosis(@PathVariable Long attemptId) {
        return Result.success(examDiagnosis.diagnose(attemptId,SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/attempts/{attemptId}/result")
    public Result<ExamVos.ResultVO> result(@PathVariable Long attemptId) {
        return Result.success(examService.getResult(attemptId, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "我的考试记录")
    @GetMapping("/attempts")
    public Result<List<ExamVos.AttemptListItemVO>> attempts() {
        return Result.success(examService.listMyAttempts(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "对主观题发起 AI 五维辅助评卷")
    @PostMapping("/attempts/{attemptId}/ai-grade")
    public Result<ExamVos.AiGradeResultVO> aiGrade(@PathVariable Long attemptId) {
        return Result.success(examService.runAiGrading(attemptId, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "重试代码题判题（交卷后 CODE 题待判时调用，幂等；平台不可用则继续待判不计 0）")
    @PostMapping("/attempts/{attemptId}/rejudge-code")
    public Result<ExamVos.ResultVO> rejudgeCode(@PathVariable Long attemptId) {
        return Result.success(examService.rejudgeCodeAnswers(attemptId, SecurityUtils.getCurrentUserId()));
    }

    // ---------- 考试错题 ----------

    @Operation(summary = "考试错题本（支持知识点/掌握状态/时间筛选与分页）")
    @GetMapping("/mistakes")
    public Result<IPage<ExamVos.ExamMistakeVO>> mistakes(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) Integer mastered,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(examService.examMistakes(topic, mastered, from, to, pageNum, pageSize,
                SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "标记考试错题为已掌握")
    @PostMapping("/mistakes/{mistakeId}/master")
    public Result<Boolean> masterMistake(@PathVariable Long mistakeId) {
        examService.markMistakeMastered(mistakeId, SecurityUtils.getCurrentUserId());
        return Result.success(true);
    }
}
