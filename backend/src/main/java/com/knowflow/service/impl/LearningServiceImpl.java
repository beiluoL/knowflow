package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningTask;
import com.knowflow.entity.LearningUserChapter;
import com.knowflow.entity.LearningUserPath;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.mapper.LearningTaskMapper;
import com.knowflow.mapper.LearningUserChapterMapper;
import com.knowflow.mapper.LearningUserPathMapper;
import com.knowflow.service.LearningService;
import com.knowflow.service.NotificationService;
import com.knowflow.vo.FlashcardVO;
import com.knowflow.vo.LearningChapterVO;
import com.knowflow.vo.LearningPathVO;
import com.knowflow.vo.LearningTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningServiceImpl extends ServiceImpl<LearningPathMapper, LearningPath> implements LearningService {

    private final LearningChapterMapper chapterMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final LearningTaskMapper taskMapper;
    private final LearningUserPathMapper userPathMapper;
    private final LearningUserChapterMapper userChapterMapper;
    private final NotificationService notificationService;

    @Override
    public List<LearningPathVO> getPathList() {
        List<LearningPath> paths = this.list(new LambdaQueryWrapper<LearningPath>()
                .eq(LearningPath::getStatus, 1)
                .orderByAsc(LearningPath::getSortOrder));
        return paths.stream()
                .map(p -> BeanUtil.copyProperties(p, LearningPathVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public LearningPathVO getPathDetail(Long pathId) {
        LearningPath path = this.getById(pathId);
        if (path == null || path.getStatus() == null || path.getStatus() != 1) {
            throw new BusinessException("学习路径不存在");
        }
        return BeanUtil.copyProperties(path, LearningPathVO.class);
    }

    @Override
    public List<LearningChapterVO> getChapterList(Long pathId, Long userId) {
        List<LearningChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<LearningChapter>()
                .eq(LearningChapter::getPathId, pathId)
                .orderByAsc(LearningChapter::getSortOrder));
        return chapters.stream()
                .map(c -> {
                    LearningChapterVO vo = BeanUtil.copyProperties(c, LearningChapterVO.class);
                    vo.setCompleted(false);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public LearningChapterVO getChapterDetail(Long chapterId, Long userId) {
        LearningChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        LearningChapterVO vo = BeanUtil.copyProperties(chapter, LearningChapterVO.class);
        vo.setCompleted(false);
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
                throw new BusinessException("学习路径不存在或未发布");
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
            throw new BusinessException("任务不存在");
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
            throw new BusinessException("任务不存在");
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
            throw new BusinessException("学习路径不存在");
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
            throw new BusinessException("章节不存在");
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

    @Override
    public void reviewFlashcard(Long flashcardId, Long userId, Integer quality) {
        LearningFlashcard card = flashcardMapper.selectById(flashcardId);
        if (card == null) {
            throw new BusinessException("闪卡不存在");
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
}
