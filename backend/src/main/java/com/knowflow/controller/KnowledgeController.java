package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.service.KnowledgeService;
import com.knowflow.vo.KnowledgeGraphVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 知识图谱 REST 接口，返回分类/文档节点与关系边，供前端可视化。 */
@Tag(name = "知识图谱接口")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "知识图谱数据")
    @GetMapping("/graph")
    public Result<KnowledgeGraphVO> graph() {
        return Result.success(knowledgeService.getGraph());
    }
}
