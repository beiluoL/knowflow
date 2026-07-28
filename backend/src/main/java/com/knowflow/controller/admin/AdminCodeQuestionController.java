package com.knowflow.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.entity.CodeQuestion;
import com.knowflow.mapper.CodeQuestionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员-代码题库管理：提供题目的增删改查与发布/下架能力。
 * 前端后台「题库管理」页面对接此 Controller。
 */
@Slf4j
@Tag(name = "管理员代码题库管理")
@RestController
@RequestMapping("/api/admin/code-questions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCodeQuestionController {

    private final CodeQuestionMapper questionMapper;

    @Operation(summary = "题目列表（支持按标题/难度/语言/状态筛选）")
    @GetMapping
    public Result<List<CodeQuestion>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<CodeQuestion> wrapper = new LambdaQueryWrapper<CodeQuestion>()
                .orderByAsc(CodeQuestion::getSortOrder)
                .orderByDesc(CodeQuestion::getCreateTime);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(CodeQuestion::getTitle, keyword.trim())
                    .or().like(CodeQuestion::getTags, keyword.trim()));
        }
        if (difficulty != null) {
            wrapper.eq(CodeQuestion::getDifficulty, difficulty);
        }
        if (language != null && !language.trim().isEmpty()) {
            wrapper.eq(CodeQuestion::getLanguage, language.trim());
        }
        if (status != null) {
            wrapper.eq(CodeQuestion::getStatus, status);
        }
        return Result.success(questionMapper.selectList(wrapper));
    }

    @Operation(summary = "题目详情")
    @GetMapping("/{id}")
    public Result<CodeQuestion> detail(@PathVariable Long id) {
        CodeQuestion q = questionMapper.selectById(id);
        if (q == null) {
            return Result.error(404, "题目不存在");
        }
        return Result.success(q);
    }

    @Operation(summary = "新增题目")
    @PostMapping
    public Result<CodeQuestion> add(@RequestBody CodeQuestion question) {
        // 设置默认值
        if (question.getStatus() == null) {
            question.setStatus(1);
        }
        if (question.getSortOrder() == null) {
            question.setSortOrder(0);
        }
        if (question.getPassCount() == null) {
            question.setPassCount(0);
        }
        if (question.getSubmitCount() == null) {
            question.setSubmitCount(0);
        }
        questionMapper.insert(question);
        return Result.success(question);
    }

    @Operation(summary = "更新题目")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CodeQuestion question) {
        question.setId(id);
        // 保护字段：不通过 update 接口修改统计计数
        question.setPassCount(null);
        question.setSubmitCount(null);
        questionMapper.updateById(question);
        return Result.success();
    }

    @Operation(summary = "删除题目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionMapper.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "发布题目")
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        CodeQuestion update = new CodeQuestion();
        update.setId(id);
        update.setStatus(1);
        questionMapper.updateById(update);
        return Result.success();
    }

    @Operation(summary = "下架题目（转为草稿）")
    @PutMapping("/{id}/unpublish")
    public Result<Void> unpublish(@PathVariable Long id) {
        CodeQuestion update = new CodeQuestion();
        update.setId(id);
        update.setStatus(0);
        questionMapper.updateById(update);
        return Result.success();
    }
}
