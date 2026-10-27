package com.knowflow.controller.admin;

import com.knowflow.common.Result;
import com.knowflow.dto.ScenarioAdminDTO;
import com.knowflow.service.LearningScenarioService;
import com.knowflow.vo.ScenarioAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景练习内容管理接口（后台 ADMIN，Architecture-4.0-6）。
 * <p>
 * 端点（SecurityConfig /api/admin/** 已按 ADMIN 角色保护，此处再以 @PreAuthorize 双保险）：
 * - GET    /api/admin/learning-scenarios         全部场景（含下架；keyword/status 过滤）
 * - GET    /api/admin/learning-scenarios/{id}    管理详情（含服务端私有 keywords）
 * - POST   /api/admin/learning-scenarios         新建场景（校验 + keyPoints 序列化）
 * - PUT    /api/admin/learning-scenarios/{id}    覆盖式更新
 * - DELETE /api/admin/learning-scenarios/{id}    删除（逻辑删除，历史作答事件不受影响）
 * <p>
 * 内容为全用户共享（不做 userId 隔离）；本组接口不触掌握度/事件，仅维护内容表。
 */
@Tag(name = "管理端-场景练习内容管理")
@RestController
@RequestMapping("/api/admin/learning-scenarios")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class LearningScenarioAdminController {

    private final LearningScenarioService scenarioService;
    private final com.knowflow.catalog.QuestionMutationAudit catalogAudit;

    @Operation(summary = "场景列表（含下架，可按关键词/状态过滤）")
    @GetMapping
    public Result<List<ScenarioAdminVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(scenarioService.adminList(keyword, status));
    }

    @Operation(summary = "场景详情（管理端，含命中关键词）")
    @GetMapping("/{id}")
    public Result<ScenarioAdminVO> detail(@PathVariable Long id) {
        return Result.success(scenarioService.adminDetail(id));
    }

    @Operation(summary = "新建场景")
    @PostMapping
    public Result<ScenarioAdminVO> create(@RequestBody ScenarioAdminDTO dto) {
        return Result.success(scenarioService.adminCreate(dto));
    }

    @Operation(summary = "覆盖式更新场景")
    @PutMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Result<ScenarioAdminVO> update(@PathVariable Long id, @RequestBody ScenarioAdminDTO dto) {
        var result = scenarioService.adminUpdate(id, dto);
        catalogAudit.record("SCENARIO", id, "EDIT_STATUS", null, String.valueOf(dto.getStatus()));
        return Result.success(result);
    }

    @Operation(summary = "删除场景（逻辑删除）")
    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> remove(@PathVariable Long id) {
        scenarioService.adminRemove(id);
        catalogAudit.record("SCENARIO", id, "DELETE", null, "deleted=1");
        return Result.success();
    }
}
