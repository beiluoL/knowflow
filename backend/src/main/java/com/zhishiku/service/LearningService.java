package com.zhishiku.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhishiku.entity.LearningPath;
import com.zhishiku.vo.FlashcardVO;
import com.zhishiku.vo.LearningChapterVO;
import com.zhishiku.vo.LearningPathVO;
import com.zhishiku.vo.LearningTaskVO;

import java.util.List;

public interface LearningService extends IService<LearningPath> {

    List<LearningPathVO> getPathList();

    LearningPathVO getPathDetail(Long pathId);

    List<LearningChapterVO> getChapterList(Long pathId, Long userId);

    LearningChapterVO getChapterDetail(Long chapterId, Long userId);

    List<FlashcardVO> getFlashcardList(Long pathId, Long chapterId);

    List<LearningTaskVO> getTaskList(Long userId);

    void enrollPath(Long pathId, Long userId);

    void completeChapter(Long chapterId, Long userId);
}
