package com.knowflow.administration.controller.admin;

import com.knowflow.common.Result;
import com.knowflow.administration.entity.SysIcon;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员图标管理：系统图标为前端预定义名称，自定义图标以 base64 存储。
 */
@Slf4j
@Tag(name = "管理员图标管理")
@RestController
@RequestMapping("/api/admin/icons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminIconController {

    private final com.knowflow.administration.service.AdminIconApplicationService applicationService;

    @Operation(summary = "获取自定义图标列表")
    @GetMapping
    public Result<List<SysIcon>> list() {
        return applicationService.list();
    }

    @Operation(summary = "上传自定义图标")
    @PostMapping
    public Result<SysIcon> create(@RequestBody SysIcon icon) {
        return applicationService.create(icon);
    }

    @Operation(summary = "删除自定义图标")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return applicationService.delete(id);
    }
}
