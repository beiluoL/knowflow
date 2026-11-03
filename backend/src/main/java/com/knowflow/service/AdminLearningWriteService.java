package com.knowflow.service;

import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

/** Persists generated learning content atomically after external AI work completes. */
@Service
@RequiredArgsConstructor
public class AdminLearningWriteService {
    private final LearningPathMapper pathMapper;
    private final LearningChapterMapper chapterMapper;
    private final LearningFlashcardMapper flashcardMapper;

    /** Creates a draft path, all chapters and statistics; any write failure rolls back the entire draft. */
    @Transactional(rollbackFor = Exception.class)
    public LearningPath createGeneratedPath(Map<String, Object> generated, String topic, String level) {
        // 创建学习路径
        LearningPath path = new LearningPath();
        path.setTitle((String) generated.getOrDefault("title", topic + "学习路径"));
        path.setDescription((String) generated.getOrDefault("description", ""));
        path.setLevel((String) generated.getOrDefault("level", level));
        path.setChapterCount(0);
        path.setTotalDuration(0);
        path.setEnrolledCount(0);
        path.setSortOrder(0);
        path.setStatus(0); // 默认草稿状态
        requireWrite(pathMapper.insert(path));

        // 创建章节
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) generated.get("chapters");
        if (chapters != null) {
            int totalDuration = 0;
            int sortIdx = 1;
            for (Map<String, Object> ch : chapters) {
                LearningChapter chapter = new LearningChapter();
                chapter.setPathId(path.getId());
                chapter.setTitle((String) ch.getOrDefault("title", "未命名章节"));
                chapter.setContent((String) ch.getOrDefault("content", ""));
                int sortOrder = ch.get("sortOrder") != null ? ((Number) ch.get("sortOrder")).intValue() : sortIdx;
                chapter.setSortOrder(sortOrder);
                chapter.setDuration(ch.get("duration") != null ? ((Number) ch.get("duration")).intValue() : 30);
                chapter.setDocIds("");
                chapter.setFlashcardIds("");
                requireWrite(chapterMapper.insert(chapter));
                totalDuration += chapter.getDuration() != null ? chapter.getDuration() : 0;
                sortIdx++;
            }
            // 更新路径统计
            path.setChapterCount(chapters.size());
            path.setTotalDuration(totalDuration);
            requireWrite(pathMapper.updateById(path));
        }

        return path;
    }

    /** Inserts a generated batch; partial persistence is rolled back on any failed card write. */
    @Transactional(rollbackFor = Exception.class)
    public List<LearningFlashcard> createGeneratedCards(List<LearningFlashcard> cards) {
        for (LearningFlashcard card : cards) {
            requireWrite(flashcardMapper.insert(card));
        }
        return cards;
    }

    private void requireWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Generated learning content write failed");
        }
    }
}
