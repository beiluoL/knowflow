package com.knowflow.mastery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.KnowledgeMastery;
import com.knowflow.mapper.KnowledgeMasteryMapper;
import com.knowflow.mastery.contract.MasteryReviewQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MasteryReviewQueryService implements MasteryReviewQuery {
    private final KnowledgeMasteryMapper masteryMapper;

    /** Projects the owner's records without changing the candidate window or evidence rules. */
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
