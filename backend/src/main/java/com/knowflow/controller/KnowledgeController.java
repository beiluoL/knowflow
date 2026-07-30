package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.service.KnowledgeService;
import com.knowflow.vo.ConceptDiagramVO;
import com.knowflow.vo.KnowledgeGraphVO;
import com.knowflow.vo.TechGraphVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 知识图谱 REST 接口：分类-文档图谱、技术栈依赖图谱、概念可视化图解。 */
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
}
