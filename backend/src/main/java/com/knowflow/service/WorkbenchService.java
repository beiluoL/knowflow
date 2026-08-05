package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
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
import com.knowflow.entity.WbStory;
import com.knowflow.vo.WbRecallSessionVO;
import com.knowflow.vo.WbForgettingCurveVO;
import com.knowflow.vo.WbReviewCardVO;
import com.knowflow.vo.WbReviewGradeResultVO;
import com.knowflow.vo.WorkbenchOverviewVO;

import java.util.List;

/**
 * 知识库工作台业务服务：输入（收集箱）/整理（康奈尔笔记）/复习（间隔重复+记忆宫殿）/输出（费曼故事）
 * 四模块闭环的增删改查、SM-2 间隔重复调度与总览统计。
 */
public interface WorkbenchService extends IService<WbCapture> {

    // ---------- 总览 ----------
    /** 工作台总览统计（四模块概览指标）。 */
    WorkbenchOverviewVO overview(Long userId);

    // ---------- 模块一：收集箱 ----------
    List<WbCapture> listCaptures(Long userId, String status, Long categoryId, String keyword);

    WbCapture getCapture(Long id, Long userId);

    Long createCapture(WbCaptureDTO dto, Long userId);

    void updateCapture(Long id, WbCaptureDTO dto, Long userId);

    void deleteCapture(Long id, Long userId);

    /** 流转状态变更：INBOX→PROCESSED→ARCHIVED，标星切换。 */
    void setCaptureStatus(Long id, String status, Long userId);

    void toggleCaptureStar(Long id, Long userId);

    // ---------- 模块二：康奈尔笔记 ----------
    List<WbNote> listNotes(Long userId, Long captureId, Long categoryId, String keyword);

    WbNote getNote(Long id, Long userId);

    Long createNote(WbNoteDTO dto, Long userId);

    void updateNote(Long id, WbNoteDTO dto, Long userId);

    void deleteNote(Long id, Long userId);

    // ---------- 模块三：间隔重复（SM-2） ----------
    List<WbReviewCardVO> listReviewCards(Long userId, Long categoryId, Long noteId);

    /** 抽取今日待复习卡片（按逾期程度排序）。 */
    List<WbReviewCardVO> drawReview(Long userId, Integer limit);

    Long createReviewCard(WbReviewCardDTO dto, Long userId);

    void updateReviewCard(Long id, WbReviewCardDTO dto, Long userId);

    void deleteReviewCard(Long id, Long userId);

    /** 评分并应用 SM-2 调度，返回新间隔信息。 */
    WbReviewGradeResultVO gradeReview(Long cardId, WbReviewGradeDTO dto, Long userId);

    void toggleSuspend(Long cardId, Long userId);

    /** 遗忘曲线可视化：按日聚合复习日志的复习量与遗忘率走势。 */
    WbForgettingCurveVO forgettingCurve(Long userId, Integer days);

    // ---------- 模块三扩展：主动回忆（三轮闭卷默写） ----------

    /** 主动回忆会话列表（按创建时间倒序）。 */
    List<WbRecallSessionVO> listRecallSessions(Long userId);

    /** 获取会话详情（含三轮分数趋势与进步百分比）。 */
    WbRecallSessionVO getRecallSession(Long id, Long userId);

    /** 创建会话（仅填原文/标题），返回新会话 ID。 */
    Long createRecallSession(WbRecallSessionDTO dto, Long userId);

    /** 提交某轮默写内容，自动比对原文计算得分并保存，返回更新后会话。 */
    WbRecallSessionVO submitRecallRound(Long id, WbRecallSessionDTO dto, Long userId);

    /** 删除会话。 */
    void deleteRecallSession(Long id, Long userId);

    // ---------- 模块三扩展：记忆宫殿 ----------
    List<WbPalace> listPalaces(Long userId);

    WbPalace getPalace(Long id, Long userId);

    Long createPalace(WbPalaceDTO dto, Long userId);

    void updatePalace(Long id, WbPalaceDTO dto, Long userId);

    void deletePalace(Long id, Long userId);

    List<WbPalaceLoci> listLoci(Long palaceId, Long userId);

    Long createLoci(WbPalaceLociDTO dto, Long userId);

    void updateLoci(Long id, WbPalaceLociDTO dto, Long userId);

    void deleteLoci(Long id, Long userId);

    // ---------- 模块四：费曼故事 ----------
    List<WbStory> listStories(Long userId, Long categoryId, String status, String keyword);

    WbStory getStory(Long id, Long userId);

    Long createStory(WbStoryDTO dto, Long userId);

    void updateStory(Long id, WbStoryDTO dto, Long userId);

    void deleteStory(Long id, Long userId);
}
