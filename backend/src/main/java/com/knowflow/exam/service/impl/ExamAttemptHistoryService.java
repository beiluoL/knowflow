package com.knowflow.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.exam.entity.ExamAttempt;
import com.knowflow.exam.entity.ExamPaper;
import com.knowflow.exam.mapper.ExamAttemptMapper;
import com.knowflow.exam.mapper.ExamPaperMapper;
import com.knowflow.exam.vo.ExamVos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Queries a user's exam history and projects attempt summaries for exam read views. */
@Service
@RequiredArgsConstructor
class ExamAttemptHistoryService {
    private final ExamAttemptMapper attemptMapper;
    private final ExamPaperMapper paperMapper;

    /** Lists the authenticated actor's attempts in reverse creation order. */
    public List<ExamVos.AttemptListItemVO> listMyAttempts(Long userId) {
        return attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                        .eq(ExamAttempt::getUserId, userId)
                        .orderByDesc(ExamAttempt::getCreateTime)).stream()
                .map(this::toListItem)
                .toList();
    }

    ExamVos.AttemptListItemVO toListItem(ExamAttempt a) {
        ExamVos.AttemptListItemVO vo = new ExamVos.AttemptListItemVO();
        vo.setAttemptId(a.getId());
        vo.setPaperId(a.getPaperId());
        ExamPaper paper = paperMapper.selectById(a.getPaperId());
        vo.setPaperName(paper == null ? "未知试卷" : paper.getName());
        vo.setMode(a.getMode());
        vo.setStatus(a.getStatus());
        vo.setGradingStatus(a.getGradingStatus());
        vo.setTotalScore(a.getTotalScore());
        vo.setMaxScore(a.getMaxScore());
        vo.setPaperMaxScore(a.getPaperMaxScore());
        vo.setCorrectCount(a.getCorrectCount());
        vo.setGradedCount(a.getGradedCount());
        vo.setQuestionCount(a.getQuestionCount());
        vo.setStartTime(a.getStartTime());
        vo.setSubmitTime(a.getSubmitTime());
        vo.setDurationSeconds(a.getDurationSeconds());
        vo.setPassScore(paper == null ? 60 : paper.getPassScore());
        vo.setPassed(ExamAttempt.STATUS_GRADED.equals(a.getStatus()) && a.getTotalScore() != null
                && paper != null && a.getTotalScore() >= paper.getPassScore());
        return vo;
    }
}
