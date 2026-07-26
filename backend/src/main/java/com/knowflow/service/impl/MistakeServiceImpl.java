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

import java.time.LocalDateTime;

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
            throw new BusinessException("错题不存在");
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
            throw new BusinessException("错题不存在");
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
        mistake.setUserId(userId);
        mistake.setMastered(0);
        mistake.setReviewCount(0);
        this.save(mistake);
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
