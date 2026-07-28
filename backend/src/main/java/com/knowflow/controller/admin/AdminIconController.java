package com.knowflow.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.entity.SysIcon;
import com.knowflow.mapper.SysIconMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private final SysIconMapper iconMapper;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        return null;
    }

    @Operation(summary = "获取自定义图标列表")
    @GetMapping
    public Result<List<SysIcon>> list() {
        QueryWrapper<SysIcon> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).orderByDesc("create_time");
        return Result.success(iconMapper.selectList(wrapper));
    }

    @Operation(summary = "上传自定义图标")
    @PostMapping
    public Result<SysIcon> create(@RequestBody SysIcon icon) {
        if (icon.getName() == null || icon.getName().isBlank()) {
            return Result.error(400, "图标名称不能为空");
        }
        if (icon.getContent() == null || icon.getContent().isBlank()) {
            return Result.error(400, "图标内容不能为空");
        }
        icon.setType("custom");
        icon.setUserId(getCurrentUserId());
        iconMapper.insert(icon);
        return Result.success(icon);
    }

    @Operation(summary = "删除自定义图标")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysIcon icon = iconMapper.selectById(id);
        if (icon == null || icon.getDeleted() != null && icon.getDeleted() == 1) {
            return Result.error(404, "图标不存在");
        }
        icon.setDeleted(1);
        iconMapper.updateById(icon);
        return Result.success(null);
    }
}
