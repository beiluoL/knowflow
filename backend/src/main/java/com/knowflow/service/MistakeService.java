package com.knowflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.LearningMistake;
import com.knowflow.vo.MistakeVO;

public interface MistakeService extends IService<LearningMistake> {

    IPage<MistakeVO> getMistakePage(Long userId, String category, Integer mastered, Integer pageNum, Integer pageSize);

    MistakeVO getMistakeDetail(Long id, Long userId);

    void markMastered(Long id, Long userId);

    void addMistake(LearningMistake mistake, Long userId);

    int getTotalCount(Long userId);

    int getMasteredCount(Long userId);

    int getPendingCount(Long userId);
}
