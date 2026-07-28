package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningTask;
import com.knowflow.vo.DailyActivityVO;
import com.knowflow.vo.FlashcardVO;
import com.knowflow.vo.LearningChapterVO;
import com.knowflow.vo.LearningPathVO;
import com.knowflow.vo.LearningTaskVO;
import com.knowflow.vo.MasteryDistributionVO;

import java.util.List;

/** 学习中心业务服务接口。 */
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

    /** 复习闪卡：依据评分 quality(0~5) 计算下次间隔（SM-2 算法）。 */
    void reviewFlashcard(Long flashcardId, Long userId, Integer quality);

    /** 学习活跃度热力图：按日期聚合用户学习事件（阅读/完成章节/复习错题）。 */
    List<DailyActivityVO> getDailyActivity(Long userId, int days);

    /** 掌握分布看板：闪卡难度分布与错题掌握情况。 */
    MasteryDistributionVO getMasteryDistribution(Long userId);
}
