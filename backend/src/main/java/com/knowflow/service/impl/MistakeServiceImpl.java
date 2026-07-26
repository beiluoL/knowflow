package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.entity.LearningMistake;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.service.MistakeService;
import com.knowflow.vo.MistakeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 错题本业务服务实现。 */
@Service
@RequiredArgsConstructor
public class MistakeServiceImpl extends ServiceImpl<LearningMistakeMapper, LearningMistake> implements MistakeService {

    @Override
    public IPage<MistakeVO> getMistakePage(Long userId, String category, Integer mastered, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<LearningMistake> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(LearningMistake::getUserId, userId);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(LearningMistake::getCategory, category);
        }
        if (mastered != null) {
            wrapper.eq(LearningMistake::getMastered, mastered);
        }
        wrapper.orderByDesc(LearningMistake::getCreateTime);

        Page<LearningMistake> page = this.page(new Page<>(com.knowflow.common.PageQuery.normalizePageNum(pageNum), com.knowflow.common.PageQuery.normalizePageSize(pageSize)), wrapper);
        return page.convert(m -> BeanUtil.copyProperties(m, MistakeVO.class));
    }

    @Override
    public MistakeVO getMistakeDetail(Long id, Long userId) {
        LearningMistake mistake = this.getById(id);
        if (mistake == null) {
            throw new BusinessException(404, "错题不存在");
        }
        if (!java.util.Objects.equals(mistake.getUserId(), userId)) {
            throw new BusinessException("无权访问该错题");
        }
        return BeanUtil.copyProperties(mistake, MistakeVO.class);
    }

    @Override
    public void markMastered(Long id, Long userId) {
        LearningMistake mistake = this.getById(id);
        if (mistake == null) {
            throw new BusinessException(404, "错题不存在");
        }
        if (!java.util.Objects.equals(mistake.getUserId(), userId)) {
            throw new BusinessException("无权操作该错题");
        }
        mistake.setMastered(1);
        mistake.setLastReviewTime(LocalDateTime.now());
        this.updateById(mistake);
    }

    @Override
    public void addMistake(LearningMistake mistake, Long userId) {
        // 幂等：同用户 + 同题目（去空白）已存在则更新答案与复习次数，避免反复刷题刷出重复错题
        String question = mistake.getQuestion() == null ? "" : mistake.getQuestion().trim();
        if (!question.isEmpty()) {
            LearningMistake exist = this.getOne(new LambdaQueryWrapper<LearningMistake>()
                    .eq(LearningMistake::getUserId, userId)
                    .eq(LearningMistake::getQuestion, question)
                    .last("LIMIT 1"));
            if (exist != null) {
                exist.setWrongAnswer(mistake.getWrongAnswer());
                exist.setCorrectAnswer(mistake.getCorrectAnswer());
                exist.setCategory(mistake.getCategory());
                exist.setDifficulty(mistake.getDifficulty());
                exist.setSource(mistake.getSource());
                exist.setReviewCount((exist.getReviewCount() == null ? 0 : exist.getReviewCount()) + 1);
                // 再次答错则重置为未掌握，重新进入待复习队列
                exist.setMastered(0);
                exist.setLastReviewTime(LocalDateTime.now());
                this.updateById(exist);
                return;
            }
        }
        mistake.setUserId(userId);
        mistake.setMastered(0);
        mistake.setReviewCount(0);
        mistake.setLastReviewTime(LocalDateTime.now());
        this.save(mistake);
    }

    @Override
    public int getWeeklyNewCount(Long userId) {
        // 本周一 00:00 起至今的错题数
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .ge(LearningMistake::getCreateTime, weekStart)));
    }

    @Override
    public int getDueTodayCount(Long userId) {
        // 待复习：未掌握且（从未复习 或 今天之前已复习过）
        LocalDate today = LocalDate.now();
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .eq(LearningMistake::getMastered, 0)
                .and(w -> w.isNull(LearningMistake::getLastReviewTime)
                        .or().lt(LearningMistake::getLastReviewTime, today.atStartOfDay()))));
    }

    @Override
    public int getTotalCount(Long userId) {
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)));
    }

    @Override
    public int getMasteredCount(Long userId) {
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .eq(LearningMistake::getMastered, 1)));
    }

    @Override
    public int getPendingCount(Long userId) {
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .eq(LearningMistake::getMastered, 0)));
    }
}
