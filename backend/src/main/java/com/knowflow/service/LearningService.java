package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.common.PageResult;
import com.knowflow.dto.FlashcardGenerateDTO;
import com.knowflow.dto.FlashcardSaveDTO;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningTask;
import com.knowflow.vo.DailyActivityVO;
import com.knowflow.vo.FlashcardVO;
import com.knowflow.vo.LearningCertificateVO;
import com.knowflow.vo.LearningChapterVO;
import com.knowflow.vo.ChapterKnowledgeVO;
import com.knowflow.vo.LearningPathVO;
import com.knowflow.vo.LearningTaskVO;
import com.knowflow.vo.CategoryMasteryVO;
import com.knowflow.vo.ChapterDagVO;import com.knowflow.vo.MasteryDistributionVO;
import com.knowflow.vo.PersonalizedPathVO;

import java.util.List;

/** 学习中心业务服务接口。 */
public interface LearningService extends IService<LearningPath> {

    /**
     * 学习路径列表（含当前用户报名状态与学习进度）。
     * @param userId 当前用户 ID；匿名访问时传 null，enrolled/progress 均为空。
     */
    List<LearningPathVO> getPathList(Long userId);

    LearningPathVO getPathDetail(Long pathId, Long userId);

    List<LearningChapterVO> getChapterList(Long pathId, Long userId);

    /** 构建章节依赖关系图（DAG）：节点为章节，边为前置依赖，状态复用前置解锁规则。 */
    ChapterDagVO getChapterDag(Long pathId, Long userId);

    LearningChapterVO getChapterDetail(Long chapterId, Long userId);

    /**
     * Architecture-4.2：解析某章节对应的真实知识点（Knowledge Context 服务端唯一来源）。
     *
     * <p>推导链：{@code learning_chapter.docIds → resource_knowledge_mapping(DOC, ACCEPTED) → kg_entity}。
     * 全部为真实映射，无映射时返回空列表（调用方应如实提示，禁止回退到名称猜测）。</p>
     *
     * @param chapterId 章节 ID
     * @return 章节知识点解析结果；章节不存在抛 404
     */
    ChapterKnowledgeVO getChapterKnowledge(Long chapterId);

    /**
     * 闪卡列表（**用户私有数据**）。
     *
     * <p><b>P0-1 修复</b>：原签名 {@code getFlashcardList(Long pathId, Long chapterId)} 缺少归属者参数，
     * 当 {@code pathId} 与 {@code chapterId} 同时为空时查询条件为空，
     * {@code selectList(空 wrapper)} 会返回<b>全表所有用户</b>的闪卡 —— 配合
     * {@code SecurityConfig} 对该端点的 {@code permitAll}，造成匿名可读全站闪卡的数据泄漏。</p>
     *
     * <p>语义澄清：{@code learning_flashcard.user_id} 为 {@code NOT NULL}（见 db/h2/schema.sql），
     * 该表按设计即「用户级私有闪卡」，不存在「公共闪卡池」概念；
     * {@code pathId}/{@code chapterId} 只是**本人卡片**对学习路径/章节的可选归属过滤，
     * 而非跨用户共享维度。管理员的全站视图走独立的
     * {@code GET /api/admin/learning/flashcards}（ADMIN 门禁）。</p>
     *
     * <p>因此本方法**无条件**按 {@code userId} 过滤：userId 为空即视为非法调用并抛 401。</p>
     *
     * @param userId    当前登录用户 ID（必须来自安全上下文，禁止由前端传入）
     * @param pathId    可选：限定本人卡片所属学习路径
     * @param chapterId 可选：限定本人卡片所属章节
     */
    List<FlashcardVO> getFlashcardList(Long userId, Long pathId, Long chapterId);

    List<LearningTaskVO> getTaskList(Long userId);

    void createTask(LearningTask task, Long userId);

    void updateTaskStatus(Long taskId, Long userId, Integer status);

    void deleteTask(Long taskId, Long userId);

    void enrollPath(Long pathId, Long userId);

    void completeChapter(Long chapterId, Long userId);

    LearningChapterVO saveReadingPosition(Long chapterId, Long userId, String anchor, Long expectedRevision, String expectedContentHash, String presentationHash);

    /**
     * 上报章节视频观看进度（L-FORM-01）：不存在记录则创建，已存在则更新。
     * @param progress 观看进度百分比（0-100）
     * @return 当前累计观看进度（百分比）
     */
    java.math.BigDecimal updateVideoProgress(Long chapterId, Long userId, java.math.BigDecimal progress);

    // ========== 数字证书（G-CERT-01） ==========

    /** 查询当前用户的所有证书（按颁发时间倒序）。 */
    List<LearningCertificateVO> listMyCertificates(Long userId);

    /** 查询证书详情（本人可见全部字段；他人仅可见可公开的展示信息）。 */
    LearningCertificateVO getCertificateDetail(Long certificateId, Long userId);

    /** 按验证码验证证书，返回证书公开信息（供任意用户/访客核验真伪）。 */
    LearningCertificateVO verifyCertificate(String certNo);

    /** 复习闪卡：依据评分 quality(0~5) 计算下次间隔（SM-2 算法）。 */
    void reviewFlashcard(Long flashcardId, Long userId, Integer quality);

    /**
     * 复习闪卡（携带真实知识点上下文）：在 SM-2 调度完全相同的前提下，
     * 把调用方已知的 knowledgeId 一并写入 FLASHCARD_REVIEWED 事件 metadata。
     * <p>为什么需要：Evidence 解析器按 {@code metadata.knowledgeId} 绑定知识点
     * （见 frontend/src/learning/evidence/resolver.ts 的 realKnowledgeId），
     * 事件不带 knowledgeId 时闪卡证据只能回落到「当前上下文」，无法归属到具体知识点。
     * 该参数为**可选**，缺失时行为与 {@link #reviewFlashcard(Long, Long, Integer)} 完全一致。</p>
     */
    void reviewFlashcard(Long flashcardId, Long userId, Integer quality, Long knowledgeId);

    /** 学习活跃度热力图：按日期聚合用户学习事件（阅读/完成章节/复习错题）。 */
    List<DailyActivityVO> getDailyActivity(Long userId, int days);

    /** 掌握分布看板：闪卡难度分布与错题掌握情况。 */
    MasteryDistributionVO getMasteryDistribution(Long userId);

    /**
     * 分类维度掌握度画像：统计各分类答题的正确率，标记薄弱项。
     * 按正确率升序排列（薄弱项在前），正确率 < 60% 自动标记 weak=true。
     */
    List<CategoryMasteryVO> getCategoryMastery(Long userId);

    // ========== 用户级「我的闪卡」新增能力 ==========

    /** 全量筛选查询：供导出等需要完整集合的内部场景使用。 */
    List<FlashcardVO> listMyFlashcards(Long userId, String keyword, String category,
                                       Integer difficulty, Long categoryId, String sourceType);

    /** 服务端分页查询：我创建的闪卡（支持关键词/分类/难度/知识库/来源）。 */
    PageResult<FlashcardVO> pageMyFlashcards(Long userId, String keyword, String category,
                                             Integer difficulty, Long categoryId, String sourceType,
                                             int pageNum, int pageSize);

    /** 当前用户待复习队列：新卡或 nextReviewTime 已到期，最多 100 张。 */
    List<FlashcardVO> listDueFlashcards(Long userId);

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
