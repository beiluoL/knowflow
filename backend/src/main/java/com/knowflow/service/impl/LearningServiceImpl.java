package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningTask;
import com.knowflow.entity.LearningUserPath;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.mapper.LearningTaskMapper;
import com.knowflow.mapper.LearningUserPathMapper;
import com.knowflow.service.LearningService;
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
        if (path == null) {
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
        path.setEnrolledCount(path.getEnrolledCount() + 1);
        this.updateById(path);
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
        userPath.setCompletedChapters(userPath.getCompletedChapters() + 1);
        userPath.setLastStudyTime(LocalDateTime.now());
        List<LearningChapter> allChapters = chapterMapper.selectList(new LambdaQueryWrapper<LearningChapter>()
                .eq(LearningChapter::getPathId, chapter.getPathId()));
        if (!allChapters.isEmpty()) {
            BigDecimal progress = new BigDecimal(userPath.getCompletedChapters())
                    .divide(new BigDecimal(allChapters.size()), 2, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            userPath.setProgress(progress);
        }
        userPathMapper.updateById(userPath);
    }
}
