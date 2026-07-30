package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.dto.QuizSubmitDTO;
import com.knowflow.entity.QuizAnswerRecord;
import com.knowflow.vo.QuizPracticeVO;
import com.knowflow.vo.QuizStatsVO;
import com.knowflow.vo.QuizSubmitResultVO;

import java.util.List;

/** 在线答题（智能题库）业务服务接口。 */
public interface QuizService extends IService<QuizAnswerRecord> {

    /**
     * 拉取用于在线练习的题目（仅已发布题目），支持按知识库、难度、题型筛选。
     *
     * @param categoryId   知识库ID，可空
     * @param difficulty   难度 1/2/3，0 或 null 表示不限
     * @param questionType 题型，空表示不限
     * @param count        题目数量上限
     */
    List<QuizPracticeVO> listPracticeQuestions(Long categoryId, Integer difficulty, String questionType, Integer count);

    /**
     * 提交作答并自动判分：持久化答题记录，并将答错题目同步至错题本。
     */
    QuizSubmitResultVO submit(QuizSubmitDTO dto, Long userId);

    /** 用户累计答题统计。 */
    QuizStatsVO getStats(Long userId);
}
