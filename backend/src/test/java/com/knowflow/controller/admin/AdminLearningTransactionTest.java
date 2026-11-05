package com.knowflow.controller.admin;

import com.knowflow.learning.controller.admin.AdminLearningController;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.knowflow.learning.entity.LearningChapter;
import com.knowflow.learning.entity.LearningPath;
import com.knowflow.learning.mapper.LearningChapterMapper;
import com.knowflow.learning.mapper.LearningPathMapper;
import com.knowflow.ai.service.impl.AiServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 Spring 代理与 H2 写入，不包裹外层测试事务。 */
@SpringBootTest(properties={
    "knowflow.datasource.h2.url=jdbc:h2:mem:admin_learning_tx;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "knowflow.datasource.config-file=/tmp/admin-learning-tx-no-overrides.json",
    "knowflow.seed.development-account.enabled=false",
    "knowflow.datasource.allow-runtime-switch=false"
})
class AdminLearningTransactionTest {
    @Autowired AdminLearningController controller;
    @Autowired LearningPathMapper paths;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @SpyBean LearningChapterMapper chapters;
    @SpyBean AiServiceImpl ai;
    @Autowired com.knowflow.learning.service.AdminLearningWriteService writes;
    @SpyBean com.knowflow.flashcards.mapper.LearningFlashcardMapper cards;

    @Test
    void chapterFailureRollsBackGeneratedPathAndAiRunsOutsideTransaction() {
        String title = "tx-" + UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                991891L, "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return "{\"title\":\"" + title + "\",\"chapters\":[{\"title\":\"one\"},{\"title\":\"two\"}]}";
        }).when(ai).complete(anyString(), anyString(), org.mockito.ArgumentMatchers.<String>isNull(), eq(991891L));
        doAnswer(call -> {
            LearningChapter chapter = call.getArgument(0);
            if ("two".equals(chapter.getTitle())) throw new IllegalStateException("second chapter failure");
            return jdbc.update("INSERT INTO learning_chapter(path_id,title,content,sort_order,duration,doc_ids,flashcard_ids) VALUES(?,?,?,?,?,?,?)", chapter.getPathId(), chapter.getTitle(), chapter.getContent(), chapter.getSortOrder(), chapter.getDuration(), chapter.getDocIds(), chapter.getFlashcardIds());
        }).when(chapters).insert(any(LearningChapter.class));
        try {
            controller.aiGeneratePath(Map.of("topic", title));
            assertEquals(0L, paths.selectCount(new QueryWrapper<LearningPath>().eq("title", title)));
        } finally {
            SecurityContextHolder.clearContext();
            reset(chapters);
        }
    }
    @Test
    void secondCardFailureRollsBackBatchThroughServiceProxy() {
        long owner = 991892L;
        com.knowflow.flashcards.entity.LearningFlashcard first = new com.knowflow.flashcards.entity.LearningFlashcard();
        first.setUserId(owner);
        first.setFront("first");
        first.setBack("answer");
        com.knowflow.flashcards.entity.LearningFlashcard second = new com.knowflow.flashcards.entity.LearningFlashcard();
        second.setUserId(owner);
        second.setFront("second");
        second.setBack("answer");
        doAnswer(call -> {
            com.knowflow.flashcards.entity.LearningFlashcard card = call.getArgument(0);
            if ("second".equals(card.getFront())) throw new IllegalStateException("second card failure");
            return jdbc.update("INSERT INTO learning_flashcard(user_id,front,back) VALUES(?,?,?)", card.getUserId(), card.getFront(), card.getBack());
        }).when(cards).insert(any(com.knowflow.flashcards.entity.LearningFlashcard.class));
        try {
            assertThrows(IllegalStateException.class, () -> writes.createGeneratedCards(List.of(first, second)));
            assertEquals(0L, cards.selectCount(new QueryWrapper<com.knowflow.flashcards.entity.LearningFlashcard>().eq("user_id", owner)));
        } finally {
            reset(cards);
        }
    }

    @Test
    void successfulGeneratedPathCommitsChaptersAndStatistics() {
        String title = "success-" + UUID.randomUUID();
        LearningPath path = writes.createGeneratedPath(Map.of("title", title, "chapters",
                List.of(Map.of("title", "one", "duration", 15), Map.of("title", "two", "duration", 25))), title, "入门");
        assertEquals(2, paths.selectById(path.getId()).getChapterCount());
        assertEquals(40, paths.selectById(path.getId()).getTotalDuration());
        assertEquals(2L, chapters.selectCount(new QueryWrapper<LearningChapter>().eq("path_id", path.getId())));
    }

    @Test
    void nonAdminCannotStartGeneratedWrites() {
        String title = "denied-" + UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                991893L, "", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> controller.aiGeneratePath(Map.of("topic", title)));
            verifyNoInteractions(ai);
            assertEquals(0L, paths.selectCount(new QueryWrapper<LearningPath>().eq("title", title)));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

}
