package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.service.KnowledgeService;
import com.knowflow.vo.ConceptDiagramVO;
import com.knowflow.vo.EntityGraphVO;
import com.knowflow.vo.ExtractResultVO;
import com.knowflow.vo.KnowledgeGraphVO;
import com.knowflow.vo.TechGraphVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 知识图谱 REST 接口：分类-文档图谱、技术栈依赖图谱、概念可视化图解、实体关系知识图谱（A-RAG-04）。 */
@Tag(name = "知识图谱接口")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "分类-文档层级图谱")
    @GetMapping("/graph")
    public Result<KnowledgeGraphVO> graph() {
        return Result.success(knowledgeService.getGraph());
    }

    @Operation(summary = "技术栈依赖图谱（AI 生成）")
    @GetMapping("/tech-graph")
    public Result<TechGraphVO> techGraph(
            @RequestParam String topic,
            @RequestParam(required = false) Long categoryId) {
        return Result.success(knowledgeService.getTechGraph(topic, categoryId));
    }

    @Operation(summary = "概念可视化图解（优先读缓存，未命中则 AI 生成并持久化）")
    @GetMapping("/concept-diagram")
    public Result<ConceptDiagramVO> conceptDiagram(@RequestParam String concept,
                                                    Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return Result.success(knowledgeService.getConceptDiagram(userId, concept));
    }

    @Operation(summary = "重新生成概念图解（删除旧缓存，AI 重新生成并持久化）")
    @PostMapping("/concept-diagram/regenerate")
    public Result<ConceptDiagramVO> regenerateConceptDiagram(@RequestParam String concept,
                                                              Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return Result.success(knowledgeService.regenerateConceptDiagram(userId, concept));
    }

    @Operation(summary = "实体关系知识图谱（A-RAG-04：AI 抽取实体+关系构建的真正知识图谱）")
    @GetMapping("/entity-graph")
    public Result<EntityGraphVO> getEntityGraph(@RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) Long docId) {
        return Result.success(knowledgeService.getEntityGraph(categoryId, docId));
    }

    @Operation(summary = "批量抽取并构建知识图谱（按分类或全部已发布文档）")
    @PostMapping("/extract")
    public Result<ExtractResultVO> extractByCategory(@RequestParam(required = false) Long categoryId) {
        return Result.success(knowledgeService.extractByCategory(categoryId));
    }

    @Operation(summary = "抽取单篇文档的实体与关系")
    @PostMapping("/extract/{docId}")
    public Result<ExtractResultVO> extractDoc(@PathVariable Long docId) {
        return Result.success(knowledgeService.extractDoc(docId));
    }
}
