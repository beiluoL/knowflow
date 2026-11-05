package com.knowflow.mastery.contract;

import java.time.LocalDateTime;
import java.util.List;

/** 为学习计划候选提供按所有者隔离的掌握度投影。 */
public interface MasteryReviewQuery {
    /** 为认证所有者按下次复习时间升序返回至多二十条记录。 */
    List<ReviewCandidate> listCandidates(long ownerId);

    record ReviewCandidate(Long knowledgeId, LocalDateTime nextReviewAt,
                           String learningStatus, Integer confidenceScore, boolean hasActivity) {
    }
}
