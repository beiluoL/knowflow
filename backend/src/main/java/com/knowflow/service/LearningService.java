package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningTask;
import com.knowflow.vo.FlashcardVO;
import com.knowflow.vo.LearningChapterVO;
import com.knowflow.vo.LearningPathVO;
import com.knowflow.vo.LearningTaskVO;

import java.util.List;

public interface LearningService extends IService<LearningPath> {

    List<LearningPathVO> getPathList();

    LearningPathVO getPathDetail(Long pathId);

    List<LearningChapterVO> getChapterList(Long pathId, Long userId);

    LearningChapterVO getChapterDetail(Long chapterId, Long userId);

    List<FlashcardVO> getFlashcardList(Long pathId, Long chapterId);

    List<LearningTaskVO> getTaskList(Long userId);

    void createTask(LearningTask task, Long userId);

    void updateTaskStatus(Long taskId, Long userId, Integer status);

    void deleteTask(Long taskId, Long userId);

    void enrollPath(Long pathId, Long userId);

    void completeChapter(Long chapterId, Long userId);

    void reviewFlashcard(Long flashcardId, Long userId, Integer quality);
}
