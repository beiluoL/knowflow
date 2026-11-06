package com.knowflow.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.learning.entity.QuizQuestion;
import com.knowflow.learning.mapper.QuizQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** 管理用例编排及持久化边界。 */
@Service
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuizQuestionApplicationService {
    private final QuizQuestionMapper quizQuestionMapper;
    /** 执行管理操作，保留既有校验与响应合同。 */
    public Result<PageResult<QuizQuestion>> list(
            Integer page,
            Integer pageSize,
            String keyword,
            String questionType,
            Integer difficulty,
            String source,
            Integer status,
            Long categoryId) {

        QueryWrapper<QuizQuestion> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("title", keyword).or().like("content", keyword));
        }
        if (questionType != null && !questionType.isBlank()) {
            qw.eq("question_type", questionType);
        }
        if (difficulty != null) {
            qw.eq("difficulty", difficulty);
        }
        if (source != null && !source.isBlank()) {
            qw.eq("source", source);
        }
        if (status != null) {
            qw.eq("status", status);
        }
        if (categoryId != null) {
            qw.eq("category_id", categoryId);
        }
        qw.ne("question_type", QuizQuestion.TYPE_CODE);
        qw.orderByDesc("create_time").orderByDesc("id");

        Page<QuizQuestion> p = quizQuestionMapper.selectPage(
                new Page<>(page, pageSize), qw);

        return Result.success(PageResult.of(p));
    }

    /** 执行管理操作，保留既有校验与响应合同。 */
    public Result<QuizQuestion> detail(Long id) {
        QuizQuestion q = quizQuestionMapper.selectById(id);
        if (q == null) {
            return Result.error("题目不存在");
        }
        return Result.success(q);
    }
}
