package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import com.knowflow.dto.FlashcardGenerateDTO;
import com.knowflow.dto.FlashcardSaveDTO;
import com.knowflow.entity.AiPersonalizedPath;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningTask;
import com.knowflow.entity.LearningUserChapter;
import com.knowflow.entity.LearningUserPath;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.AiPersonalizedPathMapper;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.mapper.LearningTaskMapper;
import com.knowflow.mapper.LearningUserChapterMapper;
import com.knowflow.mapper.LearningUserPathMapper;
import com.knowflow.service.AiService;
import com.knowflow.service.LearningService;
import com.knowflow.service.NotificationService;
import com.knowflow.vo.CategoryMasteryVO;
import com.knowflow.vo.DailyActivityVO;
import com.knowflow.vo.FlashcardVO;
import com.knowflow.vo.LearningChapterVO;
import com.knowflow.vo.LearningPathVO;
import com.knowflow.vo.LearningTaskVO;
import com.knowflow.vo.MasteryDistributionVO;
import com.knowflow.vo.PersonalizedPathVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 学习中心业务服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningServiceImpl extends ServiceImpl<LearningPathMapper, LearningPath> implements LearningService {

    private final LearningChapterMapper chapterMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final LearningTaskMapper taskMapper;
    private final LearningUserPathMapper userPathMapper;
    private final LearningUserChapterMapper userChapterMapper;
    private final LearningMistakeMapper mistakeMapper;
    private final DocReadProgressMapper readProgressMapper;
    private final DocDocumentMapper docMapper;
    private final DocCategoryMapper categoryMapper;
    private final AiPersonalizedPathMapper personalizedPathMapper;
    private final NotificationService notificationService;
    private final AiService aiService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 分类掌握度薄弱阈值：正确率低于此值标记为薄弱项 */
    private static final int WEAK_THRESHOLD = 60;

    @Override
    public List<LearningPathVO> getPathList() {
        // 仅返回平台公开路径（owner_user_id=0），避免用户采用落地的私有个性化路径污染公共列表。
        List<LearningPath> paths = this.list(new LambdaQueryWrapper<LearningPath>()
                .eq(LearningPath::getStatus, 1)
                .eq(LearningPath::getOwnerUserId, 0L)
                .orderByAsc(LearningPath::getSortOrder));
        return paths.stream()
                .map(p -> BeanUtil.copyProperties(p, LearningPathVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public LearningPathVO getPathDetail(Long pathId) {
        LearningPath path = this.getById(pathId);
        if (path == null || path.getStatus() == null || path.getStatus() != 1) {
            throw new BusinessException(404, "学习路径不存在");
        }
        return BeanUtil.copyProperties(path, LearningPathVO.class);
    }

    @Override
    public List<LearningChapterVO> getChapterList(Long pathId, Long userId) {
        List<LearningChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<LearningChapter>()
                .eq(LearningChapter::getPathId, pathId)
                .orderByAsc(LearningChapter::getSortOrder));
        // 查询已完成的章节ID集
        List<LearningUserChapter> doneList = userChapterMapper.selectList(
                new LambdaQueryWrapper<LearningUserChapter>().eq(LearningUserChapter::getUserId, userId));
        Set<Long> completedIds = doneList.stream()
                .map(LearningUserChapter::getChapterId).collect(Collectors.toSet());
        return chapters.stream()
                .map(c -> {
                    LearningChapterVO vo = BeanUtil.copyProperties(c, LearningChapterVO.class);
                    vo.setCompleted(completedIds.contains(c.getId()));
                    // L-PATH 前置解锁：前置章节未全部完成时 locked=true
                    String prereqStr = c.getPrerequisiteChapterIds();
                    boolean locked = false;
                    if (StrUtil.isNotBlank(prereqStr)) {
                        for (String pid : prereqStr.split(",")) {
                            try {
                                if (!completedIds.contains(Long.parseLong(pid.trim()))) {
                                    locked = true;
                                    break;
                                }
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                    vo.setLocked(locked && !completedIds.contains(c.getId()));
                    vo.setPrerequisiteChapterIds(prereqStr);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public LearningChapterVO getChapterDetail(Long chapterId, Long userId) {
        LearningChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        LearningChapterVO vo = BeanUtil.copyProperties(chapter, LearningChapterVO.class);
        boolean completed = userChapterMapper.selectCount(new LambdaQueryWrapper<LearningUserChapter>()
                .eq(LearningUserChapter::getUserId, userId)
                .eq(LearningUserChapter::getChapterId, chapterId)) > 0;
        vo.setCompleted(completed);
        // 前置解锁检查
        String prereqStr = chapter.getPrerequisiteChapterIds();
        boolean locked = false;
        if (StrUtil.isNotBlank(prereqStr)) {
            for (String pid : prereqStr.split(",")) {
                try {
                    if (userChapterMapper.selectCount(new LambdaQueryWrapper<LearningUserChapter>()
                            .eq(LearningUserChapter::getUserId, userId)
                            .eq(LearningUserChapter::getChapterId, Long.parseLong(pid.trim()))) == 0) {
                        locked = true;
                        break;
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        vo.setLocked(locked && !completed);
        vo.setPrerequisiteChapterIds(prereqStr);
        return vo;
    }

    @Override
    public List<FlashcardVO> getFlashcardList(Long pathId, Long chapterId) {
        Long effectivePathId = pathId;
        if (effectivePathId == null && chapterId != null) {
            LearningChapter chapter = chapterMapper.selectById(chapterId);
            if (chapter != null) {
                effectivePathId = chapter.getPathId();
            }
        }
        if (effectivePathId != null) {
            LearningPath path = this.getById(effectivePathId);
            if (path == null || path.getStatus() == null || path.getStatus() != 1) {
                throw new BusinessException(404, "学习路径不存在或未发布");
            }
        }
        LambdaQueryWrapper<LearningFlashcard> wrapper = new LambdaQueryWrapper<>();
        if (pathId != null) {
            wrapper.eq(LearningFlashcard::getPathId, pathId);
        }
        if (chapterId != null) {
            wrapper.eq(LearningFlashcard::getChapterId, chapterId);
        }
        List<LearningFlashcard> flashcards = flashcardMapper.selectList(wrapper);
        return flashcards.stream()
                .map(f -> BeanUtil.copyProperties(f, FlashcardVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<LearningTaskVO> getTaskList(Long userId) {
        List<LearningTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .orderByDesc(LearningTask::getCreateTime));
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .map(t -> BeanUtil.copyProperties(t, LearningTaskVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public void createTask(LearningTask task, Long userId) {
        task.setUserId(userId);
        if (task.getStatus() == null) {
            task.setStatus(0);
        }
        if (task.getExpReward() == null) {
            task.setExpReward(10);
        }
        if (task.getEnergyCost() == null) {
            task.setEnergyCost(5);
        }
        taskMapper.insert(task);
    }

    @Override
    public void updateTaskStatus(Long taskId, Long userId, Integer status) {
        LearningTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (!java.util.Objects.equals(task.getUserId(), userId)) {
            throw new BusinessException("无权操作该任务");
        }
        task.setStatus(status);
        taskMapper.updateById(task);
    }

    @Override
    public void deleteTask(Long taskId, Long userId) {
        LearningTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (!java.util.Objects.equals(task.getUserId(), userId)) {
            throw new BusinessException("无权删除该任务");
        }
        taskMapper.deleteById(taskId);
    }

    @Override
    @Transactional
    public void enrollPath(Long pathId, Long userId) {
        LearningPath path = this.getById(pathId);
        if (path == null) {
            throw new BusinessException(404, "学习路径不存在");
        }
        LearningUserPath userPath = userPathMapper.selectOne(new LambdaQueryWrapper<LearningUserPath>()
                .eq(LearningUserPath::getUserId, userId)
                .eq(LearningUserPath::getPathId, pathId));
        if (userPath != null) {
            throw new BusinessException("已经报名该学习路径");
        }
        userPath = new LearningUserPath();
        userPath.setUserId(userId);
        userPath.setPathId(pathId);
        userPath.setProgress(BigDecimal.ZERO);
        userPath.setCompletedChapters(0);
        userPath.setEnrollTime(LocalDateTime.now());
        userPath.setLastStudyTime(LocalDateTime.now());
        userPathMapper.insert(userPath);
        this.update(new LambdaUpdateWrapper<LearningPath>()
                .eq(LearningPath::getId, pathId)
                .setSql("enrolled_count = enrolled_count + 1"));
        notificationService.createNotification(userId, "enroll", "报名成功",
                "你已成功报名学习路径《" + path.getTitle() + "》", pathId, "path");
        LearningTask task = new LearningTask();
        task.setUserId(userId);
        task.setTitle("完成学习路径：" + path.getTitle());
        task.setType("path");
        task.setTargetId(pathId);
        task.setExpReward(50);
        task.setEnergyCost(10);
        task.setStatus(0);
        taskMapper.insert(task);
    }

    @Override
    @Transactional
    public void completeChapter(Long chapterId, Long userId) {
        LearningChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        // L-PATH-02 前置解锁检查：若有前置章节尚未完成，则不允许完成当前章节
        String prereqStr = chapter.getPrerequisiteChapterIds();
        if (StrUtil.isNotBlank(prereqStr)) {
            for (String pid : prereqStr.split(",")) {
                Long prevId = Long.parseLong(pid.trim());
                long count = userChapterMapper.selectCount(new LambdaQueryWrapper<LearningUserChapter>()
                        .eq(LearningUserChapter::getUserId, userId)
                        .eq(LearningUserChapter::getChapterId, prevId));
                if (count == 0) {
                    LearningChapter prev = chapterMapper.selectById(prevId);
                    String title = prev != null ? prev.getTitle() : String.valueOf(prevId);
                    throw new BusinessException("请先完成前置章节：「" + title + "」");
                }
            }
        }
        LearningUserPath userPath = userPathMapper.selectOne(new LambdaQueryWrapper<LearningUserPath>()
                .eq(LearningUserPath::getUserId, userId)
                .eq(LearningUserPath::getPathId, chapter.getPathId()));
        if (userPath == null) {
            throw new BusinessException("请先报名学习路径");
        }
        LearningUserChapter existing = userChapterMapper.selectOne(new LambdaQueryWrapper<LearningUserChapter>()
                .eq(LearningUserChapter::getUserId, userId)
                .eq(LearningUserChapter::getChapterId, chapterId));
        if (existing != null) {
            return;
        }
        LearningUserChapter uc = new LearningUserChapter();
        uc.setUserId(userId);
        uc.setPathId(chapter.getPathId());
        uc.setChapterId(chapterId);
        uc.setCompleteTime(LocalDateTime.now());
        userChapterMapper.insert(uc);
        userPathMapper.update(new LambdaUpdateWrapper<LearningUserPath>()
                .eq(LearningUserPath::getId, userPath.getId())
                .setSql("completed_chapters = completed_chapters + 1")
                .set(LearningUserPath::getLastStudyTime, LocalDateTime.now()));
        LearningUserPath updated = userPathMapper.selectById(userPath.getId());
        List<LearningChapter> allChapters = chapterMapper.selectList(new LambdaQueryWrapper<LearningChapter>()
                .eq(LearningChapter::getPathId, chapter.getPathId()));
        if (!allChapters.isEmpty()) {
            BigDecimal progress = new BigDecimal(updated.getCompletedChapters())
                    .divide(new BigDecimal(allChapters.size()), 2, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            userPathMapper.update(new LambdaUpdateWrapper<LearningUserPath>()
                    .eq(LearningUserPath::getId, userPath.getId())
                    .set(LearningUserPath::getProgress, progress));
        }
    }

    /** 闪卡复习调度（SM-2 间隔重复算法）：按评分 quality(0~5) 计算下次复习间隔并保证边界。 */
    @Override
    public void reviewFlashcard(Long flashcardId, Long userId, Integer quality) {
        LearningFlashcard card = flashcardMapper.selectById(flashcardId);
        if (card == null) {
            throw new BusinessException(404, "闪卡不存在");
        }
        int q = quality != null ? quality : 3;
        if (q < 0) q = 0;
        if (q > 5) q = 5;

        int reviewCount = card.getReviewCount() != null ? card.getReviewCount() : 0;
        int interval = card.getReviewInterval() != null ? card.getReviewInterval() : 0;

        if (q < 3) {
            reviewCount = 0;
            interval = 1;
        } else {
            reviewCount++;
            if (reviewCount == 1) {
                interval = 1;
            } else if (reviewCount == 2) {
                interval = 6;
            } else {
                double ef = 2.5;
                if (q == 3) ef = 2.0;
                else if (q == 4) ef = 2.3;
                else if (q == 5) ef = 2.8;
                interval = (int) Math.max(1, Math.round(interval * ef));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReview = now.plusDays(interval);

        card.setReviewCount(reviewCount);
        card.setReviewInterval(interval);
        card.setLastReviewTime(now);
        card.setNextReviewTime(nextReview);
        flashcardMapper.updateById(card);
    }

    /** C① 学习热力图：聚合用户阅读/完成章节/复习错题事件，返回最近 days 天每日计数。 */
    @Override
    public List<DailyActivityVO> getDailyActivity(Long userId, int days) {
        int d = days <= 0 ? 120 : Math.min(days, 365);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(d - 1);
        Map<LocalDate, Integer> counts = new HashMap<>();

        List<DocReadProgress> reads = readProgressMapper.selectList(
                new LambdaQueryWrapper<DocReadProgress>().eq(DocReadProgress::getUserId, userId));
        for (DocReadProgress r : reads) {
            if (r.getLastReadTime() != null) {
                counts.merge(r.getLastReadTime().toLocalDate(), 1, Integer::sum);
            }
        }
        List<LearningUserChapter> chapters = userChapterMapper.selectList(
                new LambdaQueryWrapper<LearningUserChapter>().eq(LearningUserChapter::getUserId, userId));
        for (LearningUserChapter c : chapters) {
            if (c.getCompleteTime() != null) {
                counts.merge(c.getCompleteTime().toLocalDate(), 1, Integer::sum);
            }
        }
        List<LearningMistake> mistakes = mistakeMapper.selectList(
                new LambdaQueryWrapper<LearningMistake>().eq(LearningMistake::getUserId, userId)
                        .isNotNull(LearningMistake::getLastReviewTime));
        for (LearningMistake m : mistakes) {
            if (m.getLastReviewTime() != null) {
                counts.merge(m.getLastReviewTime().toLocalDate(), 1, Integer::sum);
            }
        }

        List<DailyActivityVO> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DailyActivityVO vo = new DailyActivityVO();
            vo.setDate(date.toString());
            vo.setCount(counts.getOrDefault(date, 0));
            result.add(vo);
        }
        return result;
    }

    /** C① 掌握分布看板：用户维度闪卡难度分布、待复习/已复习与错题掌握情况。 */
    @Override
    public MasteryDistributionVO getMasteryDistribution(Long userId) {
        MasteryDistributionVO vo = new MasteryDistributionVO();
        List<LearningFlashcard> cards = flashcardMapper.selectList(new LambdaQueryWrapper<LearningFlashcard>()
                .eq(LearningFlashcard::getUserId, userId));
        int total = cards.size();
        int easy = 0;
        int medium = 0;
        int hard = 0;
        int due = 0;
        int reviewed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (LearningFlashcard c : cards) {
            int diff = c.getDifficulty() != null ? c.getDifficulty() : 1;
            if (diff == 1) easy++;
            else if (diff == 2) medium++;
            else hard++;
            if (c.getReviewCount() != null && c.getReviewCount() > 0) reviewed++;
            if (c.getNextReviewTime() == null || !c.getNextReviewTime().isAfter(now)) due++;
        }
        vo.setFlashcardTotal(total);
        vo.setFlashcardDiffEasy(easy);
        vo.setFlashcardDiffMedium(medium);
        vo.setFlashcardDiffHard(hard);
        vo.setFlashcardDue(due);
        vo.setFlashcardReviewed(reviewed);
        int mastered = Math.toIntExact(mistakeMapper.selectCount(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId).eq(LearningMistake::getMastered, 1)));
        int pending = Math.toIntExact(mistakeMapper.selectCount(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId).eq(LearningMistake::getMastered, 0)));
        vo.setMistakeMastered(mastered);
        vo.setMistakePending(pending);
        return vo;
    }

    @Override
    public List<CategoryMasteryVO> getCategoryMastery(Long userId) {
        String sql = """
                SELECT q.category,
                       COUNT(*) AS total,
                       SUM(CASE WHEN r.is_correct = 1 THEN 1 ELSE 0 END) AS correct
                FROM quiz_answer_record r
                JOIN quiz_question q ON r.question_id = q.id
                WHERE r.user_id = ? AND r.deleted = 0 AND q.deleted = 0
                GROUP BY q.category
                ORDER BY CAST(SUM(CASE WHEN r.is_correct = 1 THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) ASC
                """;
        try {
            return jdbcTemplate.query(sql, new Object[]{userId}, (rs, rowNum) -> {
                CategoryMasteryVO vo = new CategoryMasteryVO();
                vo.setCategory(rs.getString("category"));
                vo.setTotal(rs.getInt("total"));
                vo.setCorrect(rs.getInt("correct"));
                int rate = vo.getTotal() > 0 ? vo.getCorrect() * 100 / vo.getTotal() : 0;
                vo.setRate(rate);
                vo.setWeak(rate < WEAK_THRESHOLD);
                return vo;
            });
        } catch (Exception e) {
            log.warn("分类掌握度查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ============================================================
    // 用户级「我的闪卡」
    // ============================================================

    private FlashcardVO toVO(LearningFlashcard entity, Map<Long, String> categoryNameMap, Map<Long, String> docTitleMap) {
        FlashcardVO vo = BeanUtil.copyProperties(entity, FlashcardVO.class);
        if (entity.getCategoryId() != null && categoryNameMap != null) {
            vo.setCategoryName(categoryNameMap.get(entity.getCategoryId()));
        }
        if (entity.getDocId() != null && docTitleMap != null) {
            vo.setDocTitle(docTitleMap.get(entity.getDocId()));
        }
        return vo;
    }

    private List<FlashcardVO> toVOs(List<LearningFlashcard> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> docIds = new HashSet<>();
        for (LearningFlashcard c : list) {
            if (c.getCategoryId() != null) categoryIds.add(c.getCategoryId());
            if (c.getDocId() != null) docIds.add(c.getDocId());
        }
        Map<Long, String> catNames = new HashMap<>();
        Map<Long, String> docTitles = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<DocCategory> cats = categoryMapper.selectBatchIds(categoryIds);
            for (DocCategory c : cats) catNames.put(c.getId(), c.getName());
        }
        if (!docIds.isEmpty()) {
            List<DocDocument> docs = docMapper.selectBatchIds(docIds);
            for (DocDocument d : docs) docTitles.put(d.getId(), d.getTitle());
        }
        return list.stream().map(c -> toVO(c, catNames, docTitles)).collect(Collectors.toList());
    }

    @Override
    public List<FlashcardVO> listMyFlashcards(Long userId, String keyword, String category,
                                              Integer difficulty, Long categoryId, String sourceType) {
        LambdaQueryWrapper<LearningFlashcard> w = new LambdaQueryWrapper<LearningFlashcard>()
                .eq(LearningFlashcard::getUserId, userId);
        if (StrUtil.isNotBlank(keyword)) {
            w.and(x -> x.like(LearningFlashcard::getFront, keyword)
                    .or().like(LearningFlashcard::getBack, keyword)
                    .or().like(LearningFlashcard::getTags, keyword));
        }
        if (StrUtil.isNotBlank(category)) w.eq(LearningFlashcard::getCategory, category);
        if (difficulty != null) w.eq(LearningFlashcard::getDifficulty, difficulty);
        if (categoryId != null) w.eq(LearningFlashcard::getCategoryId, categoryId);
        if (StrUtil.isNotBlank(sourceType)) w.eq(LearningFlashcard::getSourceType, sourceType);
        w.orderByDesc(LearningFlashcard::getCreateTime);
        return toVOs(flashcardMapper.selectList(w));
    }

    private LearningFlashcard requireMine(Long flashcardId, Long userId) {
        LearningFlashcard c = flashcardMapper.selectById(flashcardId);
        if (c == null) throw new BusinessException(404, "闪卡不存在");
        if (!Objects.equals(c.getUserId(), userId)) {
            throw new BusinessException(403, "无权操作该闪卡");
        }
        return c;
    }

    @Override
    public FlashcardVO getMyFlashcard(Long flashcardId, Long userId) {
        return toVOs(List.of(requireMine(flashcardId, userId))).get(0);
    }

    @Override
    @Transactional
    public FlashcardVO createMyFlashcard(Long userId, FlashcardSaveDTO dto) {
        if (StrUtil.isBlank(dto.getFront()) || StrUtil.isBlank(dto.getBack())) {
            throw new BusinessException("闪卡正面和背面内容必填");
        }
        LearningFlashcard card = new LearningFlashcard();
        card.setUserId(userId);
        card.setPathId(dto.getPathId());
        card.setChapterId(dto.getChapterId());
        card.setCategoryId(dto.getCategoryId());
        card.setDocId(dto.getDocId());
        card.setFront(dto.getFront().trim());
        card.setBack(dto.getBack().trim());
        card.setCategory(StrUtil.trim(dto.getCategory()));
        card.setDifficulty(dto.getDifficulty() != null ? dto.getDifficulty() : 1);
        card.setTags(StrUtil.trim(dto.getTags()));
        card.setSourceType("MANUAL");
        card.setReviewCount(0);
        card.setReviewInterval(0);
        flashcardMapper.insert(card);
        return toVOs(List.of(card)).get(0);
    }

    @Override
    public void updateMyFlashcard(Long flashcardId, Long userId, FlashcardSaveDTO dto) {
        LearningFlashcard card = requireMine(flashcardId, userId);
        if (StrUtil.isNotBlank(dto.getFront())) card.setFront(dto.getFront().trim());
        if (StrUtil.isNotBlank(dto.getBack())) card.setBack(dto.getBack().trim());
        if (dto.getPathId() != null) card.setPathId(dto.getPathId());
        if (dto.getChapterId() != null) card.setChapterId(dto.getChapterId());
        if (dto.getCategoryId() != null) card.setCategoryId(dto.getCategoryId());
        if (dto.getDocId() != null) card.setDocId(dto.getDocId());
        if (dto.getCategory() != null) card.setCategory(StrUtil.trim(dto.getCategory()));
        if (dto.getDifficulty() != null) card.setDifficulty(dto.getDifficulty());
        if (dto.getTags() != null) card.setTags(StrUtil.trim(dto.getTags()));
        flashcardMapper.updateById(card);
    }

    @Override
    public void deleteMyFlashcard(Long flashcardId, Long userId) {
        requireMine(flashcardId, userId);
        flashcardMapper.deleteById(flashcardId);
    }

    @Override
    @Transactional
    public void deleteMyFlashcards(List<Long> flashcardIds, Long userId) {
        if (flashcardIds == null || flashcardIds.isEmpty()) return;
        for (Long id : flashcardIds) {
            LearningFlashcard c = flashcardMapper.selectById(id);
            if (c != null && Objects.equals(c.getUserId(), userId)) {
                flashcardMapper.deleteById(id);
            }
        }
    }

    @Override
    @Transactional
    public List<FlashcardVO> generateMyFlashcards(Long userId, FlashcardGenerateDTO dto) {
        Long docId = dto.getDocId();
        Long categoryId = dto.getCategoryId();
        if (docId == null && categoryId == null) {
            throw new BusinessException("请选择知识库或文档作为生成来源");
        }
        int count = dto.getCount() != null ? dto.getCount() : 10;
        if (count < 1) count = 1;
        if (count > 30) count = 30;

        StringBuilder prompt = new StringBuilder();
        String categoryName = null;
        if (docId != null) {
            DocDocument doc = docMapper.selectById(docId);
            if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) {
                throw new BusinessException(404, "文档不存在或未发布");
            }
            if (doc.getCategoryId() != null) {
                DocCategory cat = categoryMapper.selectById(doc.getCategoryId());
                if (cat != null) categoryName = cat.getName();
            }
            String content = doc.getContent() != null ? doc.getContent() : "";
            if (content.length() > 5000) content = content.substring(0, 5000);
            prompt.append("基于以下文章，生成 ").append(count).append(" 张复习闪卡，每张卡为一个问答对。\n")
                    .append("返回严格 JSON 数组，元素格式：{\"front\":\"问题\",\"back\":\"答案\",\"difficulty\":1,\"tags\":\"tag1,tag2\"}\n")
                    .append("difficulty 取值 1(简单)/2(中等)/3(困难)。不要输出 JSON 以外的任何文字。\n\n")
                    .append("标题：").append(doc.getTitle()).append("\n正文：\n").append(content);
        } else {
            DocCategory cat = categoryMapper.selectById(categoryId);
            if (cat == null) throw new BusinessException(404, "知识库不存在");
            categoryName = cat.getName();

            // 聚合知识库下所有文档（最多 15 篇，每篇截断 1500 字，防止超模型上下文）
            List<DocDocument> docs = docMapper.selectList(new LambdaQueryWrapper<DocDocument>()
                    .eq(DocDocument::getCategoryId, categoryId)
                    .eq(DocDocument::getStatus, 1)
                    .orderByDesc(DocDocument::getCreateTime)
                    .last("LIMIT 15"));
            StringBuilder ctx = new StringBuilder();
            for (DocDocument d : docs) {
                String c = d.getContent() != null ? d.getContent() : "";
                if (c.length() > 1500) c = c.substring(0, 1500);
                ctx.append("【").append(d.getTitle()).append("】\n").append(c).append("\n\n");
            }
            if (StrUtil.isBlank(ctx)) {
                throw new BusinessException("该知识库下暂无可生成的文档，请先上传文档");
            }
            prompt.append("基于以下知识库内多篇文章，生成 ").append(count).append(" 张复习闪卡，每张卡为一个问答对。\n")
                    .append("返回严格 JSON 数组，元素格式：{\"front\":\"问题\",\"back\":\"答案\",\"difficulty\":1,\"tags\":\"tag1,tag2\"}\n")
                    .append("difficulty 取值 1(简单)/2(中等)/3(困难)。不要输出 JSON 以外的任何文字。\n\n")
                    .append("知识库：").append(cat.getName()).append("\n文档内容：\n").append(ctx);
        }

        int diffPref = dto.getDifficultyPreference() != null ? dto.getDifficultyPreference() : 0;
        if (diffPref >= 1 && diffPref <= 3) {
            prompt.append("\n偏好难度：").append(diffPref).append("（1简单/2中等/3困难），尽量生成这个难度。");
        }

        String raw = aiService.complete(
                "你是资深闪卡生成助手，只输出符合要求的 JSON 数组。卡片问题要聚焦核心概念/易混点，答案简洁要点化。",
                prompt.toString());

        List<Map<String, Object>> parsed = parseFlashcardJson(raw);
        if (parsed.isEmpty()) {
            throw new BusinessException("AI 未返回有效闪卡，请调整输入后重试");
        }

        List<LearningFlashcard> cards = new ArrayList<>();
        String finalCategoryName = categoryName;
        for (Map<String, Object> item : parsed) {
            Object front = item.get("front");
            Object back = item.get("back");
            if (front == null || back == null) continue;
            LearningFlashcard c = new LearningFlashcard();
            c.setUserId(userId);
            c.setCategoryId(categoryId);
            c.setDocId(docId);
            c.setFront(String.valueOf(front).trim());
            c.setBack(String.valueOf(back).trim());
            c.setCategory(finalCategoryName);
            int d = 1;
            Object diff = item.get("difficulty");
            if (diff instanceof Number) d = ((Number) diff).intValue();
            if (d < 1 || d > 3) d = 1;
            c.setDifficulty(d);
            Object tags = item.get("tags");
            if (tags != null) c.setTags(String.valueOf(tags).trim());
            c.setSourceType(docId != null ? "AI_DOC" : "AI_KB");
            c.setReviewCount(0);
            c.setReviewInterval(0);
            cards.add(c);
        }
        if (cards.isEmpty()) {
            throw new BusinessException("AI 未返回有效闪卡，请调整输入后重试");
        }
        for (LearningFlashcard c : cards) flashcardMapper.insert(c);
        return toVOs(cards);
    }

    /** 解析 AI 返回的 JSON 数组（兼容 ```json 围栏与额外前缀文字）。 */
    private List<Map<String, Object>> parseFlashcardJson(String raw) {
        if (raw == null) return Collections.emptyList();
        String json = raw.trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end <= start) return Collections.emptyList();
        json = json.substring(start, end + 1);
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析闪卡 JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public int importMyFlashcards(Long userId, List<FlashcardSaveDTO> cards) {
        if (cards == null || cards.isEmpty()) return 0;
        int inserted = 0;
        for (FlashcardSaveDTO dto : cards) {
            if (StrUtil.isBlank(dto.getFront()) || StrUtil.isBlank(dto.getBack())) continue;
            LearningFlashcard c = new LearningFlashcard();
            c.setUserId(userId);
            c.setPathId(dto.getPathId());
            c.setChapterId(dto.getChapterId());
            c.setCategoryId(dto.getCategoryId());
            c.setDocId(dto.getDocId());
            c.setFront(dto.getFront().trim());
            c.setBack(dto.getBack().trim());
            c.setCategory(StrUtil.trim(dto.getCategory()));
            int d = dto.getDifficulty() != null ? dto.getDifficulty() : 1;
            if (d < 1 || d > 3) d = 1;
            c.setDifficulty(d);
            c.setTags(StrUtil.trim(dto.getTags()));
            c.setSourceType("IMPORT");
            c.setReviewCount(0);
            c.setReviewInterval(0);
            flashcardMapper.insert(c);
            inserted++;
        }
        return inserted;
    }

    @Override
    public List<FlashcardVO> exportMyFlashcards(Long userId) {
        return listMyFlashcards(userId, null, null, null, null, null);
    }

    // ========== 个性化学习路径（支持持久化缓存） ==========

    @Override
    public PersonalizedPathVO generatePersonalizedPath(Long userId, String goal, String level, Integer dailyMinutes) {
        String effectiveLevel = (level != null && !level.isBlank()) ? level : "入门";
        int effectiveDaily = (dailyMinutes != null && dailyMinutes > 0) ? dailyMinutes : 30;
        String effectiveGoal = (goal != null && !goal.isBlank()) ? goal : "综合提升";

        // 1. 查询缓存（按 user_id + goal + level + daily_minutes 维度）
        if (userId != null) {
            AiPersonalizedPath cached = personalizedPathMapper.selectOne(
                    new LambdaQueryWrapper<AiPersonalizedPath>()
                            .eq(AiPersonalizedPath::getUserId, userId)
                            .eq(AiPersonalizedPath::getGoal, effectiveGoal)
                            .eq(AiPersonalizedPath::getLevel, effectiveLevel)
                            .eq(AiPersonalizedPath::getDailyMinutes, effectiveDaily)
                            .last("LIMIT 1"));
            if (cached != null) {
                return entityToPathVo(cached);
            }
        }
        // 2. 未命中缓存，生成新的
        return generateAndSavePersonalizedPath(userId, effectiveGoal, effectiveLevel, effectiveDaily);
    }

    @Override
    public PersonalizedPathVO regeneratePersonalizedPath(Long userId, String goal, String level, Integer dailyMinutes) {
        String effectiveLevel = (level != null && !level.isBlank()) ? level : "入门";
        int effectiveDaily = (dailyMinutes != null && dailyMinutes > 0) ? dailyMinutes : 30;
        String effectiveGoal = (goal != null && !goal.isBlank()) ? goal : "综合提升";

        // 删除旧缓存（物理删除：避免逻辑删除与含 deleted 列的唯一索引反复重生成时冲突）
        if (userId != null) {
            personalizedPathMapper.physicalDeleteByKey(userId, effectiveGoal, effectiveLevel, effectiveDaily);
        }
        return generateAndSavePersonalizedPath(userId, effectiveGoal, effectiveLevel, effectiveDaily);
    }

    @Override
    public List<PersonalizedPathVO> listPersonalizedPaths(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<AiPersonalizedPath> list = personalizedPathMapper.selectList(
                new LambdaQueryWrapper<AiPersonalizedPath>()
                        .eq(AiPersonalizedPath::getUserId, userId)
                        .orderByDesc(AiPersonalizedPath::getCreateTime));
        return list.stream().map(this::entityToPathVo).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Long adoptPersonalizedPath(Long userId, Long personalizedId) {
        AiPersonalizedPath cache = personalizedPathMapper.selectById(personalizedId);
        if (cache == null || !Objects.equals(cache.getUserId(), userId)) {
            throw new BusinessException(404, "个性化路径不存在");
        }
        // 已采用且目标路径仍在：幂等返回，必要时补报名
        if (cache.getRelatedPathId() != null) {
            LearningPath existed = this.getById(cache.getRelatedPathId());
            if (existed != null) {
                ensureEnrolled(existed.getId(), userId);
                return existed.getId();
            }
        }
        // 解析章节规划
        List<PersonalizedPathVO.RecommendChapter> chapters = parseChapters(cache.getChaptersText());
        if (chapters.isEmpty()) {
            throw new BusinessException("该路径暂无可采用的章节，请重新生成");
        }
        // 1. 落地为真实学习路径（私有：owner_user_id=当前用户）
        LearningPath path = new LearningPath();
        path.setTitle(cache.getTitle() != null ? cache.getTitle() : (cache.getGoal() + "学习路径"));
        path.setDescription(cache.getReason());
        path.setLevel(cache.getLevel());
        path.setChapterCount(chapters.size());
        path.setTotalDuration(cache.getTotalDuration());
        path.setEnrolledCount(0);
        path.setSortOrder(0);
        path.setStatus(1);
        path.setOwnerUserId(userId);
        this.save(path);
        // 2. 落地章节
        for (PersonalizedPathVO.RecommendChapter rc : chapters) {
            LearningChapter ch = new LearningChapter();
            ch.setPathId(path.getId());
            ch.setTitle(rc.getTitle());
            // 将「学习重点」并入内容，保留 AI 规划的完整信息
            String content = rc.getContent() != null ? rc.getContent() : "";
            if (rc.getFocus() != null && !rc.getFocus().isBlank()) {
                content = content + (content.isBlank() ? "" : "\n\n") + "学习重点：" + rc.getFocus();
            }
            ch.setContent(content);
            ch.setSortOrder(rc.getSortOrder() != null ? rc.getSortOrder() : 0);
            ch.setDuration(rc.getDuration());
            chapterMapper.insert(ch);
        }
        // 3. 自动报名
        enrollPath(path.getId(), userId);
        // 4. 回填关联路径 ID，后续重复采用幂等
        cache.setRelatedPathId(path.getId());
        personalizedPathMapper.updateById(cache);
        return path.getId();
    }

    @Override
    public void deletePersonalizedPath(Long userId, Long personalizedId) {
        AiPersonalizedPath cache = personalizedPathMapper.selectById(personalizedId);
        if (cache == null || !Objects.equals(cache.getUserId(), userId)) {
            throw new BusinessException(404, "个性化路径不存在");
        }
        // 物理删除推荐记录（不影响已采用落地的 learning_path）
        personalizedPathMapper.physicalDeleteByIdAndUser(personalizedId, userId);
    }

    /** 若用户尚未报名目标路径则自动报名（采用幂等场景复用）。 */
    private void ensureEnrolled(Long pathId, Long userId) {
        LearningUserPath userPath = userPathMapper.selectOne(new LambdaQueryWrapper<LearningUserPath>()
                .eq(LearningUserPath::getUserId, userId)
                .eq(LearningUserPath::getPathId, pathId));
        if (userPath == null) {
            enrollPath(pathId, userId);
        }
    }

    /** 解析章节规划 JSON（chapters_text）为推荐章节列表。 */
    private List<PersonalizedPathVO.RecommendChapter> parseChapters(String chaptersText) {
        if (chaptersText == null || chaptersText.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(chaptersText,
                    new TypeReference<List<PersonalizedPathVO.RecommendChapter>>() {});
        } catch (Exception e) {
            log.warn("章节规划解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private PersonalizedPathVO generateAndSavePersonalizedPath(Long userId, String goal, String level, int dailyMinutes) {
        // 调用 AI 生成
        PersonalizedPathVO vo = callAiForPersonalizedPath(userId, goal, level, dailyMinutes);

        // 持久化到数据库
        if (userId != null && vo != null) {
            try {
                AiPersonalizedPath entity = pathVoToEntity(userId, goal, level, dailyMinutes, vo);
                personalizedPathMapper.insert(entity);
                // 回填自增主键与创建时间，使前端生成后可直接采用/删除
                vo.setId(entity.getId());
                if (entity.getCreateTime() != null) {
                    vo.setCreateTime(entity.getCreateTime().toString());
                }
            } catch (Exception e) {
                log.warn("个性化路径缓存保存失败: {}", e.getMessage());
            }
        }
        return vo;
    }

    @SuppressWarnings("unchecked")
    private PersonalizedPathVO callAiForPersonalizedPath(Long userId, String goal, String level, int dailyMinutes) {
        // 1. 收集用户学习数据
        StringBuilder userProfile = new StringBuilder();

        // 已报名的路径
        List<LearningUserPath> userPaths = userPathMapper.selectList(
                new LambdaQueryWrapper<LearningUserPath>().eq(LearningUserPath::getUserId, userId));
        if (!userPaths.isEmpty()) {
            userProfile.append("已报名学习路径：\n");
            for (LearningUserPath up : userPaths) {
                LearningPath p = this.getById(up.getPathId());
                if (p != null) {
                    userProfile.append(String.format("  - %s（进度 %.0f%%，已完成 %d 章）\n",
                            p.getTitle(), up.getProgress().doubleValue(), up.getCompletedChapters()));
                }
            }
        }

        // 已完成的章节
        List<LearningUserChapter> completedChapters = userChapterMapper.selectList(
                new LambdaQueryWrapper<LearningUserChapter>().eq(LearningUserChapter::getUserId, userId));
        userProfile.append("已完成章节数：").append(completedChapters.size()).append("\n");

        // 阅读过的文档
        List<DocReadProgress> readDocs = readProgressMapper.selectList(
                new LambdaQueryWrapper<DocReadProgress>().eq(DocReadProgress::getUserId, userId));
        userProfile.append("阅读文档数：").append(readDocs.size()).append("\n");

        // 闪卡复习情况
        List<LearningFlashcard> myCards = flashcardMapper.selectList(
                new LambdaQueryWrapper<LearningFlashcard>().eq(LearningFlashcard::getUserId, userId));
        userProfile.append("创建闪卡数：").append(myCards.size()).append("\n");

        // 错题数
        Long mistakeCount = mistakeMapper.selectCount(
                new LambdaQueryWrapper<LearningMistake>().eq(LearningMistake::getUserId, userId));
        userProfile.append("错题数：").append(mistakeCount).append("\n");

        // 2. 构建 prompt（level 和 dailyMinutes 已在调用前归一化）
        String systemPrompt = "你是 KnowFlow 学习平台的 AI 学习规划师。" +
                "请根据用户的学习数据和目标，生成一份个性化的学习路径推荐。" +
                "路径应循序渐进、难度递进，每个章节都要有明确的学习重点。";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请为用户生成个性化学习路径。\n\n");
        userPrompt.append("学习目标：").append(goal).append("\n");
        userPrompt.append("当前水平：").append(level).append("\n");
        userPrompt.append("每日可投入学习时间：").append(dailyMinutes).append(" 分钟\n\n");
        userPrompt.append("用户学习数据：\n").append(userProfile).append("\n\n");

        userPrompt.append("要求：\n");
        userPrompt.append("1. 根据用户当前水平和目标，设计 5-8 个章节\n");
        userPrompt.append("2. 章节难度应从基础到进阶逐步递进\n");
        userPrompt.append("3. 每个章节时长建议在 20-60 分钟之间\n");
        userPrompt.append("4. 每个章节标注学习重点（focus）\n");
        userPrompt.append("5. 总时长应合理，适合用户每日投入时间\n");
        userPrompt.append("6. 给出个性化学习建议（advice）\n\n");

        userPrompt.append("请严格按以下 JSON 格式输出（不要输出其他文字）：\n");
        userPrompt.append("```json\n");
        userPrompt.append("{\n");
        userPrompt.append("  \"title\": \"个性化学习路径标题\",\n");
        userPrompt.append("  \"reason\": \"推荐理由（50-100字）\",\n");
        userPrompt.append("  \"level\": \"入门|进阶|高级\",\n");
        userPrompt.append("  \"totalDuration\": 300,\n");
        userPrompt.append("  \"dailyDuration\": 30,\n");
        userPrompt.append("  \"goals\": [\"目标1\", \"目标2\"],\n");
        userPrompt.append("  \"chapters\": [\n");
        userPrompt.append("    {\"title\": \"章节标题\", \"content\": \"内容描述\", \"duration\": 30, \"sortOrder\": 1, \"focus\": \"学习重点\"}\n");
        userPrompt.append("  ],\n");
        userPrompt.append("  \"advice\": \"个性化学习建议（100-200字）\"\n");
        userPrompt.append("}\n");
        userPrompt.append("```");

        // 3. 调用 AI
        String rawResponse;
        try {
            rawResponse = aiService.complete(systemPrompt, userPrompt.toString(), null, userId);
        } catch (Exception e) {
            log.warn("AI 个性化路径生成失败: {}", e.getMessage());
            return buildDefaultPersonalizedPath(goal, level, dailyMinutes);
        }

        // 4. 解析响应
        try {
            String json = rawResponse.trim();
            // 去除 markdown 代码块
            if (json.startsWith("```json")) json = json.substring(7);
            else if (json.startsWith("```")) json = json.substring(3);
            if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
            json = json.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) json = json.substring(start, end + 1);

            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});

            PersonalizedPathVO vo = new PersonalizedPathVO();
            vo.setTitle((String) map.getOrDefault("title", goal + "学习路径"));
            vo.setReason((String) map.getOrDefault("reason", ""));
            vo.setLevel((String) map.getOrDefault("level", level));
            Object td = map.get("totalDuration");
            vo.setTotalDuration(td instanceof Number ? ((Number) td).intValue() : dailyMinutes * 10);
            vo.setDailyDuration(dailyMinutes);

            Object goalsObj = map.get("goals");
            if (goalsObj instanceof List) {
                vo.setGoals(((List<Object>) goalsObj).stream()
                        .map(String::valueOf).collect(Collectors.toList()));
            } else {
                vo.setGoals(List.of(goal));
            }

            List<PersonalizedPathVO.RecommendChapter> chapters = new ArrayList<>();
            Object chaptersObj = map.get("chapters");
            if (chaptersObj instanceof List) {
                for (Object item : (List<Object>) chaptersObj) {
                    if (item instanceof Map) {
                        Map<String, Object> ch = (Map<String, Object>) item;
                        PersonalizedPathVO.RecommendChapter chapter = new PersonalizedPathVO.RecommendChapter();
                        chapter.setTitle((String) ch.getOrDefault("title", "未命名章节"));
                        chapter.setContent((String) ch.getOrDefault("content", ""));
                        Object dur = ch.get("duration");
                        chapter.setDuration(dur instanceof Number ? ((Number) dur).intValue() : dailyMinutes);
                        Object so = ch.get("sortOrder");
                        chapter.setSortOrder(so instanceof Number ? ((Number) so).intValue() : chapters.size() + 1);
                        chapter.setFocus((String) ch.getOrDefault("focus", ""));
                        chapters.add(chapter);
                    }
                }
            }
            vo.setChapters(chapters);
            vo.setAdvice((String) map.getOrDefault("advice", ""));

            return vo;
        } catch (Exception e) {
            log.warn("个性化路径 JSON 解析失败: {}", e.getMessage());
            return buildDefaultPersonalizedPath(goal, level, dailyMinutes);
        }
    }

    private PersonalizedPathVO buildDefaultPersonalizedPath(String goal, String level, int dailyMinutes) {
        PersonalizedPathVO vo = new PersonalizedPathVO();
        vo.setTitle((goal != null ? goal : "综合提升") + "学习路径");
        vo.setReason("基于您的学习数据生成的推荐路径，建议按顺序学习");
        vo.setLevel(level);
        vo.setTotalDuration(dailyMinutes * 10);
        vo.setDailyDuration(dailyMinutes);
        vo.setGoals(List.of(goal != null ? goal : "综合提升"));

        List<PersonalizedPathVO.RecommendChapter> chapters = new ArrayList<>();
        String[] titles = {"基础概念入门", "核心原理掌握", "实战练习巩固", "进阶技巧提升", "综合项目实战"};
        String[] focuses = {"理解基本概念和术语", "掌握核心工作原理", "通过练习加深理解", "学习高级用法和最佳实践", "综合应用所学知识"};
        for (int i = 0; i < titles.length; i++) {
            PersonalizedPathVO.RecommendChapter ch = new PersonalizedPathVO.RecommendChapter();
            ch.setTitle(titles[i]);
            ch.setContent("系统学习" + titles[i] + "的相关知识");
            ch.setDuration(dailyMinutes);
            ch.setSortOrder(i + 1);
            ch.setFocus(focuses[i]);
            chapters.add(ch);
        }
        vo.setChapters(chapters);
        vo.setAdvice("建议每日保持 " + dailyMinutes + " 分钟的学习时间，按章节顺序循序渐进。学完每章后可生成闪卡巩固记忆，遇到难点可查看概念图解加深理解。");
        return vo;
    }

    /** 个性化路径实体转 VO（反序列化 JSON 字段）。 */
    @SuppressWarnings("unchecked")
    private PersonalizedPathVO entityToPathVo(AiPersonalizedPath e) {
        PersonalizedPathVO vo = new PersonalizedPathVO();
        vo.setId(e.getId());
        vo.setTitle(e.getTitle());
        vo.setReason(e.getReason());
        vo.setLevel(e.getLevel());
        vo.setTotalDuration(e.getTotalDuration());
        vo.setDailyDuration(e.getDailyMinutes());
        vo.setAdvice(e.getAdvice());
        vo.setRelatedPathId(e.getRelatedPathId());
        if (e.getCreateTime() != null) {
            vo.setCreateTime(e.getCreateTime().toString());
        }
        try {
            if (e.getGoalsText() != null && !e.getGoalsText().isBlank()) {
                vo.setGoals(objectMapper.readValue(e.getGoalsText(), new TypeReference<List<String>>() {}));
            } else {
                vo.setGoals(Collections.emptyList());
            }
        } catch (Exception ex) {
            vo.setGoals(Collections.emptyList());
        }
        try {
            if (e.getChaptersText() != null && !e.getChaptersText().isBlank()) {
                vo.setChapters(objectMapper.readValue(e.getChaptersText(),
                        new TypeReference<List<PersonalizedPathVO.RecommendChapter>>() {}));
            } else {
                vo.setChapters(Collections.emptyList());
            }
        } catch (Exception ex) {
            vo.setChapters(Collections.emptyList());
        }
        return vo;
    }

    /** 个性化路径 VO 转实体（序列化 JSON 字段）。 */
    private AiPersonalizedPath pathVoToEntity(Long userId, String goal, String level, int dailyMinutes,
                                              PersonalizedPathVO vo) throws Exception {
        AiPersonalizedPath e = new AiPersonalizedPath();
        e.setUserId(userId);
        e.setGoal(goal);
        e.setLevel(level);
        e.setDailyMinutes(dailyMinutes);
        e.setTitle(vo.getTitle());
        e.setReason(vo.getReason());
        e.setTotalDuration(vo.getTotalDuration());
        e.setAdvice(vo.getAdvice());
        e.setRelatedPathId(vo.getRelatedPathId());
        e.setGoalsText(vo.getGoals() != null ? objectMapper.writeValueAsString(vo.getGoals()) : "[]");
        e.setChaptersText(vo.getChapters() != null ? objectMapper.writeValueAsString(vo.getChapters()) : "[]");
        return e;
    }
}
