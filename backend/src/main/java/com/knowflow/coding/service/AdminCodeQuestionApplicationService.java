package com.knowflow.coding.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.coding.entity.CodeQuestion;
import com.knowflow.coding.mapper.CodeQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import org.springframework.stereotype.Service;

/** 管理用例编排及持久化边界。 */
@Service
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminCodeQuestionApplicationService {
    private final CodeQuestionMapper questionMapper;
    private final com.knowflow.catalog.service.QuestionMutationAudit catalogAudit;
    /** 执行管理操作，保留既有校验与响应合同。 */
    public Result<List<CodeQuestion>> list(
            String keyword,
            Integer difficulty,
            String language,
            Integer status) {
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

    /** 执行管理操作，保留既有校验与响应合同。 */
    public Result<CodeQuestion> detail(Long id) {
        CodeQuestion q = questionMapper.selectById(id);
        if (q == null) {
            return Result.error(404, "题目不存在");
        }
        return Result.success(q);
    }

    /** 执行管理操作，保留既有校验与响应合同。 */
    public Result<CodeQuestion> add(CodeQuestion question) {
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

    /** 执行管理操作，保留既有校验与响应合同。 */
    public Result<Void> update(Long id, CodeQuestion question) {
        question.setId(id);
        // 保护字段：不通过 update 接口修改统计计数
        question.setPassCount(null);
        question.setSubmitCount(null);
        questionMapper.updateById(question);
        return Result.success();
    }

    /** 执行管理操作，保留既有校验与响应合同。 */
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> delete(Long id) {
        if (questionMapper.selectById(id) == null) throw new com.knowflow.exception.BusinessException(404, "题目不存在");
        questionMapper.deleteById(id);
        catalogAudit.record("LEGACY_CODE", id, "DELETE", null, "deleted=1");
        return Result.success();
    }

    /** 执行管理操作，保留既有校验与响应合同。 */
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> publish(Long id) {
        if (questionMapper.selectById(id) == null) throw new com.knowflow.exception.BusinessException(404, "题目不存在");
        CodeQuestion update = new CodeQuestion();
        update.setId(id);
        update.setStatus(1);
        catalogAudit.record("LEGACY_CODE", id, "PUBLISH", null, "1");
        questionMapper.updateById(update);
        return Result.success();
    }

    /** 执行管理操作，保留既有校验与响应合同。 */
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> unpublish(Long id) {
        if (questionMapper.selectById(id) == null) throw new com.knowflow.exception.BusinessException(404, "题目不存在");
        CodeQuestion update = new CodeQuestion();
        update.setId(id);
        update.setStatus(0);
        catalogAudit.record("LEGACY_CODE", id, "UNPUBLISH", null, "0");
        questionMapper.updateById(update);
        return Result.success();
    }
}
