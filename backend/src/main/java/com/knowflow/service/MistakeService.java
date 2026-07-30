package com.knowflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.dto.CodeMistakeCollectRequest;
import com.knowflow.dto.CodeMistakeCollectResult;
import com.knowflow.entity.LearningMistake;
import com.knowflow.vo.MistakeVO;

/** 错题本业务服务接口。 */
public interface MistakeService extends IService<LearningMistake> {

    IPage<MistakeVO> getMistakePage(Long userId, String category, Integer mastered, Integer pageNum, Integer pageSize);

    MistakeVO getMistakeDetail(Long id, Long userId);

    void markMastered(Long id, Long userId);

    void addMistake(LearningMistake mistake, Long userId);

    /** 代码运行异常自动归集（SC1-AI-03）：提取错误类型、关联知识库、幂等保存 */
    CodeMistakeCollectResult collectCodeMistake(CodeMistakeCollectRequest req, Long userId);

    int getTotalCount(Long userId);

    int getMasteredCount(Long userId);

    int getPendingCount(Long userId);

    /** 本周新增错题数（周一至今）。 */
    int getWeeklyNewCount(Long userId);

    /** 今日待复习数（未掌握且今日尚未复习）。 */
    int getDueTodayCount(Long userId);
}
