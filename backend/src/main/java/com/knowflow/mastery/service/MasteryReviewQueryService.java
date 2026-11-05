package com.knowflow.mastery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.mastery.entity.KnowledgeMastery;
import com.knowflow.mastery.mapper.KnowledgeMasteryMapper;
import com.knowflow.mastery.contract.MasteryReviewQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将所有者的掌握度记录投影为有界复习候选。
 */
@Service
@RequiredArgsConstructor
public class MasteryReviewQueryService implements MasteryReviewQuery {
    private final KnowledgeMasteryMapper masteryMapper;

    /** 投影所有者记录，不改变候选窗口或证据规则。 */
    @Override
    public List<ReviewCandidate> listCandidates(long ownerId) {
        return masteryMapper.selectList(new LambdaQueryWrapper<KnowledgeMastery>()
                        .eq(KnowledgeMastery::getUserId, ownerId)
                        .orderByAsc(KnowledgeMastery::getNextReviewAt)
                        .last("LIMIT 20"))
                .stream()
                .map(row -> new ReviewCandidate(row.getKnowledgeId(), row.getNextReviewAt(),
                        row.getLearningStatus(), row.getConfidenceScore(),
                        row.getLastLearnedAt() != null || row.getLastReviewedAt() != null
                                || row.getLastAssessedAt() != null))
                .toList();
    }
}
