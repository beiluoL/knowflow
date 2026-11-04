package com.knowflow.mastery.contract;

import java.time.LocalDateTime;
import java.util.List;

/** Owner-scoped mastery projection for learning-plan candidates. */
public interface MasteryReviewQuery {
    /** Returns at most twenty rows in ascending next-review order for an authenticated owner. */
    List<ReviewCandidate> listCandidates(long ownerId);

    record ReviewCandidate(Long knowledgeId, LocalDateTime nextReviewAt,
                           String learningStatus, Integer confidenceScore, boolean hasActivity) {
    }
}
