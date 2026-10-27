package com.knowflow.controller.admin;

import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.TechVocabDtos;
import com.knowflow.service.TechVocabExtractionService;
import com.knowflow.service.TechVocabService;
import com.knowflow.vo.TechVocabVOs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 技术英语管理端接口：词条创建/编辑/审核与批量导入。
 * 整个类受 ADMIN 门禁；导入预览与确认绑定管理员身份，避免 token 被他人复用。
 */
@Tag(name = "管理员 - 技术英语")
@RestController
@RequestMapping("/api/admin/tech-english")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTechEnglishController {

    private final TechVocabService techVocabService;
    private final TechVocabExtractionService extractionService;

    @Operation(summary = "词条列表（含草稿，按关键词分页）")
    @GetMapping("/terms")
    public Result<PageResult<TechVocabVOs.TermSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        // 管理端检索包含草稿与归档，便于审核与编辑
        return Result.success(techVocabService.adminSearch(q, type, domain, pageNum, pageSize));
    }

    @Operation(summary = "词条详情（含草稿）")
    @GetMapping("/terms/{id}")
    public Result<TechVocabVOs.TermDetail> detail(@PathVariable Long id) {
        return Result.success(techVocabService.adminDetail(id));
    }

    @Operation(summary = "创建词条")
    @PostMapping("/terms")
    public Result<TechVocabVOs.TermDetail> create(@RequestBody TechVocabDtos.TermInput input) {
        return Result.success(techVocabService.adminCreate(input));
    }

    @Operation(summary = "编辑/审核词条")
    @PutMapping("/terms/{id}")
    public Result<TechVocabVOs.TermDetail> update(@PathVariable Long id,
                                                  @RequestBody TechVocabDtos.TermInput input) {
        return Result.success(techVocabService.adminUpdate(id, input));
    }

    @Operation(summary = "批量导入预览（不写库）")
    @PostMapping("/import-preview")
    public Result<TechVocabVOs.ImportPreviewVO> preview(@RequestBody TechVocabDtos.ImportRequest request) {
        return Result.success(techVocabService.previewImport(SecurityUtils.getCurrentUserId(), request));
    }

    @Operation(summary = "确认导入（同一 token 幂等）")
    @PostMapping("/import-confirm")
    public Result<TechVocabVOs.ImportResultVO> confirm(@RequestBody TechVocabDtos.ImportConfirmRequest request) {
        String token = request == null ? null : request.token();
        return Result.success(techVocabService.confirmImport(SecurityUtils.getCurrentUserId(), token));
    }

    /* ============================ P2 个人候选审核 ============================ */

    @Operation(summary = "候选词条列表（默认待审核）")
    @GetMapping("/candidates")
    public Result<List<TechVocabVOs.CandidateVO>> candidates(
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        return Result.success(extractionService.adminCandidates(status));
    }

    @Operation(summary = "审核候选词条（通过时建立正式词条，幂等）")
    @PostMapping("/candidates/{id}/review")
    public Result<TechVocabVOs.CandidateVO> reviewCandidate(
            @PathVariable Long id,
            @RequestBody TechVocabDtos.CandidateReviewInput input) {
        return Result.success(extractionService.reviewCandidate(id, input));
    }
}
