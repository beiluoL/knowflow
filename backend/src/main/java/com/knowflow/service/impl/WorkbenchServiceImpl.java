package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.dto.WbCaptureDTO;
import com.knowflow.dto.WbNoteDTO;
import com.knowflow.dto.WbPalaceDTO;
import com.knowflow.dto.WbPalaceLociDTO;
import com.knowflow.dto.WbReviewCardDTO;
import com.knowflow.dto.WbReviewGradeDTO;
import com.knowflow.dto.WbStoryDTO;
import com.knowflow.entity.WbCapture;
import com.knowflow.entity.WbNote;
import com.knowflow.entity.WbPalace;
import com.knowflow.entity.WbPalaceLoci;
import com.knowflow.entity.WbReviewCard;
import com.knowflow.entity.WbReviewLog;
import com.knowflow.entity.WbStory;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.WbCaptureMapper;
import com.knowflow.mapper.WbNoteMapper;
import com.knowflow.mapper.WbPalaceLociMapper;
import com.knowflow.mapper.WbPalaceMapper;
import com.knowflow.mapper.WbReviewCardMapper;
import com.knowflow.mapper.WbReviewLogMapper;
import com.knowflow.mapper.WbStoryMapper;
import com.knowflow.service.WorkbenchService;
import com.knowflow.vo.WbForgettingCurveVO;
import com.knowflow.vo.WbReviewCardVO;
import com.knowflow.vo.WbReviewGradeResultVO;
import com.knowflow.vo.WorkbenchOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库工作台业务实现：四模块闭环（输入/整理/复习/输出）。
 * 复习采用经典 SM-2 间隔重复算法（SuperMemo-2），难度系数以整数放大 100 倍存储以避免浮点误差。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkbenchServiceImpl extends ServiceImpl<WbCaptureMapper, WbCapture>
        implements WorkbenchService {

    private final WbNoteMapper noteMapper;
    private final WbReviewCardMapper reviewCardMapper;
    private final WbReviewLogMapper reviewLogMapper;
    private final WbPalaceMapper palaceMapper;
    private final WbPalaceLociMapper lociMapper;
    private final WbStoryMapper storyMapper;

    /** SM-2 难度系数默认 2.50，以整数存储（×100）。 */
    private static final int DEFAULT_EF = 250;
    /** SM-2 难度系数下限 1.30（×100），防止过度压缩间隔。 */
    private static final int MIN_EF = 130;
    /** 评分质量 q 的及格线：≥2 视为答对，<2 视为遗忘（重置连续答对与间隔）。 */
    private static final int PASS_QUALITY = 2;
    /** 近 7 天复习统计窗口（天）。 */
    private static final int RECENT_DAYS = 7;

    // ============================ 总览 ============================

    @Override
    public WorkbenchOverviewVO overview(Long userId) {
        WorkbenchOverviewVO vo = new WorkbenchOverviewVO();
        vo.setCaptureTotal(countCapture(userId, null));
        vo.setCaptureInbox(countCapture(userId, "INBOX"));
        vo.setCaptureStarred(countCaptureStarred(userId));
        vo.setNoteTotal(noteMapper.selectCount(own(userId, WbNote::getUserId)));
        vo.setReviewDue(countDue(userId));
        vo.setReviewCount(sumReviewCount(userId));
        vo.setPalaceTotal(palaceMapper.selectCount(own(userId, WbPalace::getUserId)));
        vo.setLociTotal(lociMapper.selectCount(own(userId, WbPalaceLoci::getUserId)));
        vo.setStoryTotal(storyMapper.selectCount(own(userId, WbStory::getUserId)));
        vo.setStoryDraft(countStoryDraft(userId));
        vo.setReviewLast7d(countReviewLast7d(userId));
        return vo;
    }

    private long countCapture(Long userId, String status) {
        LambdaQueryWrapper<WbCapture> w = own(userId, WbCapture::getUserId);
        if (status != null) {
            w.eq(WbCapture::getStatus, status);
        }
        return baseMapper.selectCount(w);
    }

    private long countCaptureStarred(Long userId) {
        return baseMapper.selectCount(own(userId, WbCapture::getUserId).eq(WbCapture::getStarred, 1));
    }

    private long countDue(Long userId) {
        return reviewCardMapper.selectCount(own(userId, WbReviewCard::getUserId)
                .le(WbReviewCard::getNextReviewTime, LocalDateTime.now())
                .eq(WbReviewCard::getSuspended, 0));
    }

    private long sumReviewCount(Long userId) {
        List<WbReviewCard> cards = reviewCardMapper.selectList(own(userId, WbReviewCard::getUserId));
        return cards.stream().mapToLong(c -> c.getReviewCount() == null ? 0 : c.getReviewCount()).sum();
    }

    private long countStoryDraft(Long userId) {
        return storyMapper.selectCount(own(userId, WbStory::getUserId)
                .in(WbStory::getStatus, "DRAFT", "DONE"));
    }

    private long countReviewLast7d(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(RECENT_DAYS);
        return reviewLogMapper.selectCount(own(userId, WbReviewLog::getUserId)
                .ge(WbReviewLog::getCreateTime, since));
    }

    // ============================ 模块一：收集箱 ============================

    @Override
    public List<WbCapture> listCaptures(Long userId, String status, Long categoryId, String keyword) {
        LambdaQueryWrapper<WbCapture> w = own(userId, WbCapture::getUserId)
                .orderByDesc(WbCapture::getStarred)
                .orderByDesc(WbCapture::getCreateTime);
        if (StringUtils.hasText(status)) {
            w.eq(WbCapture::getStatus, status);
        }
        if (categoryId != null) {
            w.eq(WbCapture::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            w.like(WbCapture::getTitle, keyword);
        }
        return baseMapper.selectList(w);
    }

    @Override
    public WbCapture getCapture(Long id, Long userId) {
        return requireOwn(baseMapper.selectById(id), userId, WbCapture::getUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCapture(WbCaptureDTO dto, Long userId) {
        WbCapture e = new WbCapture();
        e.setUserId(userId);
        e.setTitle(requireNotBlank(dto.getTitle(), "标题不能为空"));
        e.setContent(dto.getContent());
        e.setSourceType(defaultIfBlank(dto.getSourceType(), "MANUAL"));
        e.setSourceUrl(dto.getSourceUrl());
        e.setDocId(dto.getDocId());
        e.setCategoryId(dto.getCategoryId());
        e.setTags(dto.getTags());
        e.setStatus("INBOX");
        e.setStarred(dto.getStarred() == null ? 0 : dto.getStarred());
        baseMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCapture(Long id, WbCaptureDTO dto, Long userId) {
        WbCapture e = getCapture(id, userId);
        if (StringUtils.hasText(dto.getTitle())) {
            e.setTitle(dto.getTitle());
        }
        e.setContent(dto.getContent());
        e.setSourceType(defaultIfBlank(dto.getSourceType(), e.getSourceType()));
        e.setSourceUrl(dto.getSourceUrl());
        e.setDocId(dto.getDocId());
        e.setCategoryId(dto.getCategoryId());
        e.setTags(dto.getTags());
        if (dto.getStarred() != null) {
            e.setStarred(dto.getStarred());
        }
        baseMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCapture(Long id, Long userId) {
        getCapture(id, userId);
        baseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCaptureStatus(Long id, String status, Long userId) {
        WbCapture e = getCapture(id, userId);
        e.setStatus(status);
        baseMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleCaptureStar(Long id, Long userId) {
        WbCapture e = getCapture(id, userId);
        e.setStarred(e.getStarred() != null && e.getStarred() == 1 ? 0 : 1);
        baseMapper.updateById(e);
    }

    // ============================ 模块二：康奈尔笔记 ============================

    @Override
    public List<WbNote> listNotes(Long userId, Long captureId, Long categoryId, String keyword) {
        LambdaQueryWrapper<WbNote> w = own(userId, WbNote::getUserId).orderByDesc(WbNote::getUpdateTime);
        if (captureId != null) {
            w.eq(WbNote::getCaptureId, captureId);
        }
        if (categoryId != null) {
            w.eq(WbNote::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            w.like(WbNote::getTitle, keyword);
        }
        return noteMapper.selectList(w);
    }

    @Override
    public WbNote getNote(Long id, Long userId) {
        return requireOwn(noteMapper.selectById(id), userId, WbNote::getUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNote(WbNoteDTO dto, Long userId) {
        WbNote e = new WbNote();
        e.setUserId(userId);
        e.setCaptureId(dto.getCaptureId());
        e.setCategoryId(dto.getCategoryId());
        e.setTitle(requireNotBlank(dto.getTitle(), "笔记标题不能为空"));
        e.setCueColumn(dto.getCueColumn());
        e.setNoteColumn(dto.getNoteColumn());
        e.setSummaryColumn(dto.getSummaryColumn());
        e.setTags(dto.getTags());
        e.setMastery(dto.getMastery() == null ? 0 : dto.getMastery());
        noteMapper.insert(e);
        // 收集箱条目流转为「已整理」
        if (dto.getCaptureId() != null) {
            markCaptureProcessed(dto.getCaptureId(), userId);
        }
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNote(Long id, WbNoteDTO dto, Long userId) {
        WbNote e = getNote(id, userId);
        if (dto.getCaptureId() != null) {
            e.setCaptureId(dto.getCaptureId());
        }
        if (dto.getCategoryId() != null) {
            e.setCategoryId(dto.getCategoryId());
        }
        if (StringUtils.hasText(dto.getTitle())) {
            e.setTitle(dto.getTitle());
        }
        e.setCueColumn(dto.getCueColumn());
        e.setNoteColumn(dto.getNoteColumn());
        e.setSummaryColumn(dto.getSummaryColumn());
        e.setTags(dto.getTags());
        if (dto.getMastery() != null) {
            e.setMastery(dto.getMastery());
        }
        noteMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNote(Long id, Long userId) {
        getNote(id, userId);
        noteMapper.deleteById(id);
    }

    private void markCaptureProcessed(Long captureId, Long userId) {
        WbCapture c = baseMapper.selectById(captureId);
        if (c != null && Objects.equals(c.getUserId(), userId) && !"ARCHIVED".equals(c.getStatus())) {
            c.setStatus("PROCESSED");
            baseMapper.updateById(c);
        }
    }

    // ============================ 模块三：间隔重复（SM-2） ============================

    @Override
    public List<WbReviewCardVO> listReviewCards(Long userId, Long categoryId, Long noteId) {
        LambdaQueryWrapper<WbReviewCard> w = own(userId, WbReviewCard::getUserId)
                .orderByAsc(WbReviewCard::getNextReviewTime);
        if (categoryId != null) {
            w.eq(WbReviewCard::getCategoryId, categoryId);
        }
        if (noteId != null) {
            w.eq(WbReviewCard::getNoteId, noteId);
        }
        return toCardVO(reviewCardMapper.selectList(w));
    }

    @Override
    public List<WbReviewCardVO> drawReview(Long userId, Integer limit) {
        LambdaQueryWrapper<WbReviewCard> w = own(userId, WbReviewCard::getUserId)
                .eq(WbReviewCard::getSuspended, 0)
                .le(WbReviewCard::getNextReviewTime, LocalDateTime.now().plusDays(1))
                .orderByAsc(WbReviewCard::getNextReviewTime)
                .orderByAsc(WbReviewCard::getId);
        if (limit != null && limit > 0) {
            w.last("LIMIT " + limit);
        }
        return toCardVO(reviewCardMapper.selectList(w));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReviewCard(WbReviewCardDTO dto, Long userId) {
        WbReviewCard e = new WbReviewCard();
        e.setUserId(userId);
        e.setCaptureId(dto.getCaptureId());
        e.setNoteId(dto.getNoteId());
        e.setCategoryId(dto.getCategoryId());
        e.setFront(requireNotBlank(dto.getFront(), "卡片正面不能为空"));
        e.setBack(requireNotBlank(dto.getBack(), "卡片背面不能为空"));
        e.setCardType(defaultIfBlank(dto.getCardType(), "BASIC"));
        e.setEaseFactor(DEFAULT_EF);
        e.setRepetitions(0);
        e.setIntervalDay(0);
        e.setReviewCount(0);
        e.setLapseCount(0);
        e.setSuspended(0);
        // 新卡立即进入今日待复习队列
        e.setNextReviewTime(LocalDateTime.now());
        e.setLastReviewTime(null);
        reviewCardMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReviewCard(Long id, WbReviewCardDTO dto, Long userId) {
        WbReviewCard e = requireOwn(reviewCardMapper.selectById(id), userId, WbReviewCard::getUserId);
        if (StringUtils.hasText(dto.getFront())) {
            e.setFront(dto.getFront());
        }
        if (StringUtils.hasText(dto.getBack())) {
            e.setBack(dto.getBack());
        }
        if (StringUtils.hasText(dto.getCardType())) {
            e.setCardType(dto.getCardType());
        }
        e.setCaptureId(dto.getCaptureId());
        e.setNoteId(dto.getNoteId());
        e.setCategoryId(dto.getCategoryId());
        reviewCardMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReviewCard(Long id, Long userId) {
        requireOwn(reviewCardMapper.selectById(id), userId, WbReviewCard::getUserId);
        reviewCardMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WbReviewGradeResultVO gradeReview(Long cardId, WbReviewGradeDTO dto, Long userId) {
        WbReviewCard e = requireOwn(reviewCardMapper.selectById(cardId), userId, WbReviewCard::getUserId);
        int quality = dto.getQuality() == null ? 0 : dto.getQuality();
        if (quality < 0 || quality > 3) {
            throw new BusinessException("评分质量需在 0~3 之间");
        }

        int ef = e.getEaseFactor() == null ? DEFAULT_EF : e.getEaseFactor();
        int repetitions = e.getRepetitions() == null ? 0 : e.getRepetitions();
        int interval = e.getIntervalDay() == null ? 0 : e.getIntervalDay();
        boolean lapsed = quality < PASS_QUALITY;

        // SM-2 核心：更新难度系数 —— EF' = EF + (0.1 - (5-q)(0.08 + (5-q)*0.02))
        double q = quality;
        double efDouble = ef / 100.0 + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        ef = (int) Math.round(Math.max(MIN_EF / 100.0, efDouble) * 100);

        if (lapsed) {
            // 遗忘：连续答对归零，间隔重置为 1 天后重新学习
            repetitions = 0;
            interval = 1;
            e.setLapseCount((e.getLapseCount() == null ? 0 : e.getLapseCount()) + 1);
        } else {
            repetitions += 1;
            if (repetitions == 1) {
                interval = 1;
            } else if (repetitions == 2) {
                interval = 6;
            } else {
                interval = (int) Math.round(interval * (ef / 100.0));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        e.setEaseFactor(ef);
        e.setRepetitions(repetitions);
        e.setIntervalDay(interval);
        e.setReviewCount((e.getReviewCount() == null ? 0 : e.getReviewCount()) + 1);
        e.setLastReviewTime(now);
        e.setNextReviewTime(now.plusDays(interval));
        reviewCardMapper.updateById(e);

        // 写复习日志
        WbReviewLog log = new WbReviewLog();
        log.setUserId(userId);
        log.setCardId(cardId);
        log.setQuality(quality);
        log.setIntervalDay(interval);
        log.setEaseFactor(ef);
        log.setCostMs(dto.getCostMs());
        reviewLogMapper.insert(log);

        WbReviewGradeResultVO result = new WbReviewGradeResultVO();
        result.setCardId(cardId);
        result.setQuality(quality);
        result.setRepetitions(repetitions);
        result.setIntervalDay(interval);
        result.setEaseFactor(ef / 100.0);
        result.setNextReviewAt(e.getNextReviewTime().toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli());
        result.setLapsed(lapsed);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleSuspend(Long cardId, Long userId) {
        WbReviewCard e = requireOwn(reviewCardMapper.selectById(cardId), userId, WbReviewCard::getUserId);
        e.setSuspended(e.getSuspended() != null && e.getSuspended() == 1 ? 0 : 1);
        reviewCardMapper.updateById(e);
    }

    private List<WbReviewCardVO> toCardVO(List<WbReviewCard> cards) {
        return cards.stream().map(c -> {
            WbReviewCardVO vo = new WbReviewCardVO();
            org.springframework.beans.BeanUtils.copyProperties(c, vo);
            vo.setEaseFactorDecimal((c.getEaseFactor() == null ? DEFAULT_EF : c.getEaseFactor()) / 100.0);
            vo.setNextReviewHint(buildNextHint(c.getNextReviewTime()));
            return vo;
        }).collect(Collectors.toList());
    }

    private String buildNextHint(LocalDateTime next) {
        if (next == null) {
            return "待安排";
        }
        long days = Duration.between(LocalDateTime.now().toLocalDate().atStartOfDay(),
                next.toLocalDate().atStartOfDay()).toDays();
        if (days < 0) {
            return "已逾期 " + (-days) + " 天";
        } else if (days == 0) {
            return "今天";
        } else if (days == 1) {
            return "明天";
        }
        return days + " 天后";
    }

    // ============================ 模块三扩展：记忆宫殿 ============================

    @Override
    public List<WbPalace> listPalaces(Long userId) {
        return palaceMapper.selectList(own(userId, WbPalace::getUserId).orderByDesc(WbPalace::getUpdateTime));
    }

    @Override
    public WbPalace getPalace(Long id, Long userId) {
        return requireOwn(palaceMapper.selectById(id), userId, WbPalace::getUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPalace(WbPalaceDTO dto, Long userId) {
        WbPalace e = new WbPalace();
        e.setUserId(userId);
        e.setName(requireNotBlank(dto.getName(), "宫殿名称不能为空"));
        e.setDescription(dto.getDescription());
        e.setTheme(defaultIfBlank(dto.getTheme(), "ROOM"));
        e.setCoverColor(dto.getCoverColor());
        e.setCategoryId(dto.getCategoryId());
        palaceMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePalace(Long id, WbPalaceDTO dto, Long userId) {
        WbPalace e = getPalace(id, userId);
        if (StringUtils.hasText(dto.getName())) {
            e.setName(dto.getName());
        }
        e.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getTheme())) {
            e.setTheme(dto.getTheme());
        }
        e.setCoverColor(dto.getCoverColor());
        e.setCategoryId(dto.getCategoryId());
        palaceMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePalace(Long id, Long userId) {
        getPalace(id, userId);
        // 级联删除位点（逻辑删除）
        lociMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WbPalaceLoci>()
                .eq(WbPalaceLoci::getPalaceId, id)
                .set(WbPalaceLoci::getDeleted, 1));
        palaceMapper.deleteById(id);
    }

    @Override
    public List<WbPalaceLoci> listLoci(Long palaceId, Long userId) {
        return lociMapper.selectList(own(userId, WbPalaceLoci::getUserId)
                .eq(WbPalaceLoci::getPalaceId, palaceId)
                .orderByAsc(WbPalaceLoci::getSortOrder)
                .orderByAsc(WbPalaceLoci::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLoci(WbPalaceLociDTO dto, Long userId) {
        WbPalaceLoci e = new WbPalaceLoci();
        e.setUserId(userId);
        e.setPalaceId(requireNotNull(dto.getPalaceId(), "宫殿ID不能为空"));
        e.setCaptureId(dto.getCaptureId());
        e.setNoteId(dto.getNoteId());
        e.setCategoryId(dto.getCategoryId());
        e.setName(requireNotBlank(dto.getName(), "位点名称不能为空"));
        e.setKnowledgePoint(dto.getKnowledgePoint());
        e.setImageHint(dto.getImageHint());
        e.setIcon(dto.getIcon());
        e.setPosX(dto.getPosX() == null ? 50 : dto.getPosX());
        e.setPosY(dto.getPosY() == null ? 50 : dto.getPosY());
        e.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        lociMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLoci(Long id, WbPalaceLociDTO dto, Long userId) {
        WbPalaceLoci e = requireOwn(lociMapper.selectById(id), userId, WbPalaceLoci::getUserId);
        if (dto.getPalaceId() != null) {
            e.setPalaceId(dto.getPalaceId());
        }
        e.setCaptureId(dto.getCaptureId());
        e.setNoteId(dto.getNoteId());
        e.setCategoryId(dto.getCategoryId());
        if (StringUtils.hasText(dto.getName())) {
            e.setName(dto.getName());
        }
        e.setKnowledgePoint(dto.getKnowledgePoint());
        e.setImageHint(dto.getImageHint());
        e.setIcon(dto.getIcon());
        if (dto.getPosX() != null) {
            e.setPosX(dto.getPosX());
        }
        if (dto.getPosY() != null) {
            e.setPosY(dto.getPosY());
        }
        if (dto.getSortOrder() != null) {
            e.setSortOrder(dto.getSortOrder());
        }
        lociMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLoci(Long id, Long userId) {
        requireOwn(lociMapper.selectById(id), userId, WbPalaceLoci::getUserId);
        lociMapper.deleteById(id);
    }

    // ============================ 模块四：费曼故事 ============================

    @Override
    public List<WbStory> listStories(Long userId, Long categoryId, String status, String keyword) {
        LambdaQueryWrapper<WbStory> w = own(userId, WbStory::getUserId).orderByDesc(WbStory::getUpdateTime);
        if (categoryId != null) {
            w.eq(WbStory::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(status)) {
            w.eq(WbStory::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            w.like(WbStory::getTitle, keyword);
        }
        return storyMapper.selectList(w);
    }

    @Override
    public WbStory getStory(Long id, Long userId) {
        return requireOwn(storyMapper.selectById(id), userId, WbStory::getUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStory(WbStoryDTO dto, Long userId) {
        WbStory e = new WbStory();
        fillStory(e, dto, userId);
        e.setStatus(defaultIfBlank(dto.getStatus(), "DRAFT"));
        e.setWordCount(calcWords(dto.getContent()));
        storyMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStory(Long id, WbStoryDTO dto, Long userId) {
        WbStory e = getStory(id, userId);
        fillStory(e, dto, userId);
        if (StringUtils.hasText(dto.getStatus())) {
            e.setStatus(dto.getStatus());
        }
        e.setWordCount(calcWords(dto.getContent()));
        storyMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStory(Long id, Long userId) {
        getStory(id, userId);
        storyMapper.deleteById(id);
    }

    private void fillStory(WbStory e, WbStoryDTO dto, Long userId) {
        e.setUserId(userId);
        e.setCaptureId(dto.getCaptureId());
        e.setNoteId(dto.getNoteId());
        e.setCategoryId(dto.getCategoryId());
        if (StringUtils.hasText(dto.getTitle())) {
            e.setTitle(dto.getTitle());
        }
        e.setAudience(defaultIfBlank(dto.getAudience(), "CHILD"));
        e.setMetaphor(dto.getMetaphor());
        e.setContent(dto.getContent());
        e.setGapNote(dto.getGapNote());
        if (dto.getClarityScore() != null) {
            e.setClarityScore(dto.getClarityScore());
        }
    }

    private int calcWords(String content) {
        if (content == null) {
            return 0;
        }
        // 去除 Markdown 标记后粗略统计中文字符 + 英文单词
        String text = content.replaceAll("[#>*`\\-|\\[\\]()!]", " ").replaceAll("\\s+", " ");
        int cjk = 0;
        int en = 0;
        for (String token : text.split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.matches(".*[\\u4e00-\\u9fa5].*")) {
                cjk += token.length();
            } else {
                en += 1;
            }
        }
        return cjk + en;
    }

    // ============================ 遗忘曲线可视化 ============================

    @Override
    public WbForgettingCurveVO forgettingCurve(Long userId, Integer days) {
        int span = (days == null || days <= 0) ? 30 : Math.min(days, 365);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(span - 1);
        // 拉取统计区间内的复习日志（带 cardId 以便判断首评新卡）
        List<WbReviewLog> logs = reviewLogMapper.selectList(own(userId, WbReviewLog::getUserId)
                .ge(WbReviewLog::getCreateTime, start.atStartOfDay())
                .le(WbReviewLog::getCreateTime, end.atTime(LocalTime.MAX))
                .orderByAsc(WbReviewLog::getCreateTime));

        // 每个卡片首次评分日期，用于标记 newCards
        Set<Long> seenCards = new HashSet<>();
        // 以日期为键的聚合桶
        Map<String, WbForgettingCurveVO.Point> bucket = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            WbForgettingCurveVO.Point p = new WbForgettingCurveVO.Point();
            p.setDate(d.format(DateTimeFormatter.ISO_LOCAL_DATE));
            p.setReviews(0);
            p.setLapses(0);
            p.setNewCards(0);
            bucket.put(p.getDate(), p);
        }

        long totalReviews = 0;
        long totalLapses = 0;
        for (WbReviewLog log : logs) {
            String date = log.getCreateTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            WbForgettingCurveVO.Point p = bucket.get(date);
            if (p == null) {
                continue;
            }
            p.setReviews(p.getReviews() + 1);
            if (log.getQuality() != null && log.getQuality() == 0) {
                p.setLapses(p.getLapses() + 1);
            }
            if (log.getCardId() != null && !seenCards.contains(log.getCardId())) {
                seenCards.add(log.getCardId());
                p.setNewCards(p.getNewCards() + 1);
            }
            totalReviews++;
            if (log.getQuality() != null && log.getQuality() == 0) {
                totalLapses++;
            }
        }

        List<WbForgettingCurveVO.Point> points = new ArrayList<>(bucket.values());
        for (WbForgettingCurveVO.Point p : points) {
            p.setLapseRate(p.getReviews() == 0 ? 0d : (double) p.getLapses() / p.getReviews());
        }
        double overall = totalReviews == 0 ? 0d : (double) totalLapses / totalReviews;

        WbForgettingCurveVO vo = new WbForgettingCurveVO();
        vo.setStartDate(start.format(DateTimeFormatter.ISO_LOCAL_DATE));
        vo.setEndDate(end.format(DateTimeFormatter.ISO_LOCAL_DATE));
        vo.setPoints(points);
        vo.setTotalReviews(totalReviews);
        vo.setTotalLapses(totalLapses);
        vo.setOverallLapseRate(overall);
        return vo;
    }

    // ============================ 通用工具 ============================

    private <T> LambdaQueryWrapper<T> own(Long userId, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> getter) {
        return new LambdaQueryWrapper<T>().eq(getter, userId);
    }

    private <T> T requireOwn(T entity, Long userId, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> getter) {
        if (entity == null) {
            throw new BusinessException("记录不存在");
        }
        Long owner = getter.apply(entity);
        if (!Objects.equals(owner, userId)) {
            throw new BusinessException("无权操作该记录");
        }
        return entity;
    }

    private String requireNotBlank(String value, String msg) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(msg);
        }
        return value;
    }

    private <T> T requireNotNull(T value, String msg) {
        if (value == null) {
            throw new BusinessException(msg);
        }
        return value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
