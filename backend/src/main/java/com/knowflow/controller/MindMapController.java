package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.MindMapDTO;
import com.knowflow.service.MindMapService;
import com.knowflow.vo.MindMapSummaryVO;
import com.knowflow.vo.MindMapVO;
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
 * 前台-思维导图接口：整图 CRUD，全部接口需登录（user_id 维度隔离）。
 */
@Slf4j
@Tag(name = "思维导图")
@RestController
@RequestMapping("/api/mindmaps")
@RequiredArgsConstructor
public class MindMapController {

    private final MindMapService mindMapService;

    /** 当前登录用户 ID（SecurityConfig 已要求鉴权） */
    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    @Operation(summary = "思维导图列表（当前登录用户）")
    @GetMapping
    public Result<List<MindMapSummaryVO>> list() {
        return Result.success(mindMapService.listMindMaps(uid()));
    }

    @Operation(summary = "思维导图详情（含整图数据）")
    @GetMapping("/{id}")
    public Result<MindMapVO> get(@PathVariable Long id) {
        return Result.success(mindMapService.getMindMap(id, uid()));
    }

    @Operation(summary = "新建思维导图")
    @PostMapping
    public Result<Long> create(@RequestBody MindMapDTO dto) {
        return Result.success(mindMapService.createMindMap(dto, uid()));
    }

    @Operation(summary = "更新思维导图（标题与整图数据）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MindMapDTO dto) {
        mindMapService.updateMindMap(id, dto, uid());
        return Result.success();
    }

    @Operation(summary = "删除思维导图")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mindMapService.deleteMindMap(id, uid());
        return Result.success();
    }
}
