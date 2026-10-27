package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.WbCaptureDTO;
import com.knowflow.dto.WbNoteDTO;
import com.knowflow.dto.WbPalaceDTO;
import com.knowflow.dto.WbPalaceLociDTO;
import com.knowflow.dto.WbRecallSessionDTO;
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
import com.knowflow.entity.WbRecallSession;
import com.knowflow.common.LearningEventType;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.WbCaptureMapper;
import com.knowflow.mapper.WbNoteMapper;
import com.knowflow.mapper.WbPalaceLociMapper;
import com.knowflow.mapper.WbPalaceMapper;
import com.knowflow.mapper.WbRecallSessionMapper;
import com.knowflow.mapper.WbReviewCardMapper;
import com.knowflow.mapper.WbReviewLogMapper;
import com.knowflow.mapper.WbStoryMapper;
import com.knowflow.service.LearningEventService;
import com.knowflow.service.SrsService;
import com.knowflow.service.WorkbenchService;
import com.knowflow.vo.WbForgettingCurveVO;
import com.knowflow.vo.WbRecallSessionVO;
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
    private final WbRecallSessionMapper recallSessionMapper;
    private final LearningEventService learningEventService;
    private final SrsService srsService;

    /** SM-2 难度系数默认 2.50，以整数存储（×100）。 */
    private static final int DEFAULT_EF = 250;
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
        String cardType = defaultIfBlank(dto.getCardType(), "BASIC");
        String back = dto.getBack() == null ? "" : dto.getBack();
        if ("BASIC".equals(cardType)) {
            requireNotBlank(back, "卡片背面不能为空");
        }
        e.setFront(requireNotBlank(dto.getFront(), "卡片正面不能为空"));
        e.setBack(back);
        e.setCardType(cardType);
        e.setHint(dto.getHint());
        e.setTags(dto.getTags());
        e.setExtraJson(dto.getExtraJson());
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
        String nextType = StringUtils.hasText(dto.getCardType()) ? dto.getCardType() : e.getCardType();
        if (StringUtils.hasText(dto.getFront())) {
            e.setFront(dto.getFront());
        }
        if (dto.getBack() != null) {
            if ("BASIC".equals(nextType) && !StringUtils.hasText(dto.getBack())) {
                throw new BusinessException("卡片背面不能为空");
            }
            e.setBack(dto.getBack());
        }
        if (StringUtils.hasText(dto.getCardType())) {
            e.setCardType(dto.getCardType());
        }
        if (dto.getHint() != null) {
            e.setHint(dto.getHint());
        }
        if (dto.getTags() != null) {
            e.setTags(dto.getTags());
        }
        if (dto.getExtraJson() != null) {
            e.setExtraJson(dto.getExtraJson());
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

        // SM-2 核心计算统一收敛到 SrsService（唯一业务实现入口），此处仅做领域校验与落库
        SrsService.SrsState state = new SrsService.SrsState();
        state.setRepetitions(e.getRepetitions() == null ? 0 : e.getRepetitions());
        state.setIntervalDay(e.getIntervalDay() == null ? 0 : e.getIntervalDay());
        state.setEaseFactor(e.getEaseFactor() == null ? DEFAULT_EF : e.getEaseFactor());
        state.setLapseCount(e.getLapseCount() == null ? 0 : e.getLapseCount());

        LocalDateTime now = LocalDateTime.now();
        SrsService.SrsResult r = srsService.grade(quality, PASS_QUALITY, state, now);

        int ef = r.getEaseFactor();
        int repetitions = r.getRepetitions();
        int interval = r.getIntervalDay();
        boolean lapsed = r.isLapsed();

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

        // Learning Event System（Phase 1）：复习卡 SM-2 复习落库事件，与业务完全解耦
        learningEventService.record(userId, LearningEventType.FLASHCARD_REVIEWED, "REVIEW_CARD", cardId,
                Map.of("quality", quality, "lapsed", lapsed, "intervalDay", interval, "repetitions", repetitions));

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

    // ============================ 模块三扩展：主动回忆（三轮闭卷默写） ============================

    /** 原文最大长度（字符），与前端输入上限一致。 */
    private static final int RECALL_MAX_SOURCE = 10000;
    /** 单轮默写最大长度。 */
    private static final int RECALL_MAX_TEXT = 20000;
    /** 标题最大长度。 */
    private static final int RECALL_MAX_TITLE = 100;
    /** 第三轮延迟复测间隔（分钟）。 */
    private static final int RECALL_ROUND3_DELAY_MINUTES = 60;
    private static final ObjectMapper RECALL_JSON = new ObjectMapper();

    @Override
    public List<WbRecallSessionVO> listRecallSessions(Long userId) {
        List<WbRecallSession> list = recallSessionMapper.selectList(
                new LambdaQueryWrapper<WbRecallSession>()
                        .eq(WbRecallSession::getUserId, userId)
                        .orderByDesc(WbRecallSession::getCreateTime));
        List<Long> noteIds = list.stream().map(WbRecallSession::getNoteId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> noteTitles = noteTitleMap(noteIds);
        return list.stream().map(e -> toRecallVO(e, noteTitles)).collect(Collectors.toList());
    }

    @Override
    public WbRecallSessionVO getRecallSession(Long id, Long userId) {
        WbRecallSession e = requireOwn(recallSessionMapper.selectById(id), userId, WbRecallSession::getUserId);
        return toRecallVO(e);
    }

    @Override
    @Transactional
    public Long createRecallSession(WbRecallSessionDTO dto, Long userId) {
        String source = requireRecallSource(dto.getSourceText());
        Long noteId = dto.getNoteId();
        if (noteId != null) {
            requireOwn(noteMapper.selectById(noteId), userId, WbNote::getUserId);
        }
        boolean draft = Boolean.TRUE.equals(dto.getDraft());
        WbRecallSession e = new WbRecallSession();
        e.setUserId(userId);
        e.setNoteId(noteId);
        e.setCardId(dto.getCardId());
        e.setTitle(normalizeRecallTitle(dto.getTitle(), source));
        e.setSourceText(source);
        e.setSourceType(normalizeRecallSourceType(dto.getSourceType(), noteId));
        e.setArchived(0);
        e.setCurrentRound(1);
        e.setStatus(draft ? "DRAFT" : "IN_PROGRESS");
        recallSessionMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional
    public WbRecallSessionVO updateRecallSession(Long id, WbRecallSessionDTO dto, Long userId) {
        WbRecallSession e = requireOwn(recallSessionMapper.selectById(id), userId, WbRecallSession::getUserId);
        boolean draft = "DRAFT".equals(e.getStatus());
        if (dto.getTitle() != null) {
            String title = dto.getTitle().trim();
            e.setTitle(title.isEmpty()
                    ? normalizeRecallTitle(null, e.getSourceText())
                    : truncateRecallTitle(title));
        }
        if (dto.getArchived() != null) {
            e.setArchived(dto.getArchived() == 1 ? 1 : 0);
        }
        if (dto.getSourceText() != null && !dto.getSourceText().equals(e.getSourceText())) {
            if (!draft) {
                throw new BusinessException(409, "会话已开始，原文快照已锁定；如需修改请复制为新会话");
            }
            e.setSourceText(requireRecallSource(dto.getSourceText()));
        }
        if (dto.getSourceType() != null && draft) {
            e.setSourceType(normalizeRecallSourceType(dto.getSourceType(), e.getNoteId()));
        }
        if (dto.getNoteId() != null && draft) {
            requireOwn(noteMapper.selectById(dto.getNoteId()), userId, WbNote::getUserId);
            e.setNoteId(dto.getNoteId());
            e.setSourceType("NOTE");
        }
        if (dto.getCardId() != null && draft) {
            e.setCardId(dto.getCardId());
        }
        recallSessionMapper.updateById(e);
        return toRecallVO(e);
    }

    @Override
    @Transactional
    public WbRecallSessionVO saveRecallDraft(Long id, WbRecallSessionDTO dto, Long userId) {
        WbRecallSession e = requireOwn(recallSessionMapper.selectById(id), userId, WbRecallSession::getUserId);
        int round = requireRecallRound(dto);
        String text = dto.getText() == null ? "" : dto.getText();
        if (text.length() > RECALL_MAX_TEXT) {
            throw new BusinessException(400, "草稿内容过长");
        }
        requireRecallRoundWritable(e, round, LocalDateTime.now());
        LambdaUpdateWrapper<WbRecallSession> w = Wrappers.<WbRecallSession>lambdaUpdate()
                .eq(WbRecallSession::getId, id)
                .eq(WbRecallSession::getUserId, userId)
                .set(recallDraftColumn(round), text);
        if (round == 1) {
            w.isNull(WbRecallSession::getRound1Text);
        } else if (round == 2) {
            w.isNull(WbRecallSession::getRound2Text);
        } else {
            w.isNull(WbRecallSession::getRound3Text);
        }
        if (recallSessionMapper.update(null, w) == 0) {
            throw new BusinessException(409, "该轮已提交，无法继续保存草稿");
        }
        return getRecallSession(id, userId);
    }

    @Override
    @Transactional
    public WbRecallSessionVO saveRecallGaps(Long id, WbRecallSessionDTO dto, Long userId) {
        WbRecallSession e = requireOwn(recallSessionMapper.selectById(id), userId, WbRecallSession::getUserId);
        int round = requireRecallRound(dto);
        if (recallSubmittedText(e, round) == null) {
            throw new BusinessException(409, "该轮尚未提交，无法确认遗漏");
        }
        List<String> gaps = dto.getGaps() == null ? new ArrayList<>() : dto.getGaps().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(g -> g.length() <= 100)
                .distinct()
                .limit(50)
                .collect(Collectors.toList());
        Map<String, List<String>> all = parseRecallGaps(e.getGapJson());
        all.put(String.valueOf(round), gaps);
        try {
            e.setGapJson(RECALL_JSON.writeValueAsString(all));
        } catch (Exception ex) {
            throw new BusinessException(500, "遗漏内容保存失败");
        }
        recallSessionMapper.updateById(e);
        return toRecallVO(e);
    }

    @Override
    @Transactional
    public Long copyRecallSession(Long id, Long userId) {
        WbRecallSession e = requireOwn(recallSessionMapper.selectById(id), userId, WbRecallSession::getUserId);
        WbRecallSession copy = new WbRecallSession();
        copy.setUserId(userId);
        copy.setNoteId(e.getNoteId());
        copy.setCardId(e.getCardId());
        copy.setTitle(truncateRecallTitle((StringUtils.hasText(e.getTitle()) ? e.getTitle() : "主动回忆会话") + "（副本）"));
        copy.setSourceText(e.getSourceText());
        copy.setSourceType(e.getSourceType());
        copy.setArchived(0);
        copy.setCurrentRound(1);
        copy.setStatus("DRAFT");
        recallSessionMapper.insert(copy);
        return copy.getId();
    }

    @Override
    public void setRecallArchived(Long id, Integer archived, Long userId) {
        WbRecallSession e = requireOwn(recallSessionMapper.selectById(id), userId, WbRecallSession::getUserId);
        e.setArchived(archived != null && archived == 1 ? 1 : 0);
        recallSessionMapper.updateById(e);
    }

    @Override
    @Transactional
    public WbRecallSessionVO submitRecallRound(Long id, WbRecallSessionDTO dto, Long userId) {
        WbRecallSession e = requireOwn(recallSessionMapper.selectById(id), userId, WbRecallSession::getUserId);
        int round = requireRecallRound(dto);
        if (!StringUtils.hasText(dto.getText())) {
            throw new BusinessException(400, "默写内容不能为空");
        }
        if (dto.getText().length() > RECALL_MAX_TEXT) {
            throw new BusinessException(400, "默写内容过长");
        }
        if ("COMPLETED".equals(e.getStatus())) {
            throw new BusinessException(409, "会话已完成，历史记录不可覆盖");
        }
        LocalDateTime now = LocalDateTime.now();
        requireRecallRoundWritable(e, round, now);
        int expectedRound = e.getCurrentRound() == null ? 1 : e.getCurrentRound();
        if (expectedRound != round) {
            throw new BusinessException(409, "当前应提交第 " + expectedRound + " 轮");
        }
        int score = scoreRecall(e.getSourceText(), dto.getText());
        String text = dto.getText();
        LambdaUpdateWrapper<WbRecallSession> w = Wrappers.<WbRecallSession>lambdaUpdate()
                .eq(WbRecallSession::getId, id)
                .eq(WbRecallSession::getUserId, userId)
                .eq(WbRecallSession::getCurrentRound, expectedRound);
        if (round == 1) {
            w.isNull(WbRecallSession::getRound1Text)
                    .set(WbRecallSession::getRound1Text, text)
                    .set(WbRecallSession::getRound1Score, score)
                    .set(WbRecallSession::getRound1Draft, null)
                    .set(WbRecallSession::getCurrentRound, 2)
                    .set(WbRecallSession::getStatus, "IN_PROGRESS");
        } else if (round == 2) {
            w.isNull(WbRecallSession::getRound2Text)
                    .set(WbRecallSession::getRound2Text, text)
                    .set(WbRecallSession::getRound2Score, score)
                    .set(WbRecallSession::getRound2Draft, null)
                    .set(WbRecallSession::getCurrentRound, 3)
                    .set(WbRecallSession::getStatus, "IN_PROGRESS")
                    .set(WbRecallSession::getRound3DueTime, now.plusMinutes(RECALL_ROUND3_DELAY_MINUTES));
        } else {
            w.isNull(WbRecallSession::getRound3Text)
                    .set(WbRecallSession::getRound3Text, text)
                    .set(WbRecallSession::getRound3Score, score)
                    .set(WbRecallSession::getRound3Draft, null)
                    .set(WbRecallSession::getStatus, "COMPLETED")
                    .set(WbRecallSession::getCompletedTime, now);
        }
        if (recallSessionMapper.update(null, w) == 0) {
            throw new BusinessException(409, "该轮已提交或状态已变化，请刷新后重试");
        }
        if (round == 3) {
            double recallAvg = ((e.getRound1Score() == null ? 0 : e.getRound1Score())
                    + (e.getRound2Score() == null ? 0 : e.getRound2Score()) + score) / 3.0;
            learningEventService.record(userId, LearningEventType.RECALL_COMPLETED, "RECALL_SESSION", e.getId(),
                    Map.of("round3Score", score, "avgScore", recallAvg));
        }
        return getRecallSession(id, userId);
    }

    @Override
    public void deleteRecallSession(Long id, Long userId) {
        WbRecallSession e = requireOwn(recallSessionMapper.selectById(id), userId, WbRecallSession::getUserId);
        recallSessionMapper.deleteById(e.getId());
    }

    /**
     * 文本覆盖度比对（0-100）：原文关键词（英文单词 + 中文二元组）在默写中出现的比例。
     * 该分数只反映字面覆盖，不代表语义理解正确率，前端必须明确标注为辅助指标。
     */
    private int scoreRecall(String source, String recall) {
        if (!StringUtils.hasText(source)) return 0;
        java.util.Set<String> sourceWords = tokenize(source);
        if (sourceWords.isEmpty()) return 0;
        java.util.Set<String> recallWords = tokenize(recall);
        if (recallWords.isEmpty()) return 0;
        long hit = sourceWords.stream().filter(recallWords::contains).count();
        return (int) Math.round((double) hit / sourceWords.size() * 100);
    }

    /** 分词：英文按单词（长度≥2），中文按连续汉字的二元组切分，统一小写。 */
    private java.util.Set<String> tokenize(String text) {
        java.util.Set<String> tokens = new HashSet<>();
        String lower = text.toLowerCase();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[a-z]+|[\u4e00-\u9fa5]+").matcher(lower);
        while (m.find()) {
            String t = m.group();
            if (t.length() > 1 && t.charAt(0) >= 'a' && t.charAt(0) <= 'z') {
                tokens.add(t);
            } else if (t.length() == 1) {
                tokens.add(t);
            } else {
                for (int i = 0; i + 2 <= t.length(); i++) {
                    tokens.add(t.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    /** 实体 → VO，补充分数趋势与逐轮进步百分比。 */
    private WbRecallSessionVO toRecallVO(WbRecallSession e) {
        return toRecallVO(e, noteTitleMap(e.getNoteId() == null ? List.of() : List.of(e.getNoteId())));
    }

    /** 实体 → VO（列表场景复用已批量查询的笔记标题，避免 N+1）。 */
    private WbRecallSessionVO toRecallVO(WbRecallSession e, Map<Long, String> noteTitles) {
        WbRecallSessionVO vo = new WbRecallSessionVO();
        vo.setId(e.getId());
        vo.setNoteId(e.getNoteId());
        vo.setCardId(e.getCardId());
        vo.setTitle(e.getTitle());
        vo.setSourceText(e.getSourceText());
        vo.setSourceType(e.getSourceType());
        vo.setArchived(e.getArchived() == null ? 0 : e.getArchived());
        vo.setRound1Draft(e.getRound1Draft());
        vo.setRound2Draft(e.getRound2Draft());
        vo.setRound3Draft(e.getRound3Draft());
        vo.setGapJson(e.getGapJson());
        vo.setRound1Text(e.getRound1Text());
        vo.setRound1Score(e.getRound1Score());
        vo.setRound2Text(e.getRound2Text());
        vo.setRound2Score(e.getRound2Score());
        vo.setRound3Text(e.getRound3Text());
        vo.setRound3Score(e.getRound3Score());
        vo.setCurrentRound(e.getCurrentRound());
        vo.setStatus(e.getStatus());
        vo.setRound3DueTime(e.getRound3DueTime());
        vo.setCompletedTime(e.getCompletedTime());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());

        boolean fromNote = "NOTE".equals(e.getSourceType())
                || (e.getSourceType() == null && e.getNoteId() != null);
        String noteTitle = e.getNoteId() == null ? null : noteTitles.get(e.getNoteId());
        vo.setSourceLabel(fromNote ? (StringUtils.hasText(noteTitle) ? noteTitle : "关联笔记") : "粘贴原文");

        LocalDateTime now = LocalDateTime.now();
        vo.setServerNow(now);
        vo.setRound3Available(recallRound3Available(e, now));

        List<Integer> trend = new ArrayList<>();
        trend.add(e.getRound1Score());
        trend.add(e.getRound2Score());
        trend.add(e.getRound3Score());
        vo.setScoreTrend(trend);

        List<Integer> improvement = new ArrayList<>();
        improvement.add(null);
        improvement.add(calcImprovement(e.getRound1Score(), e.getRound2Score()));
        improvement.add(calcImprovement(e.getRound2Score(), e.getRound3Score()));
        vo.setImprovementPct(improvement);
        return vo;
    }

    // ---------- 主动回忆内部工具 ----------

    private Map<Long, String> noteTitleMap(List<Long> noteIds) {
        Map<Long, String> titles = new LinkedHashMap<>();
        if (noteIds == null || noteIds.isEmpty()) return titles;
        for (WbNote note : noteMapper.selectBatchIds(noteIds)) {
            if (note != null) titles.put(note.getId(), note.getTitle());
        }
        return titles;
    }

    private String requireRecallSource(String source) {
        if (!StringUtils.hasText(source)) {
            throw new BusinessException(400, "原文不能为空");
        }
        if (source.length() > RECALL_MAX_SOURCE) {
            throw new BusinessException(400, "原文不能超过 10000 字");
        }
        return source;
    }

    private int requireRecallRound(WbRecallSessionDTO dto) {
        int round = dto.getRound() == null ? 1 : dto.getRound();
        if (round < 1 || round > 3) {
            throw new BusinessException(400, "轮次需在 1~3 之间");
        }
        return round;
    }

    /** 轮次可写校验：必须按顺序推进、该轮未提交、第三轮必须到期。 */
    private void requireRecallRoundWritable(WbRecallSession e, int round, LocalDateTime now) {
        if (round == 1) {
            if (e.getRound1Text() != null) throw new BusinessException(409, "第一轮已提交，历史记录不可覆盖");
            return;
        }
        if (e.getRound1Text() == null) throw new BusinessException(409, "请先完成第一轮");
        if (round == 2) {
            if (e.getRound2Text() != null) throw new BusinessException(409, "第二轮已提交，历史记录不可覆盖");
            return;
        }
        if (e.getRound2Text() == null) throw new BusinessException(409, "请先完成第二轮");
        if (e.getRound3Text() != null) throw new BusinessException(409, "第三轮已提交，历史记录不可覆盖");
        if (e.getRound3DueTime() != null && now.isBefore(e.getRound3DueTime())) {
            throw new BusinessException(409, "第三轮复测尚未到期，请等待复测时间");
        }
    }

    private boolean recallRound3Available(WbRecallSession e, LocalDateTime now) {
        return e.getRound2Text() != null
                && e.getRound3Text() == null
                && !"COMPLETED".equals(e.getStatus())
                && (e.getRound3DueTime() == null || !now.isBefore(e.getRound3DueTime()));
    }

    private String recallSubmittedText(WbRecallSession e, int round) {
        if (round == 1) return e.getRound1Text();
        if (round == 2) return e.getRound2Text();
        return e.getRound3Text();
    }

    private com.baomidou.mybatisplus.core.toolkit.support.SFunction<WbRecallSession, String> recallDraftColumn(int round) {
        if (round == 1) return WbRecallSession::getRound1Draft;
        if (round == 2) return WbRecallSession::getRound2Draft;
        return WbRecallSession::getRound3Draft;
    }

    private String normalizeRecallSourceType(String sourceType, Long noteId) {
        if (StringUtils.hasText(sourceType)) {
            return "NOTE".equalsIgnoreCase(sourceType) ? "NOTE" : "PASTE";
        }
        return noteId == null ? "PASTE" : "NOTE";
    }

    private String normalizeRecallTitle(String title, String source) {
        String base = StringUtils.hasText(title) ? title.trim() : recallFirstLine(source);
        base = base.replaceFirst("^#+\\s*", "").trim();
        if (base.isEmpty()) base = "主动回忆会话";
        return truncateRecallTitle(base);
    }

    private String recallFirstLine(String source) {
        if (source == null) return "";
        for (String line : source.split("\\r?\\n")) {
            if (StringUtils.hasText(line)) return line.trim();
        }
        return "";
    }

    private String truncateRecallTitle(String title) {
        String t = title == null ? "" : title.trim();
        return t.length() > RECALL_MAX_TITLE ? t.substring(0, RECALL_MAX_TITLE) : t;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> parseRecallGaps(String json) {
        if (!StringUtils.hasText(json)) return new LinkedHashMap<>();
        try {
            Map<String, List<String>> parsed = RECALL_JSON.readValue(json, Map.class);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    /** 计算本轮相对上一轮的进步百分比，任一为空返回 null。 */
    private Integer calcImprovement(Integer prev, Integer curr) {
        if (prev == null || curr == null) return null;
        if (prev == 0) return curr > 0 ? 100 : 0;
        return Math.round((float) (curr - prev) / prev * 100);
    }

    // ============================ 通用工具 ============================

    private <T> LambdaQueryWrapper<T> own(Long userId, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> getter) {
        return new LambdaQueryWrapper<T>().eq(getter, userId);
    }

    private <T> T requireOwn(T entity, Long userId, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> getter) {
        if (entity == null) {
            throw new BusinessException(404, "记录不存在");
        }
        Long owner = getter.apply(entity);
        if (!Objects.equals(owner, userId)) {
            throw new BusinessException(403, "无权操作该记录");
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
