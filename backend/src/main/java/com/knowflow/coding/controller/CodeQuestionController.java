package com.knowflow.coding.controller;

import com.knowflow.common.Result;
import com.knowflow.coding.dto.CodeSubmitDTO;
import com.knowflow.coding.entity.CodeQuestion;
import com.knowflow.coding.service.CodeQuestionService;
import com.knowflow.coding.vo.CodeSubmitRecordVO;
import com.knowflow.coding.vo.CodeSubmitResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 提供需认证的公共编程题接口。 */
@Tag(name = "代码题库")
@RestController
@RequestMapping("/api/code-questions")
@RequiredArgsConstructor
public class CodeQuestionController {
    private final CodeQuestionService questionService;

    @Operation(summary = "已发布题目列表（支持按难度/语言/关键词筛选）")
    @GetMapping
    public Result<List<CodeQuestion>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String language) {
        return questionService.list(keyword, difficulty, language);
    }

    @Operation(summary = "题目详情（仅已发布）")
    @GetMapping("/{id}")
    public Result<CodeQuestion> detail(@PathVariable Long id) {
        return questionService.detail(id);
    }

    @Operation(summary = "提交答案：记录提交与通过统计")
    @PostMapping("/{id}/submit")
    public Result<CodeSubmitResultVO> submit(@PathVariable Long id, @RequestBody CodeSubmitDTO payload) {
        return questionService.submit(id, payload);
    }

    @Operation(summary = "我的提交记录（按题目ID筛选）")
    @GetMapping("/{id}/submissions")
    public Result<List<CodeSubmitRecordVO>> submissions(@PathVariable Long id) {
        return questionService.submissions(id);
    }
}
