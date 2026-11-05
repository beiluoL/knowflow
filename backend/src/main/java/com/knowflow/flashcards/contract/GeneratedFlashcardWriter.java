package com.knowflow.flashcards.contract;

import com.knowflow.flashcards.entity.LearningFlashcard;
import com.knowflow.flashcards.mapper.LearningFlashcardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 将已通过校验的学习内容草稿保存为新闪卡。 */
@Service
@RequiredArgsConstructor
public class GeneratedFlashcardWriter {

    private final LearningFlashcardMapper cards;

    public record Draft(Long pathId, Long chapterId, Long categoryId, Long docId,
                        String front, String back, int difficulty) {}

    /**
     * 为已校验的用户保存生成卡片，返回数据库 ID。
     * 写入加入内容接纳事务，使后续场景或笔记校验失败时卡片一并回滚。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long saveAcceptedDraft(Long userId, Draft draft) {
        LearningFlashcard card = new LearningFlashcard();
        card.setUserId(userId);
        card.setPathId(draft.pathId());
        card.setChapterId(draft.chapterId());
        card.setCategoryId(draft.categoryId());
        card.setDocId(draft.docId());
        card.setFront(draft.front());
        card.setBack(draft.back());
        card.setDifficulty(draft.difficulty());
        card.setSourceType("AI");
        card.setReviewCount(0);
        card.setReviewInterval(0);
        cards.insert(card);
        return card.getId();
    }
}
