package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.dto.FlashcardGenerateDTO;
import com.knowflow.dto.FlashcardSaveDTO;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningTask;
import com.knowflow.vo.DailyActivityVO;
import com.knowflow.vo.FlashcardVO;
import com.knowflow.vo.LearningChapterVO;
import com.knowflow.vo.LearningPathVO;
import com.knowflow.vo.LearningTaskVO;
import com.knowflow.vo.MasteryDistributionVO;
import com.knowflow.vo.PersonalizedPathVO;

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

    // ========== 用户级「我的闪卡」新增能力 ==========

    /** 分页/筛选查询：我创建的所有闪卡（支持关键词/分类/难度/知识库/来源）。 */
    List<FlashcardVO> listMyFlashcards(Long userId, String keyword, String category,
                                       Integer difficulty, Long categoryId, String sourceType);

    /** 根据 ID 查我的某张闪卡（带权限校验：必须是我的）。 */
    FlashcardVO getMyFlashcard(Long flashcardId, Long userId);

    /** 新建一张闪卡（归属到当前用户）。 */
    FlashcardVO createMyFlashcard(Long userId, FlashcardSaveDTO dto);

    /** 更新我自己的一张闪卡（权限校验）。 */
    void updateMyFlashcard(Long flashcardId, Long userId, FlashcardSaveDTO dto);

    /** 删除我自己的一张闪卡（权限校验）。 */
    void deleteMyFlashcard(Long flashcardId, Long userId);

    /** 批量删除我的多张闪卡。 */
    void deleteMyFlashcards(List<Long> flashcardIds, Long userId);

    /** AI 生成闪卡：基于文档或知识库，落库到当前用户空间。 */
    List<FlashcardVO> generateMyFlashcards(Long userId, FlashcardGenerateDTO dto);

    /** 导入闪卡（JSON 数组）：[{front, back, category, difficulty, tags}]。 */
    int importMyFlashcards(Long userId, List<FlashcardSaveDTO> cards);

    /** 导出我的所有闪卡（用于下载 JSON）。 */
    List<FlashcardVO> exportMyFlashcards(Long userId);

    // ========== 个性化学习路径 ==========

    /** AI 生成个性化学习路径推荐：基于用户学习历史、当前水平和目标。优先读缓存，未命中则生成并持久化。 */
    PersonalizedPathVO generatePersonalizedPath(Long userId, String goal, String level, Integer dailyMinutes);

    /** 重新生成个性化学习路径：删除旧缓存，AI 重新生成并持久化。 */
    PersonalizedPathVO regeneratePersonalizedPath(Long userId, String goal, String level, Integer dailyMinutes);

    /** 我的个性化路径历史：按创建时间倒序返回当前用户已保存的全部 AI 推荐路径。 */
    List<PersonalizedPathVO> listPersonalizedPaths(Long userId);

    /** 采用个性化路径：将 AI 推荐落地为真实学习路径（path+chapter）并自动报名，返回落地路径 ID。 */
    Long adoptPersonalizedPath(Long userId, Long personalizedId);

    /** 删除我的一条个性化路径推荐（物理删除，不影响已采用落地的学习路径）。 */
    void deletePersonalizedPath(Long userId, Long personalizedId);
}
