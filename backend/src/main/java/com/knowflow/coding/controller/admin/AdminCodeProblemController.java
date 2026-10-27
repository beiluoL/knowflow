package com.knowflow.coding.controller.admin;

import com.knowflow.coding.service.CodeProblemService;
import com.knowflow.coding.vo.CodingVos;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端：算法题（CODE）创建/编辑/用例管理/审核/发布。ADMIN 专属。 */
@Tag(name = "管理端-算法题库")
@RestController
@RequestMapping("/api/admin/code-problems")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCodeProblemController {

    private final CodeProblemService codeProblemService;
    private final com.knowflow.catalog.QuestionMutationAudit catalogAudit;

    @Operation(summary = "算法题分页（含草稿/待审核/下架）")
    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String topicCode,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) Integer status) {
        return Result.success(codeProblemService.adminPage(keyword, topicCode, difficulty,
                reviewStatus, status, page, pageSize));
    }

    @Operation(summary = "算法题完整详情（含隐藏用例与参考解答）")
    @GetMapping("/{id}")
    public Result<CodingVos.AdminProblemDetail> detail(@PathVariable Long id) {
        return Result.success(codeProblemService.adminDetail(id));
    }

    @Operation(summary = "新建或整体更新算法题（题目+扩展+用例）")
    @PostMapping("/save")
    @org.springframework.transaction.annotation.Transactional
    public Result<Map<String, Object>> save(@RequestBody CodingVos.SaveProblemRequest req) {
        Long id = codeProblemService.saveProblem(req, SecurityUtils.getCurrentUserId());
        catalogAudit.record("CODE_PROBLEM", id, req.getStatus()!=null&&req.getStatus()==1?"SAVE_AND_PUBLISH":"SAVE", null, "saved");
        return Result.success(Map.of("id", id));
    }

    @Operation(summary = "人工审核：APPROVED（同时发布）/ REJECTED")
    @PostMapping("/{id}/review")
    @org.springframework.transaction.annotation.Transactional
    public Result<?> review(@PathVariable Long id, @RequestBody CodingVos.ReviewRequest req) {
        codeProblemService.review(id, req.getDecision(), req.getComment());
        catalogAudit.record("CODE_PROBLEM", id, "REVIEW", null, req.getDecision()+":"+(req.getComment()==null?"":req.getComment().substring(0,Math.min(req.getComment().length(),400))));
        return Result.success(Map.of("reviewed", true));
    }

    @Operation(summary = "发布/下架（未审核通过禁止发布）")
    @PostMapping("/{id}/publish")
    @org.springframework.transaction.annotation.Transactional
    public Result<?> publish(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean published) {
        codeProblemService.publish(id, published);
        catalogAudit.record("CODE_PROBLEM", id, published ? "PUBLISH" : "UNPUBLISH", null, String.valueOf(published));
        return Result.success(Map.of("published", published));
    }
}
