package com.knowflow.controller;

import com.knowflow.dto.ImportTemplateDTO;
import com.knowflow.common.SecurityUtils;
import com.knowflow.service.ImportTemplateService;
import com.knowflow.vo.ImportTemplateVO;
import com.knowflow.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 导入规则模板管理接口。
 * <p>所有操作需登录；预设模板对所有登录用户可见但不可编辑/删除。</p>
 */
@RestController
@RequestMapping("/api/import-templates")
public class ImportTemplateController {

    @Autowired
    private ImportTemplateService importTemplateService;

    /** 模板列表（可按类型/启用状态过滤；返回当前用户可见范围）。 */
    @GetMapping
    public Result<List<ImportTemplateVO>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer enabled) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(importTemplateService.listTemplates(type, enabled, userId));
    }

    /** 模板详情。 */
    @GetMapping("/{id}")
    public Result<ImportTemplateVO> detail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ImportTemplateVO vo = importTemplateService.detail(id, userId);
        if (vo == null) {
            return Result.error("模板不存在或无权访问");
        }
        return Result.success(vo);
    }

    /** 创建模板（归属当前用户）。 */
    @PostMapping
    public Result<ImportTemplateVO> create(@RequestBody ImportTemplateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (dto.getName() == null || dto.getName().isEmpty()
                || dto.getType() == null || dto.getType().isEmpty()
                || dto.getContent() == null || dto.getContent().isEmpty()) {
            return Result.error("模板名称、类型与内容均为必填");
        }
        return Result.success(importTemplateService.create(dto, userId));
    }

    /** 更新模板（仅本人非预设模板）。 */
    @PutMapping("/{id}")
    public Result<ImportTemplateVO> update(@PathVariable Long id, @RequestBody ImportTemplateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        try {
            return Result.success(importTemplateService.update(id, dto, userId));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 删除模板（逻辑删除；预设不可删）。 */
    @DeleteMapping("/{id}")
    public Result<Boolean> remove(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean ok = importTemplateService.removeTemplate(id, userId);
        return ok ? Result.success(true) : Result.error("删除失败（预设模板或无权操作）");
    }

    /** 启用/停用切换。 */
    @PostMapping("/{id}/toggle")
    public Result<Boolean> toggle(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean ok = importTemplateService.toggle(id, userId);
        return ok ? Result.success(true) : Result.error("操作失败（预设模板或无权操作）");
    }

    /** 设为默认模板（同类型唯一）。 */
    @PostMapping("/{id}/default")
    public Result<Boolean> setDefault(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean ok = importTemplateService.setDefault(id, userId);
        return ok ? Result.success(true) : Result.error("设为默认失败（模板不存在或未启用）");
    }
}
