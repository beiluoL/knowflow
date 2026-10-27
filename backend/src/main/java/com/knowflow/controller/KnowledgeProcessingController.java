package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.KnowledgeProcessAcceptRequestDTO;
import com.knowflow.dto.KnowledgeProcessRequestDTO;
import com.knowflow.service.KnowledgeProcessingService;
import com.knowflow.vo.KnowledgeAcceptResultVO;
import com.knowflow.vo.KnowledgeProcessResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture-4.3：Knowledge Processing 2.0 REST 接口（最小 API）。
 *
 * <p>为什么现有 API 无法表达（§63）：{@code /api/knowledge/extract}（A-RAG-04）是「LLM → 直接写库」，
 * 没有候选/审核/接受生命周期，也没有学习节点关联；{@code /api/docs/{id}/ai-summary} 只产摘要不产知识结构。
 * 本模块的 process/accept 是「候选 → 用户审核 → 落库」的两段式，语义与现有接口均不同，因此新增这两个端点
 * （仍是整个模块唯一新增的 HTTP API；其余全部复用既有端点与模型）。</p>
 */
@Tag(name = "知识处理（Architecture-4.3）")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeProcessingController {

    private final KnowledgeProcessingService knowledgeProcessingService;

    @Operation(summary = "处理资料：AI 抽取 → 校验 → 返回候选（零写库）")
    @PostMapping("/process")
    public Result<KnowledgeProcessResultVO> process(@RequestBody KnowledgeProcessRequestDTO request) {
        return Result.success(knowledgeProcessingService.process(request, com.knowflow.common.SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "接受候选：确定性校验后写入 Knowledge Core，可选关联学习节点（幂等）")
    @PostMapping("/process/accept")
    public Result<KnowledgeAcceptResultVO> accept(@RequestBody KnowledgeProcessAcceptRequestDTO request) {
        return Result.success(knowledgeProcessingService.accept(request, com.knowflow.common.SecurityUtils.getCurrentUserId()));
    }
}
