package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
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
import com.knowflow.service.WorkbenchMigrationService;
import com.knowflow.service.WorkbenchService;
import com.knowflow.vo.WbForgettingCurveVO;
import com.knowflow.vo.WbRecallSessionVO;
import com.knowflow.vo.WbReviewCardVO;
import com.knowflow.vo.WbReviewGradeResultVO;
import com.knowflow.vo.WorkbenchOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 前台-知识库工作台接口：输入（收集箱）/整理（康奈尔笔记）/复习（间隔重复+记忆宫殿）/输出（费曼故事）四模块闭环。
 * 全部接口需登录（user_id 维度隔离）。
 */
@Slf4j
@Tag(name = "知识库工作台")
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchService workbenchService;
    private final WorkbenchMigrationService workbenchMigrationService;

    /** 当前登录用户 ID（全部接口需登录）。 */
    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    // ============================ 总览 ============================
    @Operation(summary = "工作台总览统计（四模块概览指标）")
    @GetMapping("/overview")
    public Result<WorkbenchOverviewVO> overview() {
        return Result.success(workbenchService.overview(uid()));
    }

    @Operation(summary = "导出当前登录用户的工作台全量数据（用于迁移到桌面端 KnowFlow 学习工作台）")
    @GetMapping("/export")
    public Result<Map<String, Object>> exportWorkbench(HttpServletResponse response) {
        Map<String, Object> payload = workbenchMigrationService.exportAll(uid());
        String filename = "workbench-export-" + uid() + ".json";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        return Result.success(payload);
    }

    // ============================ 模块一：收集箱 ============================
    @Operation(summary = "收集箱列表（按状态/分类/关键词筛选）")
    @GetMapping("/captures")
    public Result<List<WbCapture>> listCaptures(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return Result.success(workbenchService.listCaptures(uid(), status, categoryId, keyword));
    }

    @Operation(summary = "收集箱详情")
    @GetMapping("/captures/{id}")
    public Result<WbCapture> getCapture(@PathVariable Long id) {
        return Result.success(workbenchService.getCapture(id, uid()));
    }

    @Operation(summary = "新增收集箱条目")
    @PostMapping("/captures")
    public Result<Long> createCapture(@RequestBody WbCaptureDTO dto) {
        return Result.success(workbenchService.createCapture(dto, uid()));
    }

    @Operation(summary = "更新收集箱条目")
    @PutMapping("/captures/{id}")
    public Result<Void> updateCapture(@PathVariable Long id, @RequestBody WbCaptureDTO dto) {
        workbenchService.updateCapture(id, dto, uid());
        return Result.success();
    }

    @Operation(summary = "删除收集箱条目")
    @DeleteMapping("/captures/{id}")
    public Result<Void> deleteCapture(@PathVariable Long id) {
        workbenchService.deleteCapture(id, uid());
        return Result.success();
    }

    @Operation(summary = "变更收集箱流转状态：INBOX/PROCESSED/ARCHIVED")
    @PutMapping("/captures/{id}/status")
    public Result<Void> setCaptureStatus(@PathVariable Long id, @RequestParam String status) {
        workbenchService.setCaptureStatus(id, status, uid());
        return Result.success();
    }

    @Operation(summary = "切换收集箱标星")
    @PutMapping("/captures/{id}/star")
    public Result<Void> toggleStar(@PathVariable Long id) {
        workbenchService.toggleCaptureStar(id, uid());
        return Result.success();
    }

    // ============================ 模块二：康奈尔笔记 ============================
    @Operation(summary = "康奈尔笔记列表")
    @GetMapping("/notes")
    public Result<List<WbNote>> listNotes(
            @RequestParam(required = false) Long captureId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return Result.success(workbenchService.listNotes(uid(), captureId, categoryId, keyword));
    }

    @Operation(summary = "康奈尔笔记详情")
    @GetMapping("/notes/{id}")
    public Result<WbNote> getNote(@PathVariable Long id) {
        return Result.success(workbenchService.getNote(id, uid()));
    }

    @Operation(summary = "新增康奈尔笔记（关联收集箱条目会将其标记为已整理）")
    @PostMapping("/notes")
    public Result<Long> createNote(@RequestBody WbNoteDTO dto) {
        return Result.success(workbenchService.createNote(dto, uid()));
    }

    @Operation(summary = "更新康奈尔笔记")
    @PutMapping("/notes/{id}")
    public Result<Void> updateNote(@PathVariable Long id, @RequestBody WbNoteDTO dto) {
        workbenchService.updateNote(id, dto, uid());
        return Result.success();
    }

    @Operation(summary = "删除康奈尔笔记")
    @DeleteMapping("/notes/{id}")
    public Result<Void> deleteNote(@PathVariable Long id) {
        workbenchService.deleteNote(id, uid());
        return Result.success();
    }

    // ============================ 模块三：间隔重复 ============================
    @Operation(summary = "复习卡片列表")
    @GetMapping("/reviews")
    public Result<List<WbReviewCardVO>> listReviews(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long noteId) {
        return Result.success(workbenchService.listReviewCards(uid(), categoryId, noteId));
    }

    @Operation(summary = "抽取今日待复习卡片（含逾期）")
    @GetMapping("/reviews/draw")
    public Result<List<WbReviewCardVO>> drawReview(@RequestParam(required = false, defaultValue = "20") Integer limit) {
        return Result.success(workbenchService.drawReview(uid(), limit));
    }

    @Operation(summary = "新增复习卡片（新卡立即进入今日队列）")
    @PostMapping("/reviews")
    public Result<Long> createReview(@RequestBody WbReviewCardDTO dto) {
        return Result.success(workbenchService.createReviewCard(dto, uid()));
    }

    @Operation(summary = "更新复习卡片")
    @PutMapping("/reviews/{id}")
    public Result<Void> updateReview(@PathVariable Long id, @RequestBody WbReviewCardDTO dto) {
        workbenchService.updateReviewCard(id, dto, uid());
        return Result.success();
    }

    @Operation(summary = "删除复习卡片")
    @DeleteMapping("/reviews/{id}")
    public Result<Void> deleteReview(@PathVariable Long id) {
        workbenchService.deleteReviewCard(id, uid());
        return Result.success();
    }

    @Operation(summary = "复习评分（应用 SM-2 调度，返回新间隔）")
    @PostMapping("/reviews/{id}/grade")
    public Result<WbReviewGradeResultVO> gradeReview(@PathVariable Long id, @RequestBody WbReviewGradeDTO dto) {
        return Result.success(workbenchService.gradeReview(id, dto, uid()));
    }

    @Operation(summary = "暂停/恢复复习卡片")
    @PutMapping("/reviews/{id}/suspend")
    public Result<Void> toggleSuspend(@PathVariable Long id) {
        workbenchService.toggleSuspend(id, uid());
        return Result.success();
    }

    @Operation(summary = "遗忘曲线：按日聚合复习量与遗忘率走势")
    @GetMapping("/reviews/forgetting-curve")
    public Result<WbForgettingCurveVO> forgettingCurve(@RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.success(workbenchService.forgettingCurve(uid(), days));
    }

    // ============================ 模块三扩展：主动回忆（三轮闭卷默写） ============================
    @Operation(summary = "主动回忆会话列表")
    @GetMapping("/recall-sessions")
    public Result<List<WbRecallSessionVO>> listRecallSessions() {
        return Result.success(workbenchService.listRecallSessions(uid()));
    }

    @Operation(summary = "主动回忆会话详情")
    @GetMapping("/recall-sessions/{id}")
    public Result<WbRecallSessionVO> getRecallSession(@PathVariable Long id) {
        return Result.success(workbenchService.getRecallSession(id, uid()));
    }

    @Operation(summary = "创建主动回忆会话（填原文与标题）")
    @PostMapping("/recall-sessions")
    public Result<Long> createRecallSession(@RequestBody WbRecallSessionDTO dto) {
        return Result.success(workbenchService.createRecallSession(dto, uid()));
    }

    @Operation(summary = "提交某轮默写内容（自动比对计分）")
    @PostMapping("/recall-sessions/{id}/submit")
    public Result<WbRecallSessionVO> submitRecallRound(@PathVariable Long id, @RequestBody WbRecallSessionDTO dto) {
        return Result.success(workbenchService.submitRecallRound(id, dto, uid()));
    }

    @Operation(summary = "删除主动回忆会话")
    @DeleteMapping("/recall-sessions/{id}")
    public Result<Void> deleteRecallSession(@PathVariable Long id) {
        workbenchService.deleteRecallSession(id, uid());
        return Result.success();
    }

    // ============================ 模块三扩展：记忆宫殿 ============================
    @Operation(summary = "记忆宫殿列表")
    @GetMapping("/palaces")
    public Result<List<WbPalace>> listPalaces() {
        return Result.success(workbenchService.listPalaces(uid()));
    }

    @Operation(summary = "记忆宫殿详情")
    @GetMapping("/palaces/{id}")
    public Result<WbPalace> getPalace(@PathVariable Long id) {
        return Result.success(workbenchService.getPalace(id, uid()));
    }

    @Operation(summary = "新增记忆宫殿")
    @PostMapping("/palaces")
    public Result<Long> createPalace(@RequestBody WbPalaceDTO dto) {
        return Result.success(workbenchService.createPalace(dto, uid()));
    }

    @Operation(summary = "更新记忆宫殿")
    @PutMapping("/palaces/{id}")
    public Result<Void> updatePalace(@PathVariable Long id, @RequestBody WbPalaceDTO dto) {
        workbenchService.updatePalace(id, dto, uid());
        return Result.success();
    }

    @Operation(summary = "删除记忆宫殿（级联删除位点）")
    @DeleteMapping("/palaces/{id}")
    public Result<Void> deletePalace(@PathVariable Long id) {
        workbenchService.deletePalace(id, uid());
        return Result.success();
    }

    @Operation(summary = "宫殿位点列表（按漫游顺序）")
    @GetMapping("/palaces/{id}/loci")
    public Result<List<WbPalaceLoci>> listLoci(@PathVariable Long id) {
        return Result.success(workbenchService.listLoci(id, uid()));
    }

    @Operation(summary = "新增宫殿位点")
    @PostMapping("/loci")
    public Result<Long> createLoci(@RequestBody WbPalaceLociDTO dto) {
        return Result.success(workbenchService.createLoci(dto, uid()));
    }

    @Operation(summary = "更新宫殿位点（含画布坐标拖拽）")
    @PutMapping("/loci/{id}")
    public Result<Void> updateLoci(@PathVariable Long id, @RequestBody WbPalaceLociDTO dto) {
        workbenchService.updateLoci(id, dto, uid());
        return Result.success();
    }

    @Operation(summary = "删除宫殿位点")
    @DeleteMapping("/loci/{id}")
    public Result<Void> deleteLoci(@PathVariable Long id) {
        workbenchService.deleteLoci(id, uid());
        return Result.success();
    }

    // ============================ 模块四：费曼故事 ============================
    @Operation(summary = "费曼故事列表")
    @GetMapping("/stories")
    public Result<List<WbStory>> listStories(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(workbenchService.listStories(uid(), categoryId, status, keyword));
    }

    @Operation(summary = "费曼故事详情")
    @GetMapping("/stories/{id}")
    public Result<WbStory> getStory(@PathVariable Long id) {
        return Result.success(workbenchService.getStory(id, uid()));
    }

    @Operation(summary = "新增费曼故事")
    @PostMapping("/stories")
    public Result<Long> createStory(@RequestBody WbStoryDTO dto) {
        return Result.success(workbenchService.createStory(dto, uid()));
    }

    @Operation(summary = "更新费曼故事")
    @PutMapping("/stories/{id}")
    public Result<Void> updateStory(@PathVariable Long id, @RequestBody WbStoryDTO dto) {
        workbenchService.updateStory(id, dto, uid());
        return Result.success();
    }

    @Operation(summary = "删除费曼故事")
    @DeleteMapping("/stories/{id}")
    public Result<Void> deleteStory(@PathVariable Long id) {
        workbenchService.deleteStory(id, uid());
        return Result.success();
    }
}
