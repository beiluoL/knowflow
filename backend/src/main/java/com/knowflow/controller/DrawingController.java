package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.DrawingDTO;
import com.knowflow.service.DrawingService;
import com.knowflow.vo.DrawingSummaryVO;
import com.knowflow.vo.DrawingVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台-绘图编辑器接口（流程图）：整图 CRUD，全部接口需登录（user_id 维度隔离）。
 */
@Slf4j
@Tag(name = "绘图编辑器")
@RestController
@RequestMapping("/api/drawings")
@RequiredArgsConstructor
public class DrawingController {

    private final DrawingService drawingService;

    /** 当前登录用户 ID（SecurityConfig 已要求鉴权） */
    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    @Operation(summary = "绘图列表（当前登录用户）")
    @GetMapping
    public Result<List<DrawingSummaryVO>> list() {
        return Result.success(drawingService.listDrawings(uid()));
    }

    @Operation(summary = "绘图详情（含整图数据）")
    @GetMapping("/{id}")
    public Result<DrawingVO> get(@PathVariable Long id) {
        return Result.success(drawingService.getDrawing(id, uid()));
    }

    @Operation(summary = "新建绘图")
    @PostMapping
    public Result<Long> create(@RequestBody DrawingDTO dto) {
        return Result.success(drawingService.createDrawing(dto, uid()));
    }

    @Operation(summary = "更新绘图（标题与整图数据）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DrawingDTO dto) {
        drawingService.updateDrawing(id, dto, uid());
        return Result.success();
    }

    @Operation(summary = "删除绘图")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        drawingService.deleteDrawing(id, uid());
        return Result.success();
    }
}
